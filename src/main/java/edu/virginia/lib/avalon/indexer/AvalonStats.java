package edu.virginia.lib.avalon.indexer;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraClientException;
import com.yourmediashelf.fedora.client.FedoraCredentials;
import org.apache.solr.client.solrj.SolrServerException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.transform.stream.StreamSource;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Generates basic stats used in annual reporting for the amount of published resources.
 * Created by md5wz on 7/9/15.
 */
public class AvalonStats {

    private static final List<String> COLLECTION_BLACKLIST = Arrays.asList(new String[]{ "avalon:1", "avalon:2011" });

    public static void main(String [] args) throws IOException, SolrServerException, JAXBException, FedoraClientException {
        if (args.length != 1) {
            System.err.println("Incorrect usage.  Please specify a configuration file as the only argument.");
            System.exit(-1);
        } else {
            Properties p = new Properties();
            p.load(new FileInputStream(args[0]));
            AvalonStats s = new AvalonStats(p);
            s.getStatsForPublicRecords();
        }
    }

    private Properties configuration;

    private FedoraClient fc;

    public AvalonStats(Properties p) throws MalformedURLException {
        configuration = p;

        fc = new FedoraClient(new FedoraCredentials(getRequiredProperty("fedora-url"), getProperty("fedora-username", ""), getProperty("fedora-password", "")));
    }

    public void getStatsForPublicRecords() throws SolrServerException, JAXBException, FedoraClientException {
        int titleCount = 0;
        int videoCount = 0;
        long size = 0;
        HydraSolrManager m = new HydraSolrManager(getRequiredProperty("hydra-solr-url"));
        for (HydraSolrManager.AvalonRecord r : m.getPidsUpdatedSince(null)) {
            if (isRecordPublic(r.getPid()) && !COLLECTION_BLACKLIST.contains(r.getCollectionPid())) {
                titleCount ++;
                for (String partPid : getPartPids(r.getPid())) {
                    videoCount ++;
                    size += getPartSize(partPid);
                }
            }
        }
        System.out.println("Title count:      " + titleCount);
        System.out.println("Media File count: " + videoCount);
        System.out.println("Byte count:       " + size);

    }

    public boolean isRecordPublic(String pid) throws JAXBException, FedoraClientException {
        JAXBContext jc = JAXBContext.newInstance(Workflow.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        JAXBElement<Workflow> w = unmarshaller.unmarshal(new StreamSource(FedoraClient.getDatastreamDissemination(pid, "workflow").execute(fc).getEntityInputStream()), Workflow.class);
        return w.getValue().isPublished();
    }

    public List<String> getPartPids(String pid) throws FedoraClientException, JAXBException {
        List<String> partPids = new ArrayList<String>();
        JAXBContext jc = JAXBContext.newInstance(SectionsMetadata.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        JAXBElement<SectionsMetadata> sm = unmarshaller.unmarshal(new StreamSource(FedoraClient.getDatastreamDissemination(pid, "sectionsMetadata").execute(fc).getEntityInputStream()), SectionsMetadata.class);
        for (String sectionPid : sm.getValue().section_pid) {
            partPids.add(sectionPid);
        }
        return partPids;
    }

    public long getPartSize(String partPid) throws JAXBException, FedoraClientException {
        JAXBContext jc = JAXBContext.newInstance(MasterDescMetadata.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        JAXBElement<MasterDescMetadata> m = unmarshaller.unmarshal(new StreamSource(FedoraClient.getDatastreamDissemination(partPid, "descMetadata").execute(fc).getEntityInputStream()), MasterDescMetadata.class);
        return Long.parseLong(m.getValue().file_size);
    }

    private String getRequiredProperty(String name) {
        if (configuration.containsKey(name)) {
            return configuration.getProperty(name);
        } else {
            throw new RuntimeException("Required property \"" + name + "\" not set!");
        }
    }

    private String getProperty(String name, String defaultValue) {
        if (configuration.containsKey(name)) {
            return configuration.getProperty(name);
        } else {
            return defaultValue;
        }
    }

    @XmlRootElement(name="fields")
    private static class SectionsMetadata {
        @XmlElement private String[] section_pid;
    }

    @XmlRootElement(name="workflow")
    private static class Workflow {
        @XmlElement private String published;

        public boolean isPublished() {
            return "true".equals(published);
        }
    }

    @XmlRootElement(name="fields")
    private static class MasterDescMetadata {
        @XmlElement private String file_size;
    }
}
