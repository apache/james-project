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
package org.apache.james.rrt.jdbc;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.sql.DataSource;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.util.sql.JDBCUtil;
import org.apache.james.util.sql.SqlResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible to implement the Virtual User Table in database with JDBC
 * access.
 * 
 * @deprecated use JPARecipientRewriteTable
 */
@Deprecated
public class JDBCRecipientRewriteTable extends AbstractRecipientRewriteTable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCRecipientRewriteTable.class);

    private DataSource dataSource = null;

    private String tableName = "RecipientRewriteTable";

    /**
     * Contains all of the sql strings for this component.
     */
    private SqlResources sqlQueries;

    /**
     * The name of the SQL configuration file to be used to configure this
     * repository.
     */
    private String sqlFileName;

    private FileSystem fileSystem;

    /**
     * The JDBCUtil helper class
     */
    private final JDBCUtil theJDBCUtil = new JDBCUtil() {
        protected void delegatedLog(String logString) {
            LOGGER.debug("JDBCRecipientRewriteTable: " + logString);
        }
    };

    @PostConstruct
    public void init() throws Exception {

        StringBuffer logBuffer;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(this.getClass().getName() + ".initialize()");
        }

        // Test the connection to the database, by getting the DatabaseMetaData.
        Connection conn = dataSource.getConnection();
        PreparedStatement createStatement = null;

        try {
            // Initialise the sql strings.

            InputStream sqlFile;

            try {
                sqlFile = fileSystem.getResource(sqlFileName);
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            }

            if (LOGGER.isDebugEnabled()) {
                logBuffer = new StringBuffer(128).append("Reading SQL resources from file: ").append(sqlFileName).append(", section ").append(this.getClass().getName()).append(".");
                LOGGER.debug(logBuffer.toString());
            }

            // Build the statement parameters
            Map<String, String> sqlParameters = new HashMap<>();
            if (tableName != null) {
                sqlParameters.put("table", tableName);
            }

            sqlQueries = new SqlResources();
            sqlQueries.init(sqlFile, this.getClass().getName(), conn, sqlParameters);

            // Check if the required table exists. If not, create it.
            DatabaseMetaData dbMetaData = conn.getMetaData();

            // Need to ask in the case that identifiers are stored, ask the
            // DatabaseMetaInfo.
            // Try UPPER, lower, and MixedCase, to see if the table is there.
            if (!(theJDBCUtil.tableExists(dbMetaData, tableName))) {

                // Users table doesn't exist - create it.
                createStatement = conn.prepareStatement(sqlQueries.getSqlString("createTable", true));
                createStatement.execute();

                if (LOGGER.isInfoEnabled()) {
                    logBuffer = new StringBuffer(64).append("JdbcVirtalUserTable: Created table '").append(tableName).append("'.");
                    LOGGER.info(logBuffer.toString());
                }
            }

        } finally {
            theJDBCUtil.closeJDBCStatement(createStatement);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Inject
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected void doConfigure(HierarchicalConfiguration conf) throws ConfigurationException {

        String destination = conf.getString("[@destinationURL]", null);

        if (destination == null) {
            throw new ConfigurationException("destinationURL must configured");
        }

        // normalize the destination, to simplify processing.
        if (!destination.endsWith("/")) {
            destination += "/";
        }
        // Parse the DestinationURL for the name of the datasource,
        // the table to use, and the (optional) repository Key.
        // Split on "/", starting after "db://"
        List<String> urlParams = new ArrayList<>();
        int start = 5;

        int end = destination.indexOf('/', start);
        while (end > -1) {
            urlParams.add(destination.substring(start, end));
            start = end + 1;
            end = destination.indexOf('/', start);
        }

        // Build SqlParameters and get datasource name from URL parameters
        if (urlParams.size() == 0) {
            String exceptionBuffer = "Malformed destinationURL - Must be of the format '" + "db://<data-source>'.  Was passed " + conf.getString("[@destinationURL]");
            throw new ConfigurationException(exceptionBuffer);
        }

        if (urlParams.size() >= 2) {
            tableName = urlParams.get(1);
        }

        if (LOGGER.isDebugEnabled()) {
            String logBuffer = "Parsed URL: table = '" + tableName + "'";
            LOGGER.debug(logBuffer);
        }

        sqlFileName = conf.getString("sqlFile");

    }

    /**
     * @throws RecipientRewriteTableException
     * @see org.apache.james.rrt.lib.AbstractRecipientRewriteTable#addMappingInternal(String,
     *      String, String)
     */
    protected void addMappingInternal(String user, String domain, String regex) throws RecipientRewriteTableException {
        String fixedUser = getFixedUser(user);
        String fixedDomain = getFixedDomain(domain);
        Mappings map = getUserDomainMappings(fixedUser, fixedDomain);
        if (map != null && map.size() != 0) {
            Mappings updatedMappings = MappingsImpl.from(map).add(regex).build();
            doUpdateMapping(fixedUser, fixedDomain, updatedMappings.serialize());
        }
        doAddMapping(fixedUser, fixedDomain, regex);
    }

    /**
     * @see org.apache.james.rrt.lib.AbstractRecipientRewriteTable#mapAddressInternal(java.lang.String,
     *      java.lang.String)
     */
    protected String mapAddressInternal(String user, String domain) throws RecipientRewriteTableException {
        Connection conn = null;
        PreparedStatement mappingStmt = null;
        try {
            conn = dataSource.getConnection();
            mappingStmt = conn.prepareStatement(sqlQueries.getSqlString("selectMappings", true));

            ResultSet mappingRS = null;
            try {
                mappingStmt.setString(1, user);
                mappingStmt.setString(2, domain);
                mappingRS = mappingStmt.executeQuery();
                if (mappingRS.next()) {
                    return mappingRS.getString(1);
                }
            } finally {
                theJDBCUtil.closeJDBCResultSet(mappingRS);
            }

        } catch (SQLException sqle) {
            LOGGER.error("Error accessing database", sqle);
            throw new RecipientRewriteTableException("Error accessing database", sqle);
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
        return null;
    }

    /**
     * @throws RecipientRewriteTableException
     * @see org.apache.james.rrt.lib.AbstractRecipientRewriteTable#mapAddress(java.lang.String,
     *      java.lang.String)
     */
    protected Mappings getUserDomainMappingsInternal(String user, String domain) throws RecipientRewriteTableException {
        Connection conn = null;
        PreparedStatement mappingStmt = null;
        try {
            conn = dataSource.getConnection();
            mappingStmt = conn.prepareStatement(sqlQueries.getSqlString("selectUserDomainMapping", true));
            ResultSet mappingRS = null;
            try {
                mappingStmt.setString(1, user);
                mappingStmt.setString(2, domain);
                mappingRS = mappingStmt.executeQuery();
                if (mappingRS.next()) {
                    return MappingsImpl.fromRawString(mappingRS.getString(1));
                }
            } finally {
                theJDBCUtil.closeJDBCResultSet(mappingRS);
            }
        } catch (SQLException sqle) {
            LOGGER.error("Error accessing database", sqle);
            throw new RecipientRewriteTableException("Error accessing database", sqle);
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
        return null;
    }

    /**
     * @throws RecipientRewriteTableException
     * @see org.apache.james.rrt.lib.AbstractRecipientRewriteTable#getAllMappingsInternal()
     */
    protected Map<String, Mappings> getAllMappingsInternal() throws RecipientRewriteTableException {
        Connection conn = null;
        PreparedStatement mappingStmt = null;
        Map<String, Mappings> mapping = new HashMap<>();
        try {
            conn = dataSource.getConnection();
            mappingStmt = conn.prepareStatement(sqlQueries.getSqlString("selectAllMappings", true));
            ResultSet mappingRS = null;
            try {
                mappingRS = mappingStmt.executeQuery();
                while (mappingRS.next()) {
                    String user = mappingRS.getString(1);
                    String domain = mappingRS.getString(2);
                    String map = mappingRS.getString(3);
                    mapping.put(user + "@" + domain, MappingsImpl.fromRawString(map));
                }
                if (mapping.size() > 0)
                    return mapping;
            } finally {
                theJDBCUtil.closeJDBCResultSet(mappingRS);
            }

        } catch (SQLException sqle) {
            LOGGER.error("Error accessing database", sqle);
            throw new RecipientRewriteTableException("Error accessing database", sqle);
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
        return null;
    }

    /**
     * @throws RecipientRewriteTableException
     * @see org.apache.james.rrt.lib.AbstractRecipientRewriteTable#removeMappingInternal(String,
     *      String, String)
     */
    protected void removeMappingInternal(String user, String domain, String mapping) throws RecipientRewriteTableException {
        String fixedUser = getFixedUser(user);
        String fixedDomain = getFixedDomain(domain);
        Mappings map = getUserDomainMappings(fixedUser, fixedDomain);
        if (map != null && map.size() > 1) {
            Mappings updatedMappings = map.remove(mapping);
            doUpdateMapping(fixedUser, fixedDomain, updatedMappings.serialize());
        } else {
            doRemoveMapping(fixedUser, fixedDomain, mapping);
        }
    }

    /**
     * Update the mapping for the given user and domain
     * 
     * @param user
     *            the user
     * @param domain
     *            the domain
     * @param mapping
     *            the mapping
     * @return true if update was successfully
     * @throws RecipientRewriteTableException
     */
    private void doUpdateMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        Connection conn = null;
        PreparedStatement mappingStmt = null;

        try {
            conn = dataSource.getConnection();
            mappingStmt = conn.prepareStatement(sqlQueries.getSqlString("updateMapping", true));

            ResultSet mappingRS = null;
            try {
                mappingStmt.setString(1, mapping);
                mappingStmt.setString(2, user);
                mappingStmt.setString(3, domain);

                if (mappingStmt.executeUpdate() < 1) {
                    throw new RecipientRewriteTableException("Mapping not found");
                }
            } finally {
                theJDBCUtil.closeJDBCResultSet(mappingRS);
            }

        } catch (SQLException sqle) {
            LOGGER.error("Error accessing database", sqle);
            throw new RecipientRewriteTableException("Error accessing database", sqle);
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Remove a mapping for the given user and domain
     * 
     * @param user
     *            the user
     * @param domain
     *            the domain
     * @param mapping
     *            the mapping
     * @return true if succesfully
     * @throws RecipientRewriteTableException
     */
    private void doRemoveMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        Connection conn = null;
        PreparedStatement mappingStmt = null;

        try {
            conn = dataSource.getConnection();
            mappingStmt = conn.prepareStatement(sqlQueries.getSqlString("deleteMapping", true));

            ResultSet mappingRS = null;
            try {
                mappingStmt.setString(1, user);
                mappingStmt.setString(2, domain);
                mappingStmt.setString(3, mapping);
                if (mappingStmt.executeUpdate() < 1) {
                    throw new RecipientRewriteTableException("Mapping not found");
                }
            } finally {
                theJDBCUtil.closeJDBCResultSet(mappingRS);
            }

        } catch (SQLException sqle) {
            LOGGER.error("Error accessing database", sqle);
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Add mapping for given user and domain
     * 
     * @param user
     *            the user
     * @param domain
     *            the domain
     * @param mapping
     *            the mapping
     * @return true if successfully
     * @throws RecipientRewriteTableException
     */
    private void doAddMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        Connection conn = null;
        PreparedStatement mappingStmt = null;

        try {
            conn = dataSource.getConnection();
            mappingStmt = conn.prepareStatement(sqlQueries.getSqlString("addMapping", true));

            ResultSet mappingRS = null;
            try {
                mappingStmt.setString(1, user);
                mappingStmt.setString(2, domain);
                mappingStmt.setString(3, mapping);

                if (mappingStmt.executeUpdate() < 1) {
                    throw new RecipientRewriteTableException("Mapping not found");
                }
            } finally {
                theJDBCUtil.closeJDBCResultSet(mappingRS);
            }

        } catch (SQLException sqle) {
            LOGGER.error("Error accessing database", sqle);
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

}
