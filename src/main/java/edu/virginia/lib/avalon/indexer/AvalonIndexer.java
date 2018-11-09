package edu.virginia.lib.avalon.indexer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.channels.FileLock;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

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
import org.fcrepo.client.FcrepoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AvalonIndexer {

    public static void main(String[] args) throws Exception {
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
                    System.err
                            .println(ai.getErrorCount() + " errors while updating records, " + ai.getIndexRecordCount()
                                    + " other index records created/updated as a result of changes since "
                                    + (ai.getLastRunDate() == null ? "the beginning of time"
                                            : new SimpleDateFormat("yyyy-MM-dd hh:mm").format(ai.getLastRunDate()))
                                    + ".");
                    System.exit(1);
                } else {
                    System.out.println(
                            ai.getIndexRecordCount() + " index records created/updated as a result of changes since "
                                    + (ai.getLastRunDate() == null ? "the beginning of time"
                                            : new SimpleDateFormat("yyyy-MM-dd hh:mm").format(ai.getLastRunDate()))
                                    + ".");
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

    private FcrepoClient fcrepo;

    private String avalonFedoraBaseUrl;

    private Transformer transformer;

    private int indexedRecords;

    private int errors;

    public AvalonIndexer(Properties p) throws TransformerConfigurationException, MalformedURLException {
        this.configuration = p;

        this.httpClient = new HttpClient();

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Templates templates = tFactory.newTemplates(
                new StreamSource(getClass().getClassLoader().getResourceAsStream("avalon-to-solr.xsl")));
        this.transformer = templates.newTransformer();

        this.avalonFedoraBaseUrl = getRequiredProperty("fedoraBase");
        fcrepo = FcrepoClient.client().credentials(getRequiredProperty("username"), getRequiredProperty("password"))
                .throwExceptionOnFailure().build();

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
            final boolean blacklisted = isBlacklisted(record);
            String addDoc = null;
            try {
                addDoc = generateAddDoc(record);
            } catch (Throwable t) {
                LOGGER.error("Unable to index " + record.getId() + "!", t);
                errors ++;
                return;
            }
            FileOutputStream fos = new FileOutputStream(
                    new File(getRequiredProperty("add-doc-repository"), record.getFilename()));
            try {
                LOGGER.info("Generating add doc for " + (blacklisted ? "blacklisted " : "") + record.getId()
                        + " belonging to collection " + record.getCollectionId() + "...");
                IOUtils.write(addDoc, fos);
                indexedRecords ++;
            } catch (Exception ex) {
                errors ++;
                LOGGER.error("Unable to index " + record.getId() + "!", ex);
                fos.close();
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
            Pattern p = Pattern.compile("(\\.+).xml");
            Matcher m = p.matcher(solrDocFile.getName());
            if (m.matches()) {
                final String pid = m.group(1).replace('_', ':');
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

    private HashSet<String> blacklistedCollectionIds = null;

    private boolean isBlacklisted(HydraSolrManager.AvalonRecord record) throws Exception {
        if (blacklistedCollectionIds == null) {
            blacklistedCollectionIds = new HashSet<String>();
            for (String id : getRequiredProperty("collection-blacklist").split(",")) {
                id = id.trim();
                HydraSolrManager.AvalonRecord c = getRecord(id);
                if (c == null) {
                    throw new RuntimeException("Unable to find blacklisted collection: " + id);
                }
                blacklistedCollectionIds.add(id);
                blacklistedCollectionIds.add(c.getId());
            }
        }
        return blacklistedCollectionIds.contains(record.getCollectionId());
    }

    public String generateAddDoc(HydraSolrManager.AvalonRecord rec) throws Exception {
        final String oldId = rec.getOldId();
        final String id = rec.getId();
        Document doc = this.getSolrAddDocFromMods(fcrepo.get(new URI(getURLForMods(id))).perform().getBody());
        addField(doc, "id", namespaceId(oldId != null ? oldId : id));
        addField(doc, "id_text", namespaceId(oldId != null ? oldId : id));
        addField(doc, "avalon_url_display", getRequiredProperty("avalon-url"));
        addField(doc, "duration_display", rec.getDuration());
        addField(doc, "format_facet", "Online");
        if (rec.isMovingImage()) {
            addField(doc, "format_facet", "Online Video");
            addField(doc, "format_text", "Online Video");
            addField(doc, "format_facet", "Video");
        }
        if (rec.isAudioRecording()) {
            addField(doc, "format_facet", "Streaming Audio");
            addField(doc, "format_text", "Streaming Audio");
            addField(doc, "format_text", "Sound Recording");
        }
        if (this.isBlacklisted(rec)) {
            addField(doc, "shadowed_location_facet", "HIDDEN");
        } else if (!rec.isPublished()) {
            addField(doc, "shadowed_location_facet", "HIDDEN");
        } else if (rec.isHidden()) {
            addField(doc, "shadowed_location_facet", "UNDISCOVERABLE");
        } else {
            addField(doc, "shadowed_location_facet", "VISIBLE");
        }

        HydraSolrManager m = new HydraSolrManager(getRequiredProperty("hydra-solr-url"));
        HydraSolrManager.AvalonRecord collection = getRecord(rec.getCollectionId());

        addField(doc, "digital_collection_facet", collection.getName());
        addField(doc, "digital_collection_text", collection.getName(), "0.25");
        addField(doc, "unit_display", collection.getUnit());
        addField(doc, "unit_text", collection.getUnit(), "0.25");

        // For each section...
        boolean thumb = false;
        boolean audio = false;
        for (String partId : rec.getSectionIds()) {
            HydraSolrManager.AvalonRecord part = m.getResultingIds("id:\"" + partId + "\"").get(0);
            if (!thumb && part.hasThumbnail()) {
                addField(doc, "thumbnail_url_display",
                        getRequiredProperty("avalon-url") + "/master_files/" + part.getId() + "/thumbnail");
                thumb = true;
            }
            if (part.isAudioRecording()) {
                audio = true;
            } else {
                if (audio) {
                    throw new RuntimeException("Record " + id + " has video clips after audio clips!");
                }
            }
            addField(doc, "part_pid_display", part.getId());
            addField(doc, "part_duration_display", part.getDuration());
            addField(doc, "display_aspect_ratio_display", part.getAspectRatio());
            addField(doc, "part_label_display", part.getTitle());
        }

        DOMSource domSource = new DOMSource(doc);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter sw = new StringWriter();
        StreamResult sr = new StreamResult(sw);
        transformer.transform(domSource, sr);
        return sw.toString();
    }

    public String namespaceId(final String id) {
        if (id.startsWith("avalon:")) {
            return id;
        } else {
            return "avalon:" + id;
        }
    }

    private void addField(Document doc, final String name, final String value) {
        addField(doc, name, value, null);
    }

    private void addField(Document doc, final String name, final String value, final String boost) {
        Element field = doc.createElement("field");
        field.setAttribute("name", name);
        if (boost != null) {
            field.setAttribute("boost", boost);
        }
        field.appendChild(doc.createTextNode(value));
        ((Element) doc.getDocumentElement().getElementsByTagName("doc").item(0)).appendChild(field);
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

    private Document getSolrAddDocFromMods(final InputStream sourceMods) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        DocumentBuilder b = f.newDocumentBuilder();
        DOMResult result = new DOMResult();
        transformer.transform(new DOMSource(b.parse(sourceMods)), result);
        return (Document) result.getNode();
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

    private boolean exists(String id) throws Exception {
        if (id.startsWith("avalon:")) {
            HydraSolrManager.AvalonRecord r = getRecord(id);
            if (r == null) {
                return false;
            } else {
                id = r.getId();
            }
        }
        final int status = fcrepo.head(new URI(getURLForId(id))).perform().getStatusCode();
        return (status >= 200 && status < 300);
    }

    private String getURLForMods(String id) {
        return getURLForId(id) + "/descMetadata";
    }

    private String getURLForId(String id) {
        return avalonFedoraBaseUrl + id.substring(0, 2) + "/" + id.substring(2, 4) + "/" + id.substring(4, 6) + "/"
                + id.substring(6, 8) + "/" + id;
    }

    /**
     * Gets a record based on either its current id, or old id.
     */
    private HydraSolrManager.AvalonRecord getRecord(final String id) throws Exception {
        HydraSolrManager m = new HydraSolrManager(getRequiredProperty("hydra-solr-url"));
        List<HydraSolrManager.AvalonRecord> collection = m.getResultingIds("id:\"" + id + "\"");
        if (collection.size() == 1) {
            return collection.get(0);
        } else {
            collection = m.getResultingIds("identifier_ssim:\"" + id + "\"");
            if (collection.size() == 1) {
                return collection.get(0);
            } else {
                return null;
            }
        }

    }

}
