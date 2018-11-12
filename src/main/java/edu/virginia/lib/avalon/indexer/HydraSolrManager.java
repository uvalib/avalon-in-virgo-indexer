package edu.virginia.lib.avalon.indexer;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class HydraSolrManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(HydraSolrManager.class);

    private SolrServer solr;

    public HydraSolrManager(String solrBaseUrl) {
        solr = new HttpSolrServer(solrBaseUrl);
    }

    public List<AvalonRecord> getPidsUpdatedSince(Date date) throws SolrServerException {
        String query = "+has_model_ssim:\"MediaObject\"";
        if (date != null) {
            query += " +system_modified_dtsi:[" + toISO8601DateString(date) + " TO NOW]";
        }
        LOGGER.debug("Searching solr: " + query);
        return getResultingIds(query);
    }

    /**
     * Executes the provided query against the configured Solr server and
     * returns a List of AvalonRecord objects containing minimal information
     * about each record.
     * @param query a solr query
     */
    public List<AvalonRecord> getResultingIds(String query) throws SolrServerException {
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.add("q", query);
        // TODO: improve efficiency with the next line
        // params.add("fl", "id is_member_of_collection_ssim");
        params.add("start", "0");
        params.add("rows", "100");
        QueryResponse r = solr.query(params);
        LOGGER.debug(r.getResults().getNumFound() + " results found.");
        ArrayList<AvalonRecord> results = new ArrayList<AvalonRecord>();
        while (parseResultPage(r, results)) {
            params.set("start", String.valueOf(results.size()));
            LOGGER.trace("Fetching 100 records starting at " + results.size() + "...");
            r = solr.query(params);
        }
        return results;
    }

    private boolean parseResultPage(QueryResponse response, List<AvalonRecord> results) {
        if (response.getResults().isEmpty()) {
            return false;
        } else {
            for (SolrDocument doc : response.getResults()) {
                AvalonRecord record = new AvalonRecord(doc);
                results.add(record);
                LOGGER.debug("Added " + record + ".");
            }
            return results.size() <= response.getResults().getNumFound();
        }
    }

    /**
     * The date format used to parse and generate dates in the ISO8601 format.
     */
    private static DateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    static {
        ISO8601_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * Converts an ISO8601 date String (like "2010-10-01T19:55:00.808Z") to
     * a java Date object.
     */
    public static Date parseISO8601Date(String fedoraDateStr) throws ParseException {
        return ISO8601_DATE_FORMAT.parse(fedoraDateStr);
    }

    /**
     * Converts a java Date object into a n ISO8601 date String.
     */
    public static String toISO8601DateString(Date date) {
        return ISO8601_DATE_FORMAT.format(date);
    }

    public static class AvalonRecord {

        private SolrDocument doc;

        private AvalonRecord(SolrDocument d) {
            doc = d;
        }

        public String getFilename() {
            final String oldId = getOldId();
            if (oldId != null) {
                return oldId.replace(':', '_') + ".xml";
            } else {
                return getId() + ".xml";
            }
        }

        public String getOldId() {
            return (String) doc.getFirstValue("identifier_ssim");
        }

        public boolean isPublished() {
            Collection<Object> vals = doc.getFieldValues("avalon_publisher_ssi");
            if (vals == null || vals.isEmpty()) {
                return false;
            } else {
                return true;
            }
        }

        public boolean isHidden() {
            return "true".equals(doc.getFirstValue("hidden_bsi"));
        }

        public boolean hasThumbnail() {
            return (Boolean) doc.getFirstValue("has_thumbnail?_bs");
        }

        public boolean isMovingImage() {
            Collection<Object> vals = doc.getFieldValues("avalon_resource_type_ssim");
            if (vals == null) {
                return false;
            }
            for (Object o : vals) {
                if (String.valueOf(o).equalsIgnoreCase("Moving image")) {
                    return true;
                }
            }
            return false;
        }

        public boolean isAudioRecording() {
            Collection<Object> vals = doc.getFieldValues("avalon_resource_type_ssim");
            if (vals == null) {
                return false;
            }
            for (Object o : vals) {
                if (String.valueOf(o).equalsIgnoreCase("Sound Recording")) {
                    return true;
                }
            }
            return false;
        }

        public String getName() {
            return (String) doc.getFirstValue("name_ssi");
        }

        public String getTitle() {
            return (String) doc.getFirstValue("title_tesim");
        }

        public String getUnit() {
            return (String) doc.getFirstValue("unit_ssi");
        }

        public String getId() {
            return (String) doc.getFieldValue("id");
        }

        public String getDuration() {
            long ms = Long.parseLong((String) doc.getFirstValue("duration_ssi"));
            long hours = ms / 3600000;
            long minutes = (ms / 60000) % 60;
            long seconds = (ms / 1000) % 60;
            DecimalFormat f = new DecimalFormat("00");
            if (hours > 0) {
                return String.valueOf(hours) + ":" + f.format(minutes) + ":" + f.format(seconds);
            } else {
                return f.format(minutes) + ":" + f.format(seconds);
            }
        }

        public String getAspectRatio() {
            String aspectRatio = (String) doc.getFirstValue("display_aspect_ratio_ssi");
            if (aspectRatio == null) {
                return null;
            }
            if (aspectRatio.equals("2.4")) {
                /*
                 * A bug in avalon causes an incorrect aspect ratio to be presented for ripped
                 * DVDs. This fixes that... no real video is 2.4
                 */
                aspectRatio = "1.779";
            }
            return aspectRatio;
        }

        public List<String> getSectionIds() {
            ArrayList<String> sectionIds = new ArrayList<String>();
            Collection<Object> ids = doc.getFieldValues("section_id_ssim");
            if (ids != null) {
                for (Object id : ids) {
                    sectionIds.add((String) id);
                }
            }
            return sectionIds;
        }

        public String getCollectionId() {
            return (String) doc.getFirstValue("isMemberOfCollection_ssim");
        }

        public String toString() {
            return "[id: \"" + getId() + "\", collectionId: \"" + getCollectionId() + "\"]";
        }
    }

}
