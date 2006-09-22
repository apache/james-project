<?xml version="1.0" encoding="ISO-8859-1"?>
<!-- Content Stylesheet for "jakarta-site2" Documentation -->
<!-- NOTE:  Changes here should also be reflected in "site.vsl" and vice
     versa, so either Anakia or XSLT can be used for document generation.   -->
<!-- Outstanding Compatibility Issues (with Anakia-based stylesheets):

* Handling of the <image> element to insert relative path prefixes

-->
<!-- $Id$ -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <!-- Output method -->
  <xsl:output method="xml" encoding="iso-8859-1" doctype-public="-//W3C//DTD XHTML 1.0 Strict//EN" doctype-system="http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd" indent="yes"/>
  <!-- Defined parameters (overrideable) -->
  <xsl:param name="relative-path" select="'.'"/>
  <!-- Defined variables (non-overrideable) -->
  <!-- Process an entire document into an HTML page -->
  <xsl:template match="document">
    <xsl:variable name="site" select="document('project.xml')/site"/>
    <html>
      <head>
        <xsl:apply-templates select="meta"/>
        <title>
          <xsl:value-of select="$site/title"/> - <xsl:value-of select="properties/title"/>
        </title>
        <LINK TITLE="Style" HREF="stylesheet.css" TYPE="text/css" REL="stylesheet" />
        <xsl:for-each select="properties/author">
          <xsl:variable name="name">
            <xsl:value-of select="."/>
          </xsl:variable>
          <xsl:variable name="email">
            <xsl:value-of select="@email"/>
          </xsl:variable>
          <meta value="{$name}" name="author"/>
          <meta value="{$email}" name="email"/>
        </xsl:for-each>
        <xsl:if test="properties/base">
          <base href="{properties/base/@href}"/>
        </xsl:if>
      </head>
      <body>
        <table cellspacing="0" width="100%" border="0" class="page-header">
          <xsl:comment>PAGE HEADER</xsl:comment>
          <tr>
            <td colspan="2">
              <xsl:comment>ASF LOGO</xsl:comment>
        <a href="http://www.apache.org/">
          <img border="0" alt="The ASF" align="left" src="http://www.apache.org/images/asf_logo_wide.gif" />
        </a>
        <xsl:if test="$site/logo">
          <xsl:variable name="alt">
            <xsl:value-of select="$site/logo"/>
          </xsl:variable>
          <xsl:variable name="home">
            <xsl:value-of select="$site/@href"/>
          </xsl:variable>
          <xsl:variable name="src">
            <xsl:value-of select="$site/logo/@href"/>
          </xsl:variable>

          <xsl:comment>PROJECT LOGO</xsl:comment>
          <a href="{$home}">
            <img border="0" alt="{$alt}" align="right" src="{$home}{$src}"/>
          </a>
        </xsl:if>

            </td>
          </tr>
        </table>
        <table cellspacing="4" width="100%" border="0">
          <tr>
            <xsl:comment>LEFT SIDE NAVIGATION</xsl:comment>
            <td nowrap="true" valign="top" class="left-navbar">
              <table cellpadding="0" cellspacing="0" width="100%" border="0"><tr><td>
                <!-- <a href="http://apachecon.com"><img border="0" alt="ApacheCon Promotion" align="left" src="http://apache.org/images/ac2005us_white_184x80.jpg" /></a> -->
                <a href="http://apachecon.com"><img border="0" alt="ApacheCon Promotion" align="left" src="http://www.apache.org/ads/ApacheCon/234x60-2006-us.gif" /></a>
              </td></tr></table>  
              <xsl:apply-templates select="$site/body/navbar[@name='lhs']"/>
            </td>
            <xsl:comment>MAIN BODY</xsl:comment>
            <td align="left" valign="top" class="main-body">
              <xsl:apply-templates select="body/section"/>
            </td>
            <xsl:comment>RIGHT SIDE NAVIGATION</xsl:comment>
            <td nowrap="true" valign="top" class="right-navbar">
              <xsl:apply-templates select="$site/body/navbar[@name='rhs']"/>
            </td>
          </tr>
          <xsl:comment>FOOTER SEPARATOR</xsl:comment>
          <tr>
            <td colspan="3">
              <hr size="1" noshade=""/>
            </td>
          </tr>
          <tr>
            <td colspan="3">
              <div class="page-footer">
                <em>
        Copyright &#169; 1999-2006, The Apache Software Foundation
        </em>
              </div>
            </td>
          </tr>
        </table>
      </body>
    </html>
  </xsl:template>
  <!-- Process a menu for the navigation bar -->
  <xsl:template match="menu">
    <p>
      <strong>
        <xsl:value-of select="@name"/>
      </strong>
    </p>
    <ul>
      <xsl:apply-templates select="item"/>
    </ul>
  </xsl:template>
  <!-- Process a menu item for the navigation bar -->
  <xsl:template match="item">
    <xsl:variable name="href">
      <xsl:choose>
        <xsl:when test="starts-with(@href, 'http://')">
          <xsl:value-of select="@href"/>
        </xsl:when>
        <xsl:when test="starts-with(@href, '/site')">
          <xsl:text>http://jakarta.apache.org</xsl:text>
          <xsl:value-of select="@href"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$relative-path"/>
          <xsl:value-of select="@href"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <li>
      <a href="{$href}">
        <xsl:value-of select="@name"/>
      </a>
    </li>
  </xsl:template>
  <!-- Process a documentation section -->
  <xsl:template match="section">
    <xsl:variable name="name">
      <xsl:value-of select="@name"/>
    </xsl:variable>
    <div class="section">
      <div class="section-header">
        <a name="{$name}">
          <strong>
            <xsl:value-of select="@name"/>
          </strong>
        </a>
      </div>
      <p>
        <div class="section-body">
          <xsl:apply-templates/>
        </div>
      </p>
    </div>
  </xsl:template>
  <!-- Process a documentation subsection -->
  <xsl:template match="subsection">
    <xsl:variable name="name">
      <xsl:value-of select="@name"/>
    </xsl:variable>
    <div class="subsection">
      <!-- Subsection heading -->
      <div class="subsection-header">
        <a name="{$name}">
          <strong>
            <xsl:value-of select="@name"/>
          </strong>
        </a>
      </div>
      <!-- Subsection body -->
      <div class="subsection-body">
        <xsl:apply-templates/>
      </div>
    </div>
  </xsl:template>
  <!-- Process a source code example -->
  <xsl:template match="source">
    <div class="source">
      <xsl:value-of select="."/>
    </div>
  </xsl:template>
  
  
  
<xsl:template match="*/table">
<table cellspacing="0" cellpadding="0" class="detail-table">
  <tbody>
    <xsl:apply-templates/>
  </tbody>
</table>

</xsl:template>
  <xsl:template match="tr">

  <tr class="detail-table-row">
<td class="separator-col"></td>
    <xsl:apply-templates/>
  </tr>
  
  </xsl:template>
  
  <xsl:template match="td">
    <td align="left" valign="top" class="detail-table-content">
      <xsl:if test="@colspan">
        <xsl:attribute name="colspan"><xsl:value-of select="@colspan"/></xsl:attribute>
      </xsl:if>
      <xsl:if test="@rowspan">
        <xsl:attribute name="rowspan"><xsl:value-of select="@rowspan"/></xsl:attribute>
      </xsl:if>
      <xsl:apply-templates/>
    </td><td class="separator-col"></td>
  </xsl:template>
  <!-- handle th ala site.vsl -->
  <xsl:template match="th">
    <td valign="top" class="detail-table-header">
      <xsl:if test="@colspan">
        <xsl:attribute name="colspan"><xsl:value-of select="@colspan"/></xsl:attribute>
      </xsl:if>
      <xsl:if test="@rowspan">
        <xsl:attribute name="rowspan"><xsl:value-of select="@rowspan"/></xsl:attribute>
      </xsl:if>
      <xsl:apply-templates/>
    </td><td class="separator-col"></td>
  </xsl:template>
  <!-- Process everything else by just passing it through -->
  <xsl:template match="*|@*">
    <xsl:copy>
      <xsl:apply-templates select="@*|*|text()"/>
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>
