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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
        String query = "+has_model_ssim:\"info:fedora/afmodel:MediaObject\"";
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
        params.add("fl", "id is_member_of_collection_ssim");
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
                AvalonRecord record = new AvalonRecord((String) doc.getFieldValue("id"), (String) doc.getFirstValue("is_member_of_collection_ssim"));
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

        private String pid;
        private String collectionPid;

        public AvalonRecord(String pid, String collectionPid) {
            this.pid = pid;
            if (collectionPid.startsWith("info:fedora/")) {
                this.collectionPid = collectionPid.substring(12);
            } else {
                this.collectionPid = collectionPid;
            }
        }

        public String getPid() {
            return pid;
        }

        public String getCollectionPid() {
            return collectionPid;

        }

        public String toString() {
            return "[pid: \"" + pid + "\", collectionPid: \"" + collectionPid + "\"]";
        }
    }

}
