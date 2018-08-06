package edu.virginia.lib.avalon.indexer;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraClientException;
import com.yourmediashelf.fedora.client.FedoraCredentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.nio.channels.FileLock;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AvalonIndexer {

    public static void main(String [] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Incorrect usage.  Please specify a configuration file as the only argument.");
            System.exit(-1);
        } else {
            Properties p = new Properties();
            FileInputStream fis = new FileInputStream(args[0]);
            try {
                // todo: move this to after the lock
                p.load(fis);
            } finally {
                fis.close();
            }
            FileLock lock = null;
            try {
                lock = new RandomAccessFile(new File(args[0]), "rw").getChannel().tryLock();
                if (lock == null) {
                    System.err.println("Another instance of this program is currently running the configuration " + args[0] + ".");
                    System.exit(-2);
                }

                AvalonIndexer ai = new AvalonIndexer(p);
                ai.synchronizeAddDocRepository();
                ai.shadowAnyDeletedRecords();
                if (ai.getErrorCount() > 0) {
                    System.err.println(ai.getErrorCount() + " errors while updating records, " + ai.getIndexRecordCount() + " other index records created/updated as a result of changes since " + new SimpleDateFormat("yyyy-MM-dd hh:mm").format(ai.getLastRunDate()) + ".");
                    System.exit(1);
                } else {
                    System.out.println(ai.getIndexRecordCount() + " index records created/updated as a result of changes since " + new SimpleDateFormat("yyyy-MM-dd hh:mm").format(ai.getLastRunDate()) + ".");
                }

            } finally {
                fis.close();
                if (lock != null) {
                    lock.release();
                }
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AvalonIndexer.class);

    private Properties configuration;
    
    private HttpClient httpClient;

    private FedoraClient client;

    private Transformer transformer;

    private int indexedRecords;

    private int errors;

    public AvalonIndexer(Properties p) throws TransformerConfigurationException, FedoraClientException, MalformedURLException {
        this.configuration = p;
        this.client = new FedoraClient(new FedoraCredentials(getRequiredProperty("fedora-url"), getProperty("fedora-username", ""), getProperty("fedora-password", "")));

        this.httpClient = new HttpClient();

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Templates templates = tFactory.newTemplates(
                new StreamSource(getClass().getClassLoader().getResourceAsStream("avalon-3.1-to-solr.xsl")));
        this.transformer = templates.newTransformer();
    }
    
    private String getProperty(String name, String defaultValue) {
        if (configuration.containsKey(name)) {
            return configuration.getProperty(name);
        } else {
            return defaultValue;
        }
    }
    
    private String getRequiredProperty(String name) {
        if (configuration.containsKey(name)) {
            return configuration.getProperty(name);
        } else {
            throw new RuntimeException("Required property \"" + name + "\" not set!");
        }
    }

    public int getIndexRecordCount() {
        return indexedRecords;
    }

    public int getErrorCount() {
        return errors;
    }

    /**
     * Updates the files in the given addDocRepo directory to reflect current
     * solr documents for the
     */
    public void synchronizeAddDocRepository() throws Exception {
        HydraSolrManager m = new HydraSolrManager(getRequiredProperty("hydra-solr-url"));
        for (HydraSolrManager.AvalonRecord record : m.getPidsUpdatedSince(getLastRunDate())) {
            FileOutputStream fos = new FileOutputStream(new File(getRequiredProperty("add-doc-repository"), record.getPid().replace(':', '_') + ".xml"));
            try {
                final boolean blacklisted = isBlacklisted(record);
                LOGGER.info("Generating add doc for " + (blacklisted ? "blacklisted " : "") + record.getPid() + " belonging to collection " + record.getCollectionPid() + "...");
                IOUtils.write(generateAddDoc(record.getPid(), blacklisted), fos);
                indexedRecords ++;
            } catch (Exception ex) {
                errors ++;
                LOGGER.error("Unable to index " + record.getPid() + "!", ex);
            } finally {
                fos.close();
            }
        }
        saveLastRunDate();
    }

    public void shadowAnyDeletedRecords() throws Exception {
        DocumentBuilder b = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        XPath xpath = XPathFactory.newInstance().newXPath();
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
        for (File solrDocFile : new File(getRequiredProperty("add-doc-repository")).listFiles()) {
            Pattern p = Pattern.compile("avalon_(\\d+).xml");
            Matcher m = p.matcher(solrDocFile.getName());
            if (m.matches()) {
                final String pid = "avalon:" + m.group(1);
                try {
                    if (!exists(pid)) {
                        Document solrDoc = b.parse(solrDocFile);
                        NodeList nl = (NodeList) xpath.evaluate("add/doc/field[@name='shadowed_location_facet']", solrDoc, XPathConstants.NODESET);
                        if (nl != null && nl.getLength() == 1 && !"HIDDEN".equals(nl.item(0).getTextContent())) {
                            LOGGER.debug("Hiding deleted record for " + pid + "...");
                            nl.item(0).setTextContent("HIDDEN");
                            FileOutputStream fos = new FileOutputStream(solrDocFile);
                            try {
                                StreamResult result = new StreamResult(fos);
                                transformer.transform(new DOMSource(solrDoc), result);
                            } finally {
                                fos.close();
                            }
                        }
                    }
                } catch (Exception ex) {
                    errors ++;
                    LOGGER.error("Error testing for existence of object " + pid + "!", ex);
                }
            }
        }
    }

    private boolean isBlacklisted(HydraSolrManager.AvalonRecord record) {
        for (String s : getRequiredProperty("collection-blacklist").split(",")) {
            if (record.getCollectionPid().equals(s.trim())) {
                return true;
            }
        }
        return false;
    }

    public List<String> getAllObjectPids() throws Exception {
        return getSubjects(client, "afmodel:MediaObject",  "info:fedora/fedora-system:def/model#hasModel");
    }

    public void indexPid(String pid, boolean blacklist) throws ParserConfigurationException, TransformerException, IOException, SAXException, FedoraClientException {
        postContent(generateAddDoc(pid, blacklist));
    }

    public String generateAddDoc(String pid, boolean blacklist) throws FedoraClientException, ParserConfigurationException, TransformerException, SAXException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        applyTransformation(FedoraClient.getDatastreamDissemination(pid, "descMetadata").execute(client).getEntityInputStream(), baos, pid, blacklist);
        return new String(baos.toByteArray());
    }

    private Date getLastRunDate() throws IOException, ParseException {
        File lastRunFile = new File(getRequiredProperty("last-run-file"));
        if (lastRunFile.exists()) {
            FileInputStream fis = new FileInputStream(lastRunFile);
            try {
                return new SimpleDateFormat().parse(IOUtils.toString(fis));
            } finally {
                fis.close();
            }
        } else {
            return null;
        }
    }
    
    private void saveLastRunDate() throws IOException {
        File lastRunFile = new File(getRequiredProperty("last-run-file"));
        FileOutputStream fos = new FileOutputStream(lastRunFile);
        try {
            IOUtils.write(new SimpleDateFormat().format(new Date()), fos);
        } finally {
            fos.close();
        }        
    }

    private void applyTransformation(InputStream source, OutputStream destination, String pid, boolean blacklist)
            throws ParserConfigurationException, IOException, SAXException, TransformerException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        DocumentBuilder b = f.newDocumentBuilder();
        transformer.setParameter("pid", pid);
        transformer.setParameter("fedoraBaseUrl", getRequiredProperty("fedora-url"));
        transformer.setParameter("avalonBaseUrl", getRequiredProperty("avalon-url"));
        transformer.setParameter("blacklist", blacklist ? "true" : "false");

        transformer.transform(new DOMSource(b.parse(source)), new StreamResult(destination));
    }

    public void postContent(String content) throws HttpException, IOException {
        PostMethod post = new PostMethod(getRequiredProperty("solr-update-url"));
        Part[] parts = {
                new FilePart("add.xml", new ByteArrayPartSource("add.xml", content.getBytes("UTF-8")), "text/xml", "UTF-8")
        };
        post.setRequestEntity(
                new MultipartRequestEntity(parts, post.getParams())
        );
        try {
            getClient().executeMethod(post);
            int status = post.getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + getRequiredProperty("solr-update-url") + "\" failed: " + post.getStatusLine());
            }
        } finally {
            post.releaseConnection();
        }
    }

    public void commit() throws HttpException, IOException {
        String url = getRequiredProperty("sol-update-url") + "?stream.body=%3Ccommit/%3E";
        GetMethod get = new GetMethod(url);
        try {
            HttpClient client = new HttpClient();
            client.executeMethod(get);
            int status = get.getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + url + "\" failed: " + get.getStatusLine());
            }
            LOGGER.info("Committed changes");
        } finally {
            get.releaseConnection();
        }
    }

    public void optimize() throws HttpException, IOException {
        String url = getRequiredProperty("sol-update-url") + "?stream.body=%3Coptimize/%3E";
        GetMethod get = new GetMethod(url);
        try {
            HttpClient client = new HttpClient();
            client.executeMethod(get);
            int status = get.getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + url + "\" failed: " + get.getStatusLine());
            }
            LOGGER.info("Optimized index");
        } finally {
            get.releaseConnection();
        }
    }


    private HttpClient getClient() {
        return httpClient;
    }

    public static List<String> getSubjects(FedoraClient fc, String object, String predicate) throws Exception {
        if (predicate == null) {
            throw new NullPointerException("predicate must not be null!");
        }
        String itqlQuery = "select $subject from <#ri> where $subject <" + predicate + "> " + (object != null ? "<info:fedora/" + object + ">" : "$other");
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("simple").execute(fc).getEntityInputStream()));
        List<String> pids = new ArrayList<String>();
        String line = null;
        Pattern p = Pattern.compile("\\Qsubject : <info:fedora/\\E([^\\>]+)\\Q>\\E");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                pids.add(m.group(1));
            }
        }
        return pids;
    }

    public boolean exists(String id) {
        try {
            FedoraClient.getObjectProfile(id).execute(client);
            return true;
        } catch (FedoraClientException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                return false;
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
