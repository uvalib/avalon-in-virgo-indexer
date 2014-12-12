A simple application that identifies all Avalon resources in a
Fedora repository and indexes them to a solr index.

# Requirements
1. Avalon 3.1 (https://github.com/avalonmediasystem/avalon)
2. Solr core with schema that defines fields for id, *_display,
   *_text, *_facet and *_control
3. Fedora 3.x with the resource index enabled
4. Java 1.5+, Maven 3+

# Overview

This is jus a simple java program that queries fedora to identify
all the avalon objects then runs their descMetadata datastream through
the XSLT at main/resources/avalon-3.1-to-solr.xls and posts the response
to solr.

# Usage
    mvn clean install exec:java -Dexec.mainClass=edu.virginia.lib.avalon.indexer.AvalonIndexer -Dexec.args "http://localhost:8080/fedora http://localhost:3000/avalon http://localhost:8983/solr/core/update"

Replace the fedora, avalon and solr update URLs with those corresponding to the services you wish to point to.
You can optionally provide two additional arguments representing the fedora username and password if necessary.