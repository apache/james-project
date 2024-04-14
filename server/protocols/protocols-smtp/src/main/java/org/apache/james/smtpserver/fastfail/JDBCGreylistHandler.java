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
package org.apache.james.smtpserver.fastfail;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.library.netmatcher.NetMatcher;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.fastfail.AbstractGreylistHandler;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.util.DurationParser;
import org.apache.james.util.sql.JDBCUtil;
import org.apache.james.util.sql.SqlResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GreylistHandler which can be used to activate Greylisting
 */
public class JDBCGreylistHandler extends AbstractGreylistHandler implements ProtocolHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCGreylistHandler.class);

    private DataSource datasource = null;

    private FileSystem fileSystem = null;

    private String selectQuery;

    private String insertQuery;

    private String deleteQuery;

    private String deleteAutoWhiteListQuery;

    private String updateQuery;

    /**
     * Contains all of the sql strings for this component.
     */
    private final SqlResources sqlQueries = new SqlResources();

    /** The sqlFileUrl */
    private String sqlFileUrl;

    /** Holds value of property sqlParameters. */
    private final Map<String, String> sqlParameters = new HashMap<>();

    private DNSService dnsService;

    private NetMatcher wNetworks;

    /**
     * Gets the file system service.
     * 
     * @return the fileSystem
     */
    public final FileSystem getFileSystem() {
        return fileSystem;
    }

    /**
     * Sets the filesystem service
     * 
     * @param system
     *            The filesystem service
     */
    @Inject
    public void setFileSystem(FileSystem system) {
        this.fileSystem = system;
    }

    /**
     * Set the datasources.
     * 
     * @param datasource
     *            The datasource
     */
    @Inject
    public void setDataSource(DataSource datasource) {
        this.datasource = datasource;
    }

    /**
     * Set the sqlFileUrl to use for getting the sqlRessource.xml file
     * 
     * @param sqlFileUrl
     *            The fileUrl
     */
    public void setSqlFileUrl(String sqlFileUrl) {
        this.sqlFileUrl = sqlFileUrl;
    }

    /**
     * Setup the temporary blocking time
     * 
     * @param tempBlockTime
     *            The temporary blocking time
     */
    public void setTempBlockTime(String tempBlockTime) {
        setTempBlockTime(DurationParser.parse(tempBlockTime));
    }

    /**
     * Setup the autowhitelist lifetime for which we should whitelist a triplet.
     * After this lifetime the record will be deleted
     * 
     * @param autoWhiteListLifeTime
     *            The lifeTime
     */
    public void setAutoWhiteListLifeTime(String autoWhiteListLifeTime) {
        setAutoWhiteListLifeTime(DurationParser.parse(autoWhiteListLifeTime));
    }

    /**
     * Set up the liftime of only once seen triplet. After this liftime the
     * record will be deleted
     * 
     * @param unseenLifeTime
     *            The lifetime
     */
    public void setUnseenLifeTime(String unseenLifeTime) {
        setUnseenLifeTime(DurationParser.parse(unseenLifeTime));
    }

    @Inject
    public final void setDNSService(DNSService dnsService) {
        this.dnsService = dnsService;
    }

    protected NetMatcher getWhiteListedNetworks() {
        return wNetworks;
    }
    
    @Override
    protected Iterator<String> getGreyListData(String ipAddress, String sender, String recip) throws SQLException {
        Collection<String> data = new ArrayList<>(2);
        PreparedStatement mappingStmt = null;
        Connection conn = datasource.getConnection();
        try {
            mappingStmt = conn.prepareStatement(selectQuery);
            ResultSet mappingRS = null;
            try {
                mappingStmt.setString(1, ipAddress);
                mappingStmt.setString(2, sender);
                mappingStmt.setString(3, recip);
                mappingRS = mappingStmt.executeQuery();

                if (mappingRS.next()) {
                    data.add(String.valueOf(mappingRS.getTimestamp(1).getTime()));
                    data.add(String.valueOf(mappingRS.getInt(2)));
                }
            } finally {
                theJDBCUtil.closeJDBCResultSet(mappingRS);
            }
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
        return data.iterator();
    }

    @Override
    protected void insertTriplet(String ipAddress, String sender, String recip, int count, Instant createTime) throws SQLException {
        Connection conn = datasource.getConnection();

        PreparedStatement mappingStmt = null;

        try {
            mappingStmt = conn.prepareStatement(insertQuery);

            mappingStmt.setString(1, ipAddress);
            mappingStmt.setString(2, sender);
            mappingStmt.setString(3, recip);
            mappingStmt.setInt(4, count);
            mappingStmt.setTimestamp(5, new Timestamp(createTime.getEpochSecond()));
            mappingStmt.executeUpdate();
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    @Override
    protected void updateTriplet(String ipAddress, String sender, String recip, int count, Instant time) throws SQLException {
        Connection conn = datasource.getConnection();
        PreparedStatement mappingStmt = null;

        try {
            mappingStmt = conn.prepareStatement(updateQuery);
            mappingStmt.setTimestamp(1, new Timestamp(time.getEpochSecond()));
            mappingStmt.setInt(2, (count + 1));
            mappingStmt.setString(3, ipAddress);
            mappingStmt.setString(4, sender);
            mappingStmt.setString(5, recip);
            mappingStmt.executeUpdate();
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    @Override
    protected void cleanupAutoWhiteListGreyList(Instant time) throws SQLException {
        PreparedStatement mappingStmt = null;
        Connection conn = datasource.getConnection();

        try {
            mappingStmt = conn.prepareStatement(deleteAutoWhiteListQuery);

            mappingStmt.setTimestamp(1, new Timestamp(time.getEpochSecond()));

            mappingStmt.executeUpdate();
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    @Override
    protected void cleanupGreyList(Instant time) throws SQLException {
        Connection conn = datasource.getConnection();

        PreparedStatement mappingStmt = null;

        try {
            mappingStmt = conn.prepareStatement(deleteQuery);

            mappingStmt.setTimestamp(1, new Timestamp(time.getEpochSecond()));

            mappingStmt.executeUpdate();
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * The JDBCUtil helper class
     */
    private final JDBCUtil theJDBCUtil = new JDBCUtil();

    /**
     * Initializes the sql query environment from the SqlResources file. Will
     * look for conf/sqlResources.xml.
     * 
     * @param conn
     *            The connection for accessing the database
     * @param sqlFileUrl
     *            The url which we use to get the sql file
     * @throws Exception
     *             If any error occurs
     */
    private void initSqlQueries(Connection conn, String sqlFileUrl) throws Exception {
        try {

            File sqlFile;

            try {
                sqlFile = fileSystem.getFile(sqlFileUrl);
                sqlFileUrl = null;
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            }

            sqlQueries.init(sqlFile.getCanonicalFile(), "GreyList", conn, sqlParameters);

            selectQuery = sqlQueries.getSqlString("selectQuery", true);
            insertQuery = sqlQueries.getSqlString("insertQuery", true);
            deleteQuery = sqlQueries.getSqlString("deleteQuery", true);
            deleteAutoWhiteListQuery = sqlQueries.getSqlString("deleteAutoWhitelistQuery", true);
            updateQuery = sqlQueries.getSqlString("updateQuery", true);

        } finally {
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Create the table if not exists.
     * 
     * @param tableNameSqlStringName
     *            The tableSqlname
     * @param createSqlStringName
     *            The createSqlname
     * @return true or false
     * @throws SQLException
     */
    private boolean createTable(String tableNameSqlStringName, String createSqlStringName) throws SQLException {
        Connection conn = datasource.getConnection();
        try {
            String tableName = sqlQueries.getSqlString(tableNameSqlStringName, true);

            DatabaseMetaData dbMetaData = conn.getMetaData();

            // Try UPPER, lower, and MixedCase, to see if the table is there.
            if (theJDBCUtil.tableExists(dbMetaData, tableName)) {
                return false;
            }

            PreparedStatement createStatement = null;

            try {
                createStatement = conn.prepareStatement(sqlQueries.getSqlString(createSqlStringName, true));
                createStatement.execute();

                LOGGER.info("Created table '{}' using sqlResources string '{}'.", tableName, createSqlStringName);

            } finally {
                theJDBCUtil.closeJDBCStatement(createStatement);
            }
            return true;
        } finally {
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    @Override
    public HookResult doRcpt(SMTPSession session, MaybeSender sender, MailAddress rcpt) {
        if ((wNetworks == null) || (!wNetworks.matchInetNetwork(session.getRemoteAddress().getAddress().getHostAddress()))) {
            return super.doRcpt(session, sender, rcpt);
        } else {
            LOGGER.info("IpAddress {} is whitelisted. Skip greylisting.", session.getRemoteAddress().getAddress().getHostAddress());
        }
        return HookResult.DECLINED;
    }

    @Override
    public void init(Configuration handlerConfiguration) throws ConfigurationException {
        try {
            setTempBlockTime(handlerConfiguration.getString("tempBlockTime"));
        } catch (NumberFormatException e) {
            throw new ConfigurationException(e.getMessage());
        }

        try {
            setAutoWhiteListLifeTime(handlerConfiguration.getString("autoWhiteListLifeTime"));
        } catch (NumberFormatException e) {
            throw new ConfigurationException(e.getMessage());
        }

        try {
            setUnseenLifeTime(handlerConfiguration.getString("unseenLifeTime"));
        } catch (NumberFormatException e) {
            throw new ConfigurationException(e.getMessage());
        }
        String nets = handlerConfiguration.getString("whitelistedNetworks");
        if (nets != null) {
            String[] whitelistArray = nets.split(",");
            List<String> wList = new ArrayList<>(whitelistArray.length);
            for (String aWhitelistArray : whitelistArray) {
                wList.add(aWhitelistArray.trim());
            }
            wNetworks = new NetMatcher(wList, dnsService);
            LOGGER.info("Whitelisted addresses: {}", getWhiteListedNetworks());
        }

        // Get the SQL file location
        String sFile = handlerConfiguration.getString("sqlFile", null);
        if (sFile != null) {

            setSqlFileUrl(sFile);

            if (!sqlFileUrl.startsWith("file://") && !sqlFileUrl.startsWith("classpath:")) {
                throw new ConfigurationException("Malformed sqlFile - Must be of the format \"file://<filename>\".");
            }
        } else {
            throw new ConfigurationException("sqlFile is not configured");
        }
        try {
            initSqlQueries(datasource.getConnection(), sqlFileUrl);

            // create table if not exist
            createTable("greyListTableName", "createGreyListTable");
        } catch (Exception e) {
            throw new RuntimeException("Unable to init datasource", e);
        }
    }
}
