/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.util.sql;

import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

/**
 * Provides a set of SQL String resources (eg SQL Strings) to use for a database
 * connection.<br>
 * This class allows SQL strings to be customised to particular database
 * products, by detecting product information from the jdbc DatabaseMetaData
 * object.
 * 
 */
public class SqlResources {
    /** A map of statement types to SQL statements */
    private final Map<String, String> m_sql = new HashMap<>();

    /** A map of engine specific options */
    private final Map<String, String> m_dbOptions = new HashMap<>();

    /** A set of all used String values */
    static private final Map<String, String> stringTable = java.util.Collections.synchronizedMap(new HashMap<String, String>());

    /**
     * <p>
     * Configures a DbResources object to provide SQL statements from a file.
     * </p>
     * <p>
     * SQL statements returned may be specific to the particular type and
     * version of the connected database, as well as the database driver.
     * </p>
     * <p>
     * Parameters encoded as $(parameter} in the input file are replace by
     * values from the parameters Map, if the named parameter exists. Parameter
     * values may also be specified in the resourceSection element.
     * </p>
     * 
     * @param sqlFile
     *            the input file containing the string definitions
     * @param sqlDefsSection
     *            the xml element containing the strings to be used
     * @param conn
     *            the Jdbc DatabaseMetaData, taken from a database connection
     * @param configParameters
     *            a map of parameters (name-value string pairs) which are
     *            replaced where found in the input strings
     */
    public void init(File sqlFile, String sqlDefsSection, Connection conn, Map<String, String> configParameters) throws Exception {
        // Parse the sqlFile as an XML document.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document sqlDoc = builder.parse(sqlFile);

        init(sqlDoc, sqlDefsSection, conn, configParameters);
    }

    /**
     * <p>
     * Configures a DbResources object to provide SQL statements from an
     * InputStream.
     * </p>
     * <p>
     * SQL statements returned may be specific to the particular type and
     * version of the connected database, as well as the database driver.
     * </p>
     * <p>
     * Parameters encoded as $(parameter} in the input file are replace by
     * values from the parameters Map, if the named parameter exists. Parameter
     * values may also be specified in the resourceSection element.
     * </p>
     * 
     * @param input
     *            the input stream containing the xml
     * @param sqlDefsSection
     *            the xml element containing the strings to be used
     * @param conn
     *            the Jdbc DatabaseMetaData, taken from a database connection
     * @param configParameters
     *            a map of parameters (name-value string pairs) which are
     *            replaced where found in the input strings
     */
    public void init(InputStream input, String sqlDefsSection, Connection conn, Map<String, String> configParameters) throws Exception {
        // Parse the InputStream as an XML document.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document sqlDoc = builder.parse(input);

        init(sqlDoc, sqlDefsSection, conn, configParameters);
    }

    /**
     * Configures a SqlResources object from an xml document.
     * 
     * @param sqlDoc
     * @param sqlDefsSection
     * @param conn
     * @param configParameters
     * @throws SQLException
     */
    protected void init(Document sqlDoc, String sqlDefsSection, Connection conn, Map<String, String> configParameters) throws SQLException {
        // First process the database matcher, to determine the
        // sql statements to use.
        Element dbMatcherElement = (Element) (sqlDoc.getElementsByTagName("dbMatchers").item(0));
        String dbProduct = null;
        if (dbMatcherElement != null) {
            dbProduct = matchDbConnection(conn, dbMatcherElement);
        }

        // Now get the options valid for the database product used.
        Element dbOptionsElement = (Element) (sqlDoc.getElementsByTagName("dbOptions").item(0));
        if (dbOptionsElement != null) {
            // First populate the map with default values
            populateDbOptions("", dbOptionsElement, m_dbOptions);
            // Now update the map with specific product values
            if (dbProduct != null) {
                populateDbOptions(dbProduct, dbOptionsElement, m_dbOptions);
            }
        }

        // Now get the section defining sql for the repository required.
        NodeList sections = sqlDoc.getElementsByTagName("sqlDefs");
        int sectionsCount = sections.getLength();
        Element sectionElement = null;
        boolean found = false;
        for (int i = 0; i < sectionsCount; i++) {
            sectionElement = (Element) (sections.item(i));
            String sectionName = sectionElement.getAttribute("name");
            if (sectionName != null && sectionName.equals(sqlDefsSection)) {
                found = true;
                break;
            }

        }
        if (!found) {
            String exceptionBuffer = "Error loading sql definition file. " + "The element named \'" + sqlDefsSection + "\' does not exist.";
            throw new RuntimeException(exceptionBuffer);
        }

        // Get parameters defined within the file as defaults,
        // and use supplied parameters as overrides.
        Map<String, String> parameters = new HashMap<>();
        // First read from the <params> element, if it exists.
        Element parametersElement = (Element) (sectionElement.getElementsByTagName("parameters").item(0));
        if (parametersElement != null) {
            NamedNodeMap params = parametersElement.getAttributes();
            int paramCount = params.getLength();
            for (int i = 0; i < paramCount; i++) {
                Attr param = (Attr) params.item(i);
                String paramName = param.getName();
                String paramValue = param.getValue();
                parameters.put(paramName, paramValue);
            }
        }
        // Then copy in the parameters supplied with the call.
        parameters.putAll(configParameters);

        // 2 maps - one for storing default statements,
        // the other for statements with a "db" attribute matching this
        // connection.
        Map<String, String> defaultSqlStatements = new HashMap<>();
        Map<String, String> dbProductSqlStatements = new HashMap<>();

        // Process each sql statement, replacing string parameters,
        // and adding to the appropriate map..
        NodeList sqlDefs = sectionElement.getElementsByTagName("sql");
        int sqlCount = sqlDefs.getLength();
        for (int i = 0; i < sqlCount; i++) {
            // See if this needs to be processed (is default or product
            // specific)
            Element sqlElement = (Element) (sqlDefs.item(i));
            String sqlDb = sqlElement.getAttribute("db");
            Map<String, String> sqlMap;
            if (sqlDb.equals("")) {
                // default
                sqlMap = defaultSqlStatements;
            } else if (sqlDb.equals(dbProduct)) {
                // Specific to this product
                sqlMap = dbProductSqlStatements;
            } else {
                // for a different product
                continue;
            }

            // Get the key and value for this SQL statement.
            String sqlKey = sqlElement.getAttribute("name");
            if (sqlKey == null) {
                // ignore statements without a "name" attribute.
                continue;
            }
            String sqlString = sqlElement.getFirstChild().getNodeValue();

            // Do parameter replacements for this sql string.
            StringBuilder replaceBuffer = new StringBuilder(64);
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                replaceBuffer.setLength(0);
                replaceBuffer.append("${").append(entry.getKey()).append("}");
                sqlString = substituteSubString(sqlString, replaceBuffer.toString(), entry.getValue());
            }

            // See if we already have registered a string of this value
            String shared = stringTable.get(sqlString);
            // If not, register it -- we will use it next time
            if (shared == null) {
                stringTable.put(sqlString, sqlString);
            } else {
                sqlString = shared;
            }

            // Add to the sqlMap - either the "default" or the "product" map
            sqlMap.put(sqlKey, sqlString);
        }

        // Copy in default strings, then overwrite product-specific ones.
        m_sql.putAll(defaultSqlStatements);
        m_sql.putAll(dbProductSqlStatements);
    }

    /**
     * Compares the DatabaseProductName value for a jdbc Connection against a
     * set of regular expressions defined in XML.<br>
     * The first successful match defines the name of the database product
     * connected to. This value is then used to choose the specific SQL
     * expressions to use.
     * 
     * @param conn
     *            the JDBC connection being tested
     * @param dbMatchersElement
     *            the XML element containing the database type information
     * 
     * @return the type of database to which James is connected
     * 
     */
    private String matchDbConnection(Connection conn, Element dbMatchersElement) throws SQLException {
        String dbProductName = conn.getMetaData().getDatabaseProductName();

        NodeList dbMatchers = dbMatchersElement.getElementsByTagName("dbMatcher");
        for (int i = 0; i < dbMatchers.getLength(); i++) {
            // Get the values for this matcher element.
            Element dbMatcher = (Element) dbMatchers.item(i);
            String dbMatchName = dbMatcher.getAttribute("db");
            Pattern dbProductPattern = Pattern.compile(dbMatcher.getAttribute("databaseProductName"), Pattern.CASE_INSENSITIVE);

            // If the connection databaseProcuctName matches the pattern,
            // use the match name from this matcher.
            if (dbProductPattern.matcher(dbProductName).find()) {
                return dbMatchName;
            }
        }
        return null;
    }

    /**
     * Gets all the name/value pair db option couples related to the dbProduct,
     * and put them into the dbOptionsMap.
     * 
     * @param dbProduct
     *            the db product used
     * @param dbOptionsElement
     *            the XML element containing the options
     * @param dbOptionsMap
     *            the <code>Map</code> to populate
     * 
     */
    private void populateDbOptions(String dbProduct, Element dbOptionsElement, Map<String, String> dbOptionsMap) {
        NodeList dbOptions = dbOptionsElement.getElementsByTagName("dbOption");
        for (int i = 0; i < dbOptions.getLength(); i++) {
            // Get the values for this option element.
            Element dbOption = (Element) dbOptions.item(i);
            // Check is this element is pertinent to the dbProduct
            // Notice that a missing attribute returns "", good for defaults
            if (!dbProduct.equalsIgnoreCase(dbOption.getAttribute("db"))) {
                continue;
            }
            // Put into the map
            dbOptionsMap.put(dbOption.getAttribute("name"), dbOption.getAttribute("value"));
        }
    }

    /**
     * Replace substrings of one string with another string and return altered
     * string.
     * 
     * @param input
     *            input string
     * @param find
     *            the string to replace
     * @param replace
     *            the string to replace with
     * @return the substituted string
     */
    private String substituteSubString(String input, String find, String replace) {
        int find_length = find.length();
        int replace_length = replace.length();

        StringBuilder output = new StringBuilder(input);
        int index = input.indexOf(find);
        int outputOffset = 0;

        while (index > -1) {
            output.replace(index + outputOffset, index + outputOffset + find_length, replace);
            outputOffset = outputOffset + (replace_length - find_length);

            index = input.indexOf(find, index + find_length);
        }

        return output.toString();
    }

    /**
     * Returns a named SQL string for the specified connection, replacing
     * parameters with the values set.
     * 
     * @param name
     *            the name of the SQL resource required.
     * @return the requested resource
     */
    public String getSqlString(String name) {
        return m_sql.get(name);
    }

    /**
     * Returns a named SQL string for the specified connection, replacing
     * parameters with the values set.
     * 
     * @throws RuntimeException
     *             if a required resource cannot be found.
     * 
     * @param name
     *            the name of the SQL resource required.
     * @param required
     *            true if the resource is required
     * @return the requested resource
     */
    public String getSqlString(String name, boolean required) {
        String sql = getSqlString(name);

        if (sql == null && required) {
            String exceptionBuffer = "Required SQL resource: '" + name + "' was not found.";
            throw new RuntimeException(exceptionBuffer);
        }
        return sql;
    }

    /**
     * Returns the dbOption string value set for the specified dbOption name.
     * 
     * @param name
     *            the name of the dbOption required.
     * @return the requested dbOption value
     */
    public String getDbOption(String name) {
        return m_dbOptions.get(name);
    }

}
