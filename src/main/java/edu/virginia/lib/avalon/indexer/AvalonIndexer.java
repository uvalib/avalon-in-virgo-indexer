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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AvalonIndexer {

    public static void main(String [] args) throws Exception {
        String fedoraUrl = args[0];
        String avalonUrl = args[1];
        String solrUrl = args[2];
        String fedoraUsername = args.length > 3 ? args[3] : "";
        String fedoraPassword = args.length > 4 ? args[4] : "";

        FedoraClient fc = new FedoraClient(new FedoraCredentials(fedoraUrl, fedoraUsername, fedoraPassword));
        AvalonIndexer ai = new AvalonIndexer(fc, solrUrl, fedoraUrl, avalonUrl);
        for (String pid : ai.getAllObjectPids()) {
            System.out.println(ai.generateAddDoc(pid));
            ai.indexPid(pid);
        }
        ai.commit();
        ai.optimize();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AvalonIndexer.class);

    private HttpClient httpClient;

    private FedoraClient client;

    private String fedoraBaseUrl;

    private String solrUpdateUrl;

    private String avalonBaseUrl;

    private Transformer transformer;

    public AvalonIndexer(FedoraClient fedoraClient, String solrUpdateUrl, String fedoraUrl, String avalonUrl) throws TransformerConfigurationException, FedoraClientException {
        this.client = fedoraClient;
        this.fedoraBaseUrl = fedoraUrl;
        this.avalonBaseUrl = avalonUrl;
        this.solrUpdateUrl = solrUpdateUrl;

        this.httpClient = new HttpClient();

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Templates templates = tFactory.newTemplates(
                new StreamSource(getClass().getClassLoader().getResourceAsStream("avalon-3.1-to-solr.xsl")));
        this.transformer = templates.newTransformer();
    }

    public List<String> getAllObjectPids() throws Exception {
        return getSubjects(client, "afmodel:MediaObject",  "info:fedora/fedora-system:def/model#hasModel");
    }

    public void indexPid(String pid) throws ParserConfigurationException, TransformerException, IOException, SAXException, FedoraClientException {
        postContent(generateAddDoc(pid));
    }

    public String generateAddDoc(String pid) throws FedoraClientException, ParserConfigurationException, TransformerException, SAXException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        applyTransformation(FedoraClient.getDatastreamDissemination(pid, "descMetadata").execute(client).getEntityInputStream(), baos, pid);
        return new String(baos.toByteArray());
    }

    private void applyTransformation(InputStream source, OutputStream destination, String pid)
            throws ParserConfigurationException, IOException, SAXException, TransformerException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        DocumentBuilder b = f.newDocumentBuilder();
        transformer.setParameter("pid", pid);
        transformer.setParameter("fedoraBaseUrl", fedoraBaseUrl);
        transformer.setParameter("avalonBaseUrl", avalonBaseUrl);
        transformer.transform(new DOMSource(b.parse(source)), new StreamResult(destination));
    }

    public void postContent(String content) throws HttpException, IOException {
        PostMethod post = new PostMethod(solrUpdateUrl);
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
                throw new RuntimeException("REST action \"" + solrUpdateUrl + "\" failed: " + post.getStatusLine());
            }
        } finally {
            post.releaseConnection();
        }
    }

    public void commit() throws HttpException, IOException {
        String url = solrUpdateUrl + "?stream.body=%3Ccommit/%3E";
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
        String url = solrUpdateUrl + "?stream.body=%3Coptimize/%3E";
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
}
