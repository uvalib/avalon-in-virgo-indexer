<?xml version="1.0" encoding="UTF-8"?>
<!-- converts avalon 3.1 objects into solr add documents suitable
       for virgo -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:mods="http://www.loc.gov/mods/v3"
  xmlns:rm="http://hydra-collab.stanford.edu/schemas/rightsMetadata/v1"
  xmlns:dc="http://purl.org/dc/elements/1.1/"
  xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/" version="2.0">

  <xsl:output indent="yes"/>

  <xsl:param name="debug" />

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
    </role>digital
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
        <field name="format_facet">
          <xsl:text>Online</xsl:text>
        </field>
        <field name="format_text">
          <xsl:text>Online</xsl:text>
        </field>
        <field name="source_facet">Digital Library</field>
        <xsl:apply-templates select="*"/>
        <field name="feature_facet">has_embedded_avalon_media</field>
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
      <field name="author_text">
        <xsl:value-of select="$name"/>
      </field>
    </xsl:if>
    <xsl:if test="not($roleCode  = 'cre')">
      <field name="author_added_entry_text">
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
