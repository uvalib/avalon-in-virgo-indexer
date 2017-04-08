<?xml version="1.0" encoding="UTF-8"?>
<!-- converts avalon 3.1 objects into solr add documents suitable
       for virgo -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:mods="http://www.loc.gov/mods/v3"
  xmlns:rm="http://hydra-collab.stanford.edu/schemas/rightsMetadata/v1"
  xmlns:dc="http://purl.org/dc/elements/1.1/"
  xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/" version="2.0">

  <xsl:output indent="yes"/>

  <xsl:param name="pid"/>
  <xsl:param name="fedoraBaseUrl"/>
  <xsl:param name="avalonBaseUrl"/>
  <xsl:param name="blacklist"/>

  <xsl:template match="text()" priority="-1"/>

  <xsl:variable name="lowercase" select="'abcdefghijklmnopqrstuvwxyz    '"/>
  <xsl:variable name="uppercase" select="'ABCDEFGHIJKLMNOPQRSTUVWXYZ,;-:.'"/>

  <xsl:variable name="roleCodeMap">
    <role>
      <code>cre</code>
      <label>Creator</label>
    </role>
    <role>
      <code>act</code>
      <label>Actor</label>
    </role>
    <role>
      <code>arr</code>
      <label>Arranger</label>
    </role>
    <role>
      <code>aut</code>
      <label>Author</label>
    </role>
    <role>
      <code>cmp</code>
      <label>Composer</label>
    </role>
    <role>
      <code>cnd</code>
      <label>Conductor</label>
    </role>
    <role>
      <code>cng</code>
      <label>Cinematographer</label>
    </role>
    <role>
      <code>ctb</code>
      <label>Contributor</label>
    </role>
    <role>
      <code>drt</code>
      <label>Director</label>
    </role>
    <role>
      <code>dst</code>
      <label>Distributor</label>
    </role>
    <role>
      <code>edt</code>
      <label>Editor</label>
    </role>
    <role>
      <code>hst</code>
      <label>Host</label>
    </role>
    <role>
      <code>itr</code>
      <label>Instrumentalist</label>
    </role>
    <role>
      <code>ive</code>
      <label>Interviewee</label>
    </role>
    <role>
      <code>mod</code>
      <label>Moderator</label>
    </role>
    <role>
      <code>msd</code>
      <label>Musicaldirector</label>
    </role>
    <role>
      <code>mus</code>
      <label>Musician</label>
    </role>
    <role>
      <code>nrt</code>
      <label>Narrator</label>
    </role>
    <role>
      <code>pan</code>
      <label>Panelist</label>
    </role>
    <role>
      <code>pre</code>
      <label>Presenter</label>
    </role>
    <role>
      <code>pro</code>
      <label>Producer</label>
    </role>
    <role>
      <code>prn</code>
      <label>ProductionCompany</label>
    </role>
    <role>
      <code>aus</code>
      <label>Screenwriter</label>
    </role>
    <role>
      <code>sng</code>
      <label>Singer</label>
    </role>
    <role>
      <code>spk</code>
      <label>Speaker</label>
    </role>
  </xsl:variable>

  <xsl:template match="mods:mods">
    <add>
      <doc>
        <field name="id">
          <xsl:value-of select="$pid"/>
        </field>
        <field name="id_text">
          <xsl:value-of select="$pid"/>
        </field>
        <field name="format_facet">
          <xsl:text>Online</xsl:text>
        </field>
        <field name="format_text">
          <xsl:text>Online</xsl:text>
        </field>
        <field name="source_facet">Digital Library</field>
        <field name="avalon_url_display">
          <xsl:value-of select="$avalonBaseUrl"/>
        </field>
        <xsl:apply-templates select="*"/>
        <xsl:call-template name="processDisplayMetadata"/>
        <xsl:call-template name="processSectionsMetadata"/>
        <xsl:call-template name="processWorkflowMetadata"/>
        <xsl:call-template name="processRelationshipMetadata"/>
        <!-- <field name="has_embedded_avalon_media_control">yes</field> -->
        <field name="feature_facet">has_embedded_avalon_media</field>
        
        <!-- Create field listing configuration-->
        <field name="feature_facet">custom_show_fields</field>
        <field name="custom_show_field_display">
          <xsl:variable name="names" select="mods:name" />
          <xsl:text>{"scopecontent_display":"Abstract","format_facet":"Format","format_display":"Format",</xsl:text>
          <xsl:for-each select="$roleCodeMap/role">
            <xsl:variable name="roleCode" select="./code/text()" />
            <xsl:if test="$names/mods:role/mods:roleTerm[text() = $roleCode]">
              <xsl:value-of select="concat('&quot;', ./code/text(), '_display&quot;:&quot;', ./label/text(), '&quot;,')" />
            </xsl:if>
          </xsl:for-each>
          <xsl:text>"date_display":"Date","issued_date_display":"Date","genre_display":"Genre","abstract_display":"Summary","note_display":"Notes","duration_display":"Duration","contributor_display":"Contributor","publisher_display":"Publisher","temporal_subject_display":"Time period","geographic_subject_display":"Geographic Location","language_display":"Language","extent_display":"Extent","location_display":"Location","terms_of_use_display":"Terms of Use","digital_collection_facet":"Collection","related_item_display":"Related Item"}</xsl:text>
        </field>
        
      </doc>
    </add>
  </xsl:template>

  <xsl:template match="mods:titleInfo[@usage = 'primary']">
    <field name="title_display">
      <xsl:value-of select="mods:title"/>
    </field>
    <field name="title_text">
      <xsl:value-of select="mods:title"/>
    </field>
    <field name="full_title_text">
      <xsl:value-of select="mods:title"/>
    </field>
    <field name="title_sort_facet">
      <xsl:value-of select="translate(mods:title, $uppercase, $lowercase)"/>
    </field>
  </xsl:template>

  <xsl:template match="mods:name">
    <xsl:variable name="name">
      <xsl:call-template name="stripParentheticRole">
        <xsl:with-param name="value" select="mods:namePart[1]"/>
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="roleCode" select="mods:role/mods:roleTerm[@type = 'code']"/>
    <field>
      <xsl:attribute name="name">
        <xsl:value-of select="concat($roleCode, '_display')"/>
      </xsl:attribute>
      <xsl:value-of select="$name"/>
    </field>
    <xsl:if test="$roleCode = 'cre'">
      <field name="author_facet">
        <xsl:value-of select="$name"/>
      </field>
    </xsl:if>
    <field name="name_text">
      <xsl:value-of select="$name" />
    </field>
  </xsl:template>

  <xsl:template match="mods:abstract">
    <field name="abstract_display">
      <xsl:value-of select="text()"/>
    </field>
    <field name="abstract_text">
      <xsl:value-of select="text()"/>
    </field>
  </xsl:template>

  <xsl:template match="mods:originInfo/mods:dateIssued">
    <field name="issued_date_text">
      <xsl:value-of select="text()"/>
    </field>
    <field name="issued_date_display">
      <xsl:value-of select="text()"/>
    </field>
    <xsl:variable name="yearIssued" select="number(substring(text(), 1, 4))"/>
    <xsl:if test="number($yearIssued)">
      <field name="year_multisort_i">
        <xsl:value-of select="$yearIssued"/>
      </field>
      <xsl:variable name="age"
        select="number(substring(string(current-date()), 1, 4)) - number($yearIssued)"/>
      <xsl:if test="$age &lt;= 1">
        <field name="published_date_facet">
          <xsl:text>This year</xsl:text>
        </field>
      </xsl:if>
      <xsl:if test="$age &lt;= 3">
        <field name="published_date_facet">
          <xsl:text>Last 3 years</xsl:text>
        </field>
      </xsl:if>
      <xsl:if test="$age &lt;= 10">
        <field name="published_date_facet">
          <xsl:text>Last 10 years</xsl:text>
        </field>
      </xsl:if>
      <xsl:if test="$age &lt;= 50">
        <field name="published_date_facet">
          <xsl:text>Last 50 years</xsl:text>
        </field>
      </xsl:if>
      <xsl:if test="$age &gt; 50">
        <field name="published_date_facet">
          <xsl:text>More than 50 years ago</xsl:text>
        </field>
      </xsl:if>
    </xsl:if>
  </xsl:template>

  <xsl:template match="mods:originInfo/mods:dateCreated">
    <field name="created_date_text">
      <xsl:value-of select="text()"/>
    </field>
    <field name="created_date_display">
      <xsl:value-of select="text()"/>
    </field>
    <field name="published_date_display">
      <xsl:value-of select="text()"/>
    </field>
    
  </xsl:template>

  <xsl:template match="mods:originInfo/mods:publisher">
    <field name="publisher_text">
      <xsl:value-of select="text()"/>
    </field>
    <field name="publisher_display">
      <xsl:value-of select="text()"/>
    </field>
  </xsl:template>

  <xsl:template match="mods:genre">
    <field name="genre_display">
      <xsl:value-of select="text()"/>
    </field>
    <field name="genre_text">
      <xsl:value-of select="text()"/>
    </field>
  </xsl:template>

  <xsl:template match="mods:subject/mods:topic">
    <field name="subject_facet">
      <xsl:value-of select="text()"/>
    </field>
    <field name="topic_text">
      <xsl:value-of select="text()"/>
    </field>
  </xsl:template>

  <xsl:template match="mods:subject/mods:temporal">
    <field name="temporal_subject_display">
      <xsl:value-of select="text()"/>
    </field>
    <field name="temporal_subject_text">
      <xsl:value-of select="text()"/>
    </field>
  </xsl:template>

  <xsl:template match="mods:subject/mods:geographic">
    <field name="region_facet">
      <xsl:value-of select="text()"/>
    </field>
    <field name="region_text">
      <xsl:value-of select="text()"/>
    </field>
    <field name="geographic_subject_display">
      <xsl:value-of select="text()"/>
    </field>
  </xsl:template>
  
  <xsl:template match="mods:language/mods:languageTerm[@type='text']">
    <field name="language_facet">
      <xsl:value-of select="text()"/>
    </field>
    <field name="language_display">
      <xsl:value-of select="text()"/>
    </field>
  </xsl:template>
  
  <xsl:template match="mods:relatedItem[@displayLabel]">
    <field name="related_item_display">
      <xsl:value-of select="@displayLabel" />
    </field>
    <!-- TODO: once Virgo can handle it, add the URL to the display -->
    <field name="related_item_text">
      <xsl:value-of select="@displayLabel" />
    </field>
    <field name="related_item_text">
      <xsl:value-of select="mods:location/mods:url" />
    </field>
  </xsl:template>
  
  <xsl:template match="mods:note">
    <field name="note_display">
      <xsl:value-of select="text()" />
    </field>
    <field name="note_text">
      <xsl:value-of select="text()" />
    </field>
  </xsl:template>
  
  <xsl:template match="mods:tableOfContents">
    <field name="toc_display">
      <xsl:value-of select="text()" />
    </field>
    <field name="toc_text">
      <xsl:value-of select="text()" />
    </field>
  </xsl:template>

  <xsl:template match="mods:accessCondition[@type = 'use and reproduction']">
    <field name="terms_of_use_display">
      <xsl:value-of select="text()"/>
    </field>
    <field name="terms_of_use_text">
      <xsl:value-of select="text()"/>
    </field>
  </xsl:template>

  <xsl:template match="mods:recordInfo/mods:recordChangeDate">
    <!-- record change date -->
  </xsl:template>

  <xsl:template name="processDatastream">
    <xsl:param name="dsid" required="yes"/>
    <xsl:param name="pid" required="yes"/>
    <xsl:variable name="url"
      select="concat($fedoraBaseUrl, '/objects/', $pid, '/datastreams/', $dsid, '/content')"/>
    <xsl:message>
      <xsl:text>Requesting XML datastream at "</xsl:text>
      <xsl:value-of select="$url"/>
      <xsl:text>"...</xsl:text>
    </xsl:message>
    <xsl:copy-of select="document($url)"/>
  </xsl:template>

  <xsl:template name="getDatastreamList">
    <xsl:param name="pid" required="yes"/>
    <xsl:variable name="url"
      select="concat($fedoraBaseUrl, '/objects/', $pid, '/datastreams?format=xml')"/>
    <xsl:message>
      <xsl:text>Requesting XML listing datastream at "</xsl:text>
      <xsl:value-of select="$url"/>
      <xsl:text>"...</xsl:text>
    </xsl:message>
    <xsl:copy-of select="document($url)"/>
  </xsl:template>

  <xsl:template name="processDisplayMetadata">
    <xsl:variable name="content">
      <xsl:call-template name="processDatastream">
        <xsl:with-param name="pid" select="$pid"/>
        <xsl:with-param name="dsid">displayMetadata</xsl:with-param>
      </xsl:call-template>
    </xsl:variable>
    <xsl:apply-templates mode="displayMetadata" select="$content/*"/>
  </xsl:template>

  <xsl:template match="text()" priority="-1" mode="displayMetadata"/>

  <xsl:template match="duration" mode="displayMetadata">
    <field name="duration_display">
      <xsl:call-template name="prettyPrintDurationInMS">
        <xsl:with-param name="duration" select="text()"/>
      </xsl:call-template>
    </field>
  </xsl:template>
  
  <xsl:template match="avalon_resource_type" mode="displayMetadata">
    <xsl:if test="text() = 'sound recording'">
      <field name="format_facet">
        <xsl:text>Streaming Audio</xsl:text>
      </field>
      <field name="format_text">
        <xsl:text>Sound Recording</xsl:text>
      </field>
      <field name="format_text">
        <xsl:text>Streaming Audio</xsl:text>
      </field>
    </xsl:if>
    <xsl:if test="text() = 'moving image'">
      <field name="format_facet">
        <xsl:text>Online Video</xsl:text>
      </field>
      <field name="format_facet">
        <xsl:text>Video</xsl:text>
      </field>
      <field name="format_text">
        <xsl:text>Online Video</xsl:text>
      </field>
    </xsl:if>
  </xsl:template>

  <xsl:template name="processSectionsMetadata">
    <xsl:variable name="content">
      <xsl:call-template name="processDatastream">
        <xsl:with-param name="pid" select="$pid"/>
        <xsl:with-param name="dsid">sectionsMetadata</xsl:with-param>
      </xsl:call-template>
    </xsl:variable>
    <xsl:apply-templates mode="sectionsMetadata" select="$content/*"/>
  </xsl:template>

  <xsl:template match="section_pid" mode="sectionsMetadata">
    <xsl:variable name="partPid" select="text()"/>

    <!-- if it's the first part, parse the datastream listing to see if a thumbnail exists -->
    <xsl:if test="not(current()/preceding-sibling::section_pid)">
      <xsl:variable name="dsList">
        <xsl:call-template name="getDatastreamList">
          <xsl:with-param name="pid" select="$partPid"/>
        </xsl:call-template>
      </xsl:variable>
      <xsl:if test="$dsList/apia:objectDatastreams/apia:datastream[@dsid = 'thumbnail']"
        xmlns:apia="http://www.fedora.info/definitions/1/0/access/">
        <field name="thumbnail_url_display">
          <xsl:value-of select="concat($avalonBaseUrl, '/master_files/', $partPid, '/thumbnail')"/>
        </field>
      </xsl:if>
    </xsl:if>

    <!-- get the label from the object profile, YUCK! -->
    <xsl:variable name="url" select="concat($fedoraBaseUrl, '/objects/', $partPid, '?format=xml')"/>
    <xsl:message>
      <xsl:text>Requesting XML object profile at "</xsl:text>
      <xsl:value-of select="$url"/>
      <xsl:text>"...</xsl:text>
    </xsl:message>
    <xsl:apply-templates mode="partMetadata" select="document($url)/*"/>

    <!-- get the part duration from the object descriptive metadata -->
    <xsl:variable name="content">
      <xsl:call-template name="processDatastream">
        <xsl:with-param name="pid" select="$partPid"/>
        <xsl:with-param name="dsid">descMetadata</xsl:with-param>
      </xsl:call-template>
    </xsl:variable>
    <field name="part_pid_display">
      <xsl:value-of select="$partPid"/>
    </field>
    <xsl:apply-templates mode="partMetadata" select="$content/*"/>
  </xsl:template>

  <xsl:template match="text()" priority="-1" mode="partMetadata"/>

  <xsl:template match="duration" mode="partMetadata">
    <field name="part_duration_display">
      <xsl:call-template name="prettyPrintDurationInMS">
        <xsl:with-param name="duration" select="text()"/>
      </xsl:call-template>
    </field>
  </xsl:template>

  <xsl:template name="prettyPrintDurationInMS">
    <xsl:param name="duration" required="yes"/>
    <xsl:variable name="duration_in_ms" select="$duration"/>
    <xsl:variable name="hours" select="floor($duration_in_ms div 3600000)"/>
    <xsl:variable name="minutes" select="floor($duration_in_ms div 60000) mod 60"/>
    <xsl:variable name="seconds" select="floor($duration_in_ms div 1000) mod 60"/>
    <xsl:choose>
      <xsl:when test="$hours > 0">
        <xsl:value-of
          select="concat(format-number($hours, '#'), ':', format-number($minutes, '00'), ':', format-number($seconds, '00'))"
        />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of
          select="concat(format-number($minutes, '#0'), ':', format-number($seconds, '00'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="display_aspect_ratio" mode="partMetadata">
    <xsl:if test="text() eq '2.4'">
      <!-- 
        A bug in avalon causes an incorrect aspect ratio to be presented for ripped DVDs.
        This fixes that... no real video is 2.4
         -->
      <field name="display_aspect_ratio_display">1.779</field>
    </xsl:if>
    <xsl:if test="text() ne '2.4'">
      <field name="display_aspect_ratio_display">
        <xsl:value-of select="text()"/>
      </field>
    </xsl:if>
  </xsl:template>

  <!--
  <xsl:template match="original_frame_size" mode="partMetadata">
    <field name="display_aspect_ratio_display"><xsl:value-of select="number(substring-before(text(), 'x')) div number(substring-after(text(), 'x'))" /></field>
  </xsl:template>
  -->


  <xsl:template match="apia:objLabel" xmlns:apia="http://www.fedora.info/definitions/1/0/access/"
    mode="partMetadata">
    <field name="part_label_display">
      <xsl:value-of select="text()"/>
    </field>
  </xsl:template>

  <xsl:template name="processWorkflowMetadata">
    <xsl:variable name="workflow">
      <xsl:call-template name="processDatastream">
        <xsl:with-param name="pid" select="$pid"/>
        <xsl:with-param name="dsid">workflow</xsl:with-param>
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="rights">
      <xsl:call-template name="processDatastream">
        <xsl:with-param name="pid" select="$pid"/>
        <xsl:with-param name="dsid">rightsMetadata</xsl:with-param>
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="discoveryGroup"
      select="$rights/rm:rightsMetadata/rm:access[@type = 'discover']/rm:machine/rm:group"/>
    <xsl:variable name="DC">
      <xsl:call-template name="processDatastream">
        <xsl:with-param name="pid" select="$pid"/>
        <xsl:with-param name="dsid">DC</xsl:with-param>
      </xsl:call-template>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="$blacklist eq 'true'">
        <field name="shadowed_location_facet">HIDDEN</field>
      </xsl:when>
      <xsl:when test="$DC/oai_dc:dc/dc:publisher and $discoveryGroup/text() eq 'nobody'">
        <field name="shadowed_location_facet">HIDDEN</field>
      </xsl:when>
      <xsl:when test="$DC/oai_dc:dc/dc:publisher">
        <field name="shadowed_location_facet">VISIBLE</field>
      </xsl:when>
      <xsl:otherwise>
        <field name="shadowed_location_facet">HIDDEN</field>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="processRelationshipMetadata">
    <xsl:variable name="content">
      <xsl:call-template name="processDatastream">
        <xsl:with-param name="pid" select="$pid"/>
        <xsl:with-param name="dsid">RELS-EXT</xsl:with-param>
      </xsl:call-template>
    </xsl:variable>
    <xsl:apply-templates mode="relationshipMetadata" select="$content/*"/>
  </xsl:template>


  <xsl:template match="text()" priority="-1" mode="relationshipMetadata"/>

  <xsl:template match="relsext:isMemberOfCollection" mode="relationshipMetadata"
    xmlns:relsext="info:fedora/fedora-system:def/relations-external#">
    <xsl:variable name="collectionPid">
      <xsl:value-of select="substring(@rdf:resource, 13)"
        xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"/>
    </xsl:variable>
    <xsl:variable name="content">
      <xsl:call-template name="processDatastream">
        <xsl:with-param name="pid" select="$collectionPid"/>
        <xsl:with-param name="dsid">descMetadata</xsl:with-param>
      </xsl:call-template>
    </xsl:variable>
    <xsl:apply-templates mode="collectionMetadata" select="$content/*"/>
  </xsl:template>

  <xsl:template match="text()" priority="-1" mode="collectionMetadata"/>

  <xsl:template match="name" mode="collectionMetadata">
    <xsl:if test="not(starts-with(text(), 'MSS'))">
      <field name="digital_collection_facet">
        <xsl:value-of select="text()"/>
      </field>
    </xsl:if>
    <field name="digital_collection_text" boost="0.25">
      <xsl:value-of select="text()"/>
    </field>
  </xsl:template>

  <xsl:template match="unit" mode="collectionMetadata">
    <field name="unit_display">
      <xsl:value-of select="text()"/>
    </field>
    <field name="unit_text" boost="0.25">
      <xsl:value-of select="text()"/>
    </field>
  </xsl:template>

  <xsl:template name="stripParentheticRole">
    <xsl:param name="value" required="yes"/>
    <xsl:analyze-string select="$value" regex="^(.*) \([^(]+\)$">
      <xsl:matching-substring>
        <xsl:value-of select="regex-group(1)"/>
      </xsl:matching-substring>
      <xsl:non-matching-substring>
        <xsl:value-of select="$value"/>
      </xsl:non-matching-substring>
    </xsl:analyze-string>
  </xsl:template>

</xsl:stylesheet>
