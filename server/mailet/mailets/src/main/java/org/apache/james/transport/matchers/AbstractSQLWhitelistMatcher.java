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

package org.apache.james.transport.matchers;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import javax.inject.Inject;
import javax.sql.DataSource;

import jakarta.mail.MessagingException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.transport.mailets.WhiteListManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.sql.JDBCUtil;
import org.apache.james.util.sql.SqlResources;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No documentation is available for this deprecated, experimental matcher.
 */
@SuppressWarnings("deprecation")
@Experimental
public abstract class AbstractSQLWhitelistMatcher extends GenericMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSQLWhitelistMatcher.class);

    /**
     * The user repository for this mail server. Contains all the users with
     * inboxes on this server.
     */
    private UsersRepository localusers;

    protected DataSource datasource;
    protected JDBCUtil jdbcUtil;

    /** Holds value of property sqlParameters. */
    private final Map<String, String> sqlParameters = new HashMap<>();


    @Inject
    public void setDataSource(DataSource datasource) {
        this.datasource = datasource;
    }

    @Inject
    public void setUsersRepository(UsersRepository localusers) {
        this.localusers = localusers;
    }
    
    @Inject
    public void setJdbcUtil(JDBCUtil jdbcUtil) {
        this.jdbcUtil = jdbcUtil;
    }

    /**
     * Getter for property sqlParameters.
     * 
     * @return Value of property sqlParameters.
     */
    private Map<String, String> getSqlParameters() {
        return this.sqlParameters;
    }

    /**
     * The JDBCUtil helper class
     */
    protected final JDBCUtil theJDBCUtil = new JDBCUtil();

    /**
     * Contains all of the sql strings for this component.
     */
    protected final SqlResources sqlQueries = new SqlResources();

    private FileSystem fs;

    @Inject
    public void setFilesystem(FileSystem fs) {
        this.fs = fs;
    }

    @Override
    public void init() throws MessagingException {
        String repositoryPath = null;
        StringTokenizer st = new StringTokenizer(getCondition(), ", \t", false);
        if (st.hasMoreTokens()) {
            repositoryPath = st.nextToken().trim();
        }
        if (repositoryPath != null) {
            LOGGER.info("repositoryPath: {}", repositoryPath);
        } else {
            throw new MessagingException("repositoryPath is null");
        }

        try {
            initSqlQueries(datasource.getConnection(), getMailetContext());
        } catch (Exception e) {
            throw new MessagingException("Exception initializing queries", e);
        }

        super.init();
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        // check if it's a local sender
        if (!mail.hasSender()) {
            return null;
        }
        MailAddress senderMailAddress = mail.getMaybeSender().get();
        if (getMailetContext().isLocalEmail(senderMailAddress)) {
            // is a local sender, so return
            return null;
        }

        String senderUser = senderMailAddress.getLocalPart();

        senderUser = senderUser.toLowerCase(Locale.US);

        Collection<MailAddress> recipients = mail.getRecipients();

        Collection<MailAddress> inWhiteList = new java.util.HashSet<>();

        for (MailAddress recipientMailAddress : recipients) {
            String recipientUser = recipientMailAddress.getLocalPart().toLowerCase(Locale.US);
            Domain recipientHost = recipientMailAddress.getDomain();

            if (!getMailetContext().isLocalServer(recipientHost)) {
                // not a local recipient, so skip
                continue;
            }

            if (matchedWhitelist(recipientMailAddress, mail)) {
                // This address was already in the list
                inWhiteList.add(recipientMailAddress);
            }

        }

        return inWhiteList;

    }

    protected abstract boolean matchedWhitelist(MailAddress recipient, Mail mail) throws MessagingException;

    /**
     * Initializes the sql query environment from the SqlResources file.<br>
     * Will look for conf/sqlResources.xml.<br>
     * Will <strong>not</<strong> create the database resources, if missing<br>
     * (this task is done, if needed, in the {@link WhiteListManager}
     * initialization routine).
     * 
     * @param conn
     *            The connection for accessing the database
     * @param mailetContext
     *            The current mailet context, for finding the
     *            conf/sqlResources.xml file
     * @throws Exception
     *             If any error occurs
     */
    protected void initSqlQueries(Connection conn, org.apache.mailet.MailetContext mailetContext) throws Exception {
        try {
            if (conn.getAutoCommit()) {
                conn.setAutoCommit(false);
            }

            /* Holds value of property sqlFile. */
            File sqlFile = fs.getFile("classpath:sqlResources.xml");
            sqlQueries.init(sqlFile, getSQLSectionName(), conn, getSqlParameters());
            checkTables(conn);
        } finally {
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    protected abstract String getTableName();

    protected abstract String getTableCreateQueryName();

    private void checkTables(Connection conn) throws SQLException {

        // Need to ask in the case that identifiers are stored, ask the
        // DatabaseMetaInfo.
        // Try UPPER, lower, and MixedCase, to see if the table is there.

        boolean dbUpdated;

        dbUpdated = createTable(conn, getTableName(), getTableCreateQueryName());

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

            LOGGER.info("Created table '{}' using sqlResources string '{}'.", tableName, createSqlStringName);

        } finally {
            theJDBCUtil.closeJDBCStatement(createStatement);
        }

        return true;
    }

    protected abstract String getSQLSectionName();
}
