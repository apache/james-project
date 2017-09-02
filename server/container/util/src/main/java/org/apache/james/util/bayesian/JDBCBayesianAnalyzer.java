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

package org.apache.james.util.bayesian;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.james.util.sql.JDBCUtil;
import org.apache.james.util.sql.SqlResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the persistence of the spam bayesian analysis corpus using a JDBC
 * database.
 * 
 * <p>
 * This class is abstract to allow implementations to take advantage of
 * different logging capabilities/interfaces in different parts of the code.
 * </p>
 * 
 * @since 2.3.0
 */

public class JDBCBayesianAnalyzer extends BayesianAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCBayesianAnalyzer.class);

    /** Public object representing a lock on database activity. */
    public final static String DATABASE_LOCK = "database lock";

    /**
     * The JDBCUtil helper class
     */
    private final JDBCUtil theJDBCUtil = new JDBCUtil();

    /** Contains all of the sql strings for this component. */
    private final SqlResources sqlQueries = new SqlResources();

    /** Holds value of property sqlFileName. */
    private String sqlFileName;

    /** Holds value of property sqlParameters. */
    private Map<String, String> sqlParameters = new HashMap<>();

    /** Holds value of property lastDatabaseUpdateTime. */
    private static long lastDatabaseUpdateTime;

    /**
     * Getter for property sqlFileName.
     * 
     * @return Value of property sqlFileName.
     */
    public String getSqlFileName() {

        return this.sqlFileName;
    }

    /**
     * Setter for property sqlFileName.
     * 
     * @param sqlFileName
     *            New value of property sqlFileName.
     */
    public void setSqlFileName(String sqlFileName) {

        this.sqlFileName = sqlFileName;
    }

    /**
     * Getter for property sqlParameters.
     * 
     * @return Value of property sqlParameters.
     */
    public Map<String, String> getSqlParameters() {

        return this.sqlParameters;
    }

    /**
     * Setter for property sqlParameters.
     * 
     * @param sqlParameters
     *            New value of property sqlParameters.
     */
    public void setSqlParameters(Map<String, String> sqlParameters) {

        this.sqlParameters = sqlParameters;
    }

    /**
     * Getter for static lastDatabaseUpdateTime.
     * 
     * @return Value of property lastDatabaseUpdateTime.
     */
    public static long getLastDatabaseUpdateTime() {

        return lastDatabaseUpdateTime;
    }

    /**
     * Sets static lastDatabaseUpdateTime to System.currentTimeMillis().
     */
    public static void touchLastDatabaseUpdateTime() {

        lastDatabaseUpdateTime = System.currentTimeMillis();
    }

    /**
     * Default constructor.
     */
    public JDBCBayesianAnalyzer() {
    }

    /**
     * Loads the token frequencies from the database.
     * 
     * @param conn
     *            The connection for accessing the database
     * @throws SQLException
     *             If a database error occurs
     */
    public void loadHamNSpam(Connection conn) throws java.sql.SQLException {
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            pstmt = conn.prepareStatement(sqlQueries.getSqlString("selectHamTokens", true));
            rs = pstmt.executeQuery();

            Map<String, Integer> ham = getHamTokenCounts();
            while (rs.next()) {
                String token = rs.getString(1);
                int count = rs.getInt(2);
                // to reduce memory, use the token only if the count is > 1
                if (count > 1) {
                    ham.put(token, count);
                }
            }
            // Verbose.
            LOGGER.debug("Ham tokens count: " + ham.size());

            rs.close();
            pstmt.close();

            // Get the spam tokens/counts.
            pstmt = conn.prepareStatement(sqlQueries.getSqlString("selectSpamTokens", true));
            rs = pstmt.executeQuery();

            Map<String, Integer> spam = getSpamTokenCounts();
            while (rs.next()) {
                String token = rs.getString(1);
                int count = rs.getInt(2);
                // to reduce memory, use the token only if the count is > 1
                if (count > 1) {
                    spam.put(token, count);
                }
            }

            // Verbose.
            LOGGER.error("Spam tokens count: " + spam.size());

            rs.close();
            pstmt.close();

            // Get the ham/spam message counts.
            pstmt = conn.prepareStatement(sqlQueries.getSqlString("selectMessageCounts", true));
            rs = pstmt.executeQuery();
            if (rs.next()) {
                setHamMessageCount(rs.getInt(1));
                setSpamMessageCount(rs.getInt(2));
            }

            rs.close();
            pstmt.close();

        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (java.sql.SQLException se) {
                    LOGGER.info("Exception ignored", se);
                }

                rs = null;
            }

            if (pstmt != null) {
                try {
                    pstmt.close();
                } catch (java.sql.SQLException se) {
                    LOGGER.info("Exception ignored", se);
                }

                pstmt = null;
            }
        }
    }

    /**
     * Updates the database with new "ham" token frequencies.
     * 
     * @param conn
     *            The connection for accessing the database
     * @throws SQLException
     *             If a database error occurs
     */
    public void updateHamTokens(Connection conn) throws java.sql.SQLException {
        updateTokens(conn, getHamTokenCounts(), sqlQueries.getSqlString("insertHamToken", true), sqlQueries.getSqlString("updateHamToken", true));

        setMessageCount(conn, sqlQueries.getSqlString("updateHamMessageCounts", true), getHamMessageCount());
    }

    /**
     * Updates the database with new "spam" token frequencies.
     * 
     * @param conn
     *            The connection for accessing the database
     * @throws SQLException
     *             If a database error occurs
     */
    public void updateSpamTokens(Connection conn) throws java.sql.SQLException {
        updateTokens(conn, getSpamTokenCounts(), sqlQueries.getSqlString("insertSpamToken", true), sqlQueries.getSqlString("updateSpamToken", true));

        setMessageCount(conn, sqlQueries.getSqlString("updateSpamMessageCounts", true), getSpamMessageCount());
    }

    /**
     * Reset all trained data
     * 
     * @param conn
     *            The connection for accessing the database
     * @throws SQLException
     *             If a database error occours
     */
    public void resetData(Connection conn) throws SQLException {
        deleteData(conn, sqlQueries.getSqlString("deleteHamTokens", true));
        deleteData(conn, sqlQueries.getSqlString("deleteSpamTokens", true));
        deleteData(conn, sqlQueries.getSqlString("deleteMessageCounts", true));
    }

    private void setMessageCount(Connection conn, String sqlStatement, int count) throws java.sql.SQLException {
        PreparedStatement init = null;
        PreparedStatement update = null;

        try {
            // set the ham/spam message counts.
            init = conn.prepareStatement(sqlQueries.getSqlString("initializeMessageCounts", true));
            update = conn.prepareStatement(sqlStatement);

            update.setInt(1, count);

            if (update.executeUpdate() == 0) {
                init.executeUpdate();
                update.executeUpdate();
            }

        } finally {
            if (init != null) {
                try {
                    init.close();
                } catch (java.sql.SQLException ignore) {
                }
            }
            if (update != null) {
                try {
                    update.close();
                } catch (java.sql.SQLException ignore) {
                }
            }
        }
    }

    private void updateTokens(Connection conn, Map<String, Integer> tokens, String insertSqlStatement, String updateSqlStatement) throws java.sql.SQLException {
        PreparedStatement insert = null;
        PreparedStatement update = null;

        try {
            // Used to insert new token entries.
            insert = conn.prepareStatement(insertSqlStatement);

            // Used to update existing token entries.
            update = conn.prepareStatement(updateSqlStatement);

            for (Map.Entry<String, Integer> entry : tokens.entrySet()) {
                update.setInt(1, entry.getValue());
                update.setString(2, entry.getKey());

                // If the update affected 0 (zero) rows, then the token hasn't
                // been
                // encountered before, and we need to add it to the corpus.
                if (update.executeUpdate() == 0) {
                    insert.setString(1, entry.getKey());
                    insert.setInt(2, entry.getValue());

                    insert.executeUpdate();
                }
            }
        } finally {
            if (insert != null) {
                try {
                    insert.close();
                } catch (java.sql.SQLException ignore) {
                }

                insert = null;
            }

            if (update != null) {
                try {
                    update.close();
                } catch (java.sql.SQLException ignore) {
                }

                update = null;
            }
        }
    }

    /**
     * Initializes the sql query environment from the SqlResources file. Will
     * look for conf/sqlResources.xml.
     * 
     * @param conn
     *            The connection for accessing the database
     * @param sqlFile
     *            The sqlResources.xml file
     * @throws Exception
     *             If any error occurs
     */
    public void initSqlQueries(Connection conn, File sqlFile) throws Exception {
        try {
            if (conn.getAutoCommit()) {
                conn.setAutoCommit(false);
            }

            sqlQueries.init(sqlFile, JDBCBayesianAnalyzer.class.getName(), conn, getSqlParameters());

            checkTables(conn);
        } finally {
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    private void checkTables(Connection conn) throws SQLException {
        // Need to ask in the case that identifiers are stored, ask the
        // DatabaseMetaInfo.
        // Try UPPER, lower, and MixedCase, to see if the table is there.

        boolean dbUpdated;

        dbUpdated = createTable(conn, "hamTableName", "createHamTable");

        dbUpdated = createTable(conn, "spamTableName", "createSpamTable");

        dbUpdated = createTable(conn, "messageCountsTableName", "createMessageCountsTable");

        // Commit our changes if necessary.
        if (conn != null && dbUpdated && !conn.getAutoCommit()) {
            conn.commit();
            dbUpdated = false;
        }

    }

    private boolean createTable(Connection conn, String tableNameSqlStringName, String createSqlStringName) throws SQLException {
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

            StringBuffer logBuffer;
            logBuffer = new StringBuffer(64).append("Created table '").append(tableName).append("' using sqlResources string '").append(createSqlStringName).append("'.");
            LOGGER.error(logBuffer.toString());

        } finally {
            theJDBCUtil.closeJDBCStatement(createStatement);
        }

        return true;
    }

    private void deleteData(Connection conn, String deleteSqlStatement) throws SQLException {
        PreparedStatement delete = null;

        try {
            // Used to delete ham tokens
            delete = conn.prepareStatement(deleteSqlStatement);
            delete.executeUpdate();
        } finally {
            if (delete != null) {
                try {
                    delete.close();
                } catch (java.sql.SQLException ignore) {
                }

                delete = null;
            }
        }
    }
}
