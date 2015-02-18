A program to generate alternative Solr index records for content
in Avalon.

# Requirements
1. Avalon 3.1 (https://github.com/avalonmediasystem/avalon)
2. Solr core with schema that defines fields for id, *_display,
   *_text, *_facet and *_control
5. Java 1.5+, Maven 3+

# Overview

This is a simple program, meant to be run on a schedule to maintain
a repository of solr add documents for all records in avalon.  The
add documents are generated using the XSLT at
 src/main/resources/avalon-3.1-to-solr.xls.

# Usage
    mvn clean install exec:java -Dexec.mainClass=edu.virginia.lib.avalon.indexer.AvalonIndexer -Dexec.args="development.properties"

View developement.properties for descriptions of the required properties.

