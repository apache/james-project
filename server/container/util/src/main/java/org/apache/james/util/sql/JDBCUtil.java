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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Helper class for managing common JDBC tasks.
 * </p>
 *
 */
public class JDBCUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCUtil.class);

    /**
     * Checks database metadata to see if a table exists. Try UPPER, lower, and
     * MixedCase, to see if the table is there.
     * 
     * @param dbMetaData
     *            the database metadata to be used to look up this table
     * @param tableName
     *            the table name
     * 
     * @throws SQLException
     *             if an exception is encountered while accessing the database
     */
    public boolean tableExists(DatabaseMetaData dbMetaData, String tableName) throws SQLException {
        return (tableExistsCaseSensitive(dbMetaData, tableName) || tableExistsCaseSensitive(dbMetaData, tableName.toUpperCase(Locale.US)) || tableExistsCaseSensitive(dbMetaData, tableName.toLowerCase(Locale.US)));
    }

    /**
     * Checks database metadata to see if a table exists. This method is
     * sensitive to the case of the provided table name.
     * 
     * @param dbMetaData
     *            the database metadata to be used to look up this table
     * @param tableName
     *            the case sensitive table name
     * 
     * @throws SQLException
     *             if an exception is encountered while accessing the database
     */
    public boolean tableExistsCaseSensitive(DatabaseMetaData dbMetaData, String tableName) throws SQLException {
        ResultSet rsTables = dbMetaData.getTables(null, null, tableName, null);
        try {
            return rsTables.next();
        } finally {
            closeJDBCResultSet(rsTables);
        }
    }

    /**
     * Checks database metadata to see if a column exists in a table. Try UPPER,
     * lower, and MixedCase, both on the table name and the column name, to see
     * if the column is there.
     * 
     * @param dbMetaData
     *            the database metadata to be used to look up this column
     * @param tableName
     *            the table name
     * @param columnName
     *            the column name
     * 
     * @throws SQLException
     *             if an exception is encountered while accessing the database
     */
    public boolean columnExists(DatabaseMetaData dbMetaData, String tableName, String columnName) throws SQLException {
        return (columnExistsCaseSensitive(dbMetaData, tableName, columnName) || columnExistsCaseSensitive(dbMetaData, tableName, columnName.toUpperCase(Locale.US)) || columnExistsCaseSensitive(dbMetaData, tableName, columnName.toLowerCase(Locale.US))
                || columnExistsCaseSensitive(dbMetaData, tableName.toUpperCase(Locale.US), columnName) || columnExistsCaseSensitive(dbMetaData, tableName.toUpperCase(Locale.US), columnName.toUpperCase(Locale.US))
                || columnExistsCaseSensitive(dbMetaData, tableName.toUpperCase(Locale.US), columnName.toLowerCase(Locale.US)) || columnExistsCaseSensitive(dbMetaData, tableName.toLowerCase(Locale.US), columnName)
                || columnExistsCaseSensitive(dbMetaData, tableName.toLowerCase(Locale.US), columnName.toUpperCase(Locale.US)) || columnExistsCaseSensitive(dbMetaData, tableName.toLowerCase(Locale.US), columnName.toLowerCase(Locale.US)));
    }

    /**
     * Checks database metadata to see if a column exists in a table. This
     * method is sensitive to the case of both the provided table name and
     * column name.
     * 
     * @param dbMetaData
     *            the database metadata to be used to look up this column
     * @param tableName
     *            the case sensitive table name
     * @param columnName
     *            the case sensitive column name
     * 
     * @throws SQLException
     *             if an exception is encountered while accessing the database
     */
    public boolean columnExistsCaseSensitive(DatabaseMetaData dbMetaData, String tableName, String columnName) throws SQLException {
        ResultSet rsTables = dbMetaData.getColumns(null, null, tableName, columnName);
        try {
            return rsTables.next();
        } finally {
            closeJDBCResultSet(rsTables);
        }
    }

    /**
     * Closes database connection and logs if an error is encountered
     * 
     * @param conn
     *            the connection to be closed
     */
    public void closeJDBCConnection(Connection conn) {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException sqle) {
            // Log exception and continue
            LOGGER.error("Unexpected exception while closing database connection.", sqle);
        }
    }

    /**
     * Closes database statement and logs if an error is encountered
     * 
     * @param stmt
     *            the statement to be closed
     */
    public void closeJDBCStatement(Statement stmt) {
        try {
            if (stmt != null) {
                stmt.close();
            }
        } catch (SQLException sqle) {
            // Log exception and continue
            LOGGER.error("Unexpected exception while closing database statement.", sqle);
        }
    }

    /**
     * Closes database result set and logs if an error is encountered
     * 
     * @param aResultSet
     *            the result set to be closed
     */
    public void closeJDBCResultSet(ResultSet aResultSet) {
        try {
            if (aResultSet != null) {
                aResultSet.close();
            }
        } catch (SQLException sqle) {
            // Log exception and continue
            LOGGER.error("Unexpected exception while closing database result set.", sqle);
        }
    }

}
