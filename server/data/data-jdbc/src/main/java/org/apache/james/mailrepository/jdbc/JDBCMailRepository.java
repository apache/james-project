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

package org.apache.james.mailrepository.jdbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.sql.DataSource;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.core.MailImpl;
import org.apache.james.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.core.MimeMessageWrapper;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailrepository.lib.AbstractMailRepository;
import org.apache.james.repository.file.FilePersistentStreamRepository;
import org.apache.james.util.sql.JDBCUtil;
import org.apache.james.util.sql.SqlResources;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a MailRepository on a database.
 * 
 * <p>
 * Requires a configuration element in the .conf.xml file of the form:
 * 
 * <pre>
 *  &lt;repository destinationURL="db://&lt;datasource&gt;/&lt;table_name&gt;/&lt;repository_name&gt;"
 *              type="MAIL"
 *              model="SYNCHRONOUS"/&gt;
 *  &lt;/repository&gt;
 * </pre>
 * 
 * </p>
 * <p>
 * destinationURL specifies..(Serge??) <br>
 * Type can be SPOOL or MAIL <br>
 * Model is currently not used and may be dropped
 * </p>
 * 
 * <p>
 * Requires a logger called MailRepository.
 * </p>
 * 
 * @version CVS $Revision$ $Date: 2010-12-29 21:47:46 +0100 (Wed, 29
 *          Dec 2010) $
 */
public class JDBCMailRepository extends AbstractMailRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCMailRepository.class);

    /**
     * The table name parsed from the destination URL
     */
    protected String tableName;

    /**
     * The repository name parsed from the destination URL
     */
    protected String repositoryName;

    /**
     * The name of the SQL configuration file to be used to configure this
     * repository.
     */
    private String sqlFileName;

    /**
     * The stream repository used in dbfile mode
     */
    private FilePersistentStreamRepository sr = null;

    /**
     * The JDBC datasource that provides the JDBC connection
     */
    protected DataSource datasource;

    /**
     * The name of the datasource used by this repository
     */
    protected String datasourceName;

    /**
     * Contains all of the sql strings for this component.
     */
    protected SqlResources sqlQueries;

    /**
     * The JDBCUtil helper class
     */
    protected JDBCUtil theJDBCUtil;

    /**
     * "Support for Mail Attributes under JDBC repositories is ready" indicator.
     */
    protected boolean jdbcMailAttributesReady = false;

    /**
     * The size threshold for in memory handling of storing operations
     */
    private int inMemorySizeLimit;

    private FileSystem fileSystem;

    private String filestore;

    private String destination;

    @Inject
    public void setDatasource(DataSource datasource) {
        this.datasource = datasource;
    }

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    protected void doConfigure(HierarchicalConfiguration configuration) throws ConfigurationException {
        super.doConfigure(configuration);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(this.getClass().getName() + ".configure()");
        }
        destination = configuration.getString("[@destinationURL]");

        // normalize the destination, to simplify processing.
        if (!destination.endsWith("/")) {
            destination += "/";
        }
        // Parse the DestinationURL for the name of the datasource,
        // the table to use, and the (optional) repository Key.
        // Split on "/", starting after "db://"
        List<String> urlParams = new ArrayList<>();
        int start = 5;
        if (destination.startsWith("dbfile")) {
            // this is dbfile:// instead of db://
            start += 4;
        }
        int end = destination.indexOf('/', start);
        while (end > -1) {
            urlParams.add(destination.substring(start, end));
            start = end + 1;
            end = destination.indexOf('/', start);
        }

        // Build SqlParameters and get datasource name from URL parameters
        if (urlParams.size() == 0) {
            String exceptionBuffer = "Malformed destinationURL - Must be of the format '" + "db://<data-source>[/<table>[/<repositoryName>]]'.  Was passed " + configuration.getString("[@destinationURL]");
            throw new ConfigurationException(exceptionBuffer);
        }
        if (urlParams.size() >= 1) {
            datasourceName = urlParams.get(0);
        }
        if (urlParams.size() >= 2) {
            tableName = urlParams.get(1);
        }
        if (urlParams.size() >= 3) {
            repositoryName = "";
            for (int i = 2; i < urlParams.size(); i++) {
                if (i >= 3) {
                    repositoryName += '/';
                }
                repositoryName += urlParams.get(i);
            }
        }

        if (LOGGER.isDebugEnabled()) {
            String logBuffer = "Parsed URL: table = '" + tableName + "', repositoryName = '" + repositoryName + "'";
            LOGGER.debug(logBuffer);
        }

        inMemorySizeLimit = configuration.getInt("inMemorySizeLimit", 409600000);

        filestore = configuration.getString("filestore", null);
        sqlFileName = configuration.getString("sqlFile");

    }

    /**
     * Initialises the JDBC repository.
     * <ol>
     * <li>Tests the connection to the database.</li>
     * <li>Loads SQL strings from the SQL definition file, choosing the
     * appropriate SQL for this connection, and performing paramter
     * substitution,</li>
     * <li>Initialises the database with the required tables, if necessary.</li>
     * </ol>
     * 
     * @throws Exception
     *             if an error occurs
     */
    @Override
    @PostConstruct
    public void init() throws Exception {
        StringBuffer logBuffer;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(this.getClass().getName() + ".initialize()");
        }

        try {
            if (filestore != null) {

                // prepare Configurations for stream repositories
                DefaultConfigurationBuilder streamConfiguration = new DefaultConfigurationBuilder();

                streamConfiguration.addProperty("[@destinationURL]", filestore);

                sr = new FilePersistentStreamRepository();
                sr.setFileSystem(fileSystem);
                sr.configure(streamConfiguration);
                sr.init();

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Got filestore for JdbcMailRepository: " + filestore);
                }
            }

            if (LOGGER.isDebugEnabled()) {
                String logBuf = this.getClass().getName() + " created according to " + destination;
                LOGGER.debug(logBuf);
            }
        } catch (Exception e) {
            final String message = "Failed to retrieve Store component:" + e.getMessage();
            LOGGER.error(message, e);
            throw new ConfigurationException(message, e);
        }

        theJDBCUtil = new JDBCUtil() {
            protected void delegatedLog(String logString) {
                JDBCMailRepository.this.LOGGER.warn("JDBCMailRepository: " + logString);
            }
        };

        // Test the connection to the database, by getting the DatabaseMetaData.
        Connection conn = datasource.getConnection();
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
            if (repositoryName != null) {
                sqlParameters.put("repository", repositoryName);
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
                    logBuffer = new StringBuffer(64).append("JdbcMailRepository: Created table '").append(tableName).append("'.");
                    LOGGER.info(logBuffer.toString());
                }
            }

            checkJdbcAttributesSupport(dbMetaData);

        } finally {
            theJDBCUtil.closeJDBCStatement(createStatement);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Checks whether support for JDBC Mail atributes is activated for this
     * repository and if everything is consistent.<br>
     * Looks for both the "updateMessageAttributesSQL" and
     * "retrieveMessageAttributesSQL" statements in sqlResources and for a table
     * column named "message_attributes".
     * 
     * @param dbMetaData
     *            the database metadata to be used to look up the column
     * @throws SQLException
     *             if a fatal situation is met
     */
    protected void checkJdbcAttributesSupport(DatabaseMetaData dbMetaData) throws SQLException {
        String attributesColumnName = "message_attributes";
        boolean hasUpdateMessageAttributesSQL = false;
        boolean hasRetrieveMessageAttributesSQL = false;

        boolean hasMessageAttributesColumn = theJDBCUtil.columnExists(dbMetaData, tableName, attributesColumnName);

        StringBuilder logBuffer = new StringBuilder(64).append("JdbcMailRepository '").append(repositoryName).append(", table '").append(tableName).append("': ");

        // Determine whether attributes are used and available for storing
        // Do we have updateMessageAttributesSQL?
        String updateMessageAttrSql = sqlQueries.getSqlString("updateMessageAttributesSQL", false);
        if (updateMessageAttrSql != null) {
            hasUpdateMessageAttributesSQL = true;
        }

        // Determine whether attributes are used and retrieve them
        // Do we have retrieveAttributesSQL?
        String retrieveMessageAttrSql = sqlQueries.getSqlString("retrieveMessageAttributesSQL", false);
        if (retrieveMessageAttrSql != null) {
            hasRetrieveMessageAttributesSQL = true;
        }

        if (hasUpdateMessageAttributesSQL && !hasRetrieveMessageAttributesSQL) {
            logBuffer.append("JDBC Mail Attributes support was activated for update but not for retrieval" + "(found 'updateMessageAttributesSQL' but not 'retrieveMessageAttributesSQL'" + "in table '").append(tableName).append("').");
            LOGGER.error(logBuffer.toString());
            throw new SQLException(logBuffer.toString());
        }
        if (!hasUpdateMessageAttributesSQL && hasRetrieveMessageAttributesSQL) {
            logBuffer.append("JDBC Mail Attributes support was activated for retrieval but not for update" + "(found 'retrieveMessageAttributesSQL' but not 'updateMessageAttributesSQL'" + "in table '").append(tableName).append("'.");
            LOGGER.error(logBuffer.toString());
            throw new SQLException(logBuffer.toString());
        }
        if (!hasMessageAttributesColumn && (hasUpdateMessageAttributesSQL || hasRetrieveMessageAttributesSQL)) {
            logBuffer.append("JDBC Mail Attributes support was activated but column '").append(attributesColumnName).append("' is missing in table '").append(tableName).append("'.");
            LOGGER.error(logBuffer.toString());
            throw new SQLException(logBuffer.toString());
        }
        if (hasUpdateMessageAttributesSQL && hasRetrieveMessageAttributesSQL) {
            jdbcMailAttributesReady = true;
            if (LOGGER.isInfoEnabled()) {
                logBuffer.append("JDBC Mail Attributes support ready.");
                LOGGER.info(logBuffer.toString());
            }
        } else {
            jdbcMailAttributesReady = false;
            logBuffer.append("JDBC Mail Attributes support not activated. " + "Missing both 'updateMessageAttributesSQL' " + "and 'retrieveMessageAttributesSQL' " + "statements for table '").append(tableName).append("' in sqlResources.xml. ").append("Will not persist in the repository '").append(repositoryName).append("'.");
            LOGGER.warn(logBuffer.toString());
        }
    }

    /**
     * @see org.apache.james.mailrepository.lib.AbstractMailRepository#internalStore(Mail)
     */
    protected void internalStore(Mail mc) throws IOException, MessagingException {
        Connection conn = null;
        try {
            conn = datasource.getConnection();
            // Need to determine whether need to insert this record, or update
            // it.

            // Determine whether the message body has changed, and possibly
            // avoid
            // updating the database.
            boolean saveBody;

            MimeMessage messageBody = mc.getMessage();
            // if the message is a CopyOnWrite proxy we check the modified
            // wrapped object.
            if (messageBody instanceof MimeMessageCopyOnWriteProxy) {
                MimeMessageCopyOnWriteProxy messageCow = (MimeMessageCopyOnWriteProxy) messageBody;
                messageBody = messageCow.getWrappedMessage();
            }
            if (messageBody instanceof MimeMessageWrapper) {
                MimeMessageWrapper message = (MimeMessageWrapper) messageBody;
                saveBody = message.isModified();
                if (saveBody) {
                    message.loadMessage();
                }
            } else {
                saveBody = true;
            }
            MessageInputStream is = new MessageInputStream(mc, sr, inMemorySizeLimit, true);

            // Begin a transaction
            conn.setAutoCommit(false);

            PreparedStatement checkMessageExists = null;
            ResultSet rsExists = null;
            boolean exists = false;
            try {
                checkMessageExists = conn.prepareStatement(sqlQueries.getSqlString("checkMessageExistsSQL", true));
                checkMessageExists.setString(1, mc.getName());
                checkMessageExists.setString(2, repositoryName);
                rsExists = checkMessageExists.executeQuery();
                exists = rsExists.next() && rsExists.getInt(1) > 0;
            } finally {
                theJDBCUtil.closeJDBCResultSet(rsExists);
                theJDBCUtil.closeJDBCStatement(checkMessageExists);
            }

            if (exists) {
                // MessageInputStream is = new
                // MessageInputStream(mc,sr,inMemorySizeLimit, true);

                // Update the existing record
                PreparedStatement updateMessage = null;

                try {
                    updateMessage = conn.prepareStatement(sqlQueries.getSqlString("updateMessageSQL", true));
                    updateMessage.setString(1, mc.getState());
                    updateMessage.setString(2, mc.getErrorMessage());
                    if (mc.getSender() == null) {
                        updateMessage.setNull(3, java.sql.Types.VARCHAR);
                    } else {
                        updateMessage.setString(3, mc.getSender().toString());
                    }
                    StringBuilder recipients = new StringBuilder();
                    for (Iterator<MailAddress> i = mc.getRecipients().iterator(); i.hasNext();) {
                        recipients.append(i.next().toString());
                        if (i.hasNext()) {
                            recipients.append("\r\n");
                        }
                    }
                    updateMessage.setString(4, recipients.toString());
                    updateMessage.setString(5, mc.getRemoteHost());
                    updateMessage.setString(6, mc.getRemoteAddr());
                    updateMessage.setTimestamp(7, new java.sql.Timestamp(mc.getLastUpdated().getTime()));
                    updateMessage.setString(8, mc.getName());
                    updateMessage.setString(9, repositoryName);
                    updateMessage.execute();
                } finally {
                    Statement localUpdateMessage = updateMessage;
                    // Clear reference to statement
                    updateMessage = null;
                    theJDBCUtil.closeJDBCStatement(localUpdateMessage);
                }

                // Determine whether attributes are used and available for
                // storing
                if (jdbcMailAttributesReady && mc.hasAttributes()) {
                    String updateMessageAttrSql = sqlQueries.getSqlString("updateMessageAttributesSQL", false);
                    PreparedStatement updateMessageAttr = null;
                    try {
                        updateMessageAttr = conn.prepareStatement(updateMessageAttrSql);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(baos);
                        try {
                            if (mc instanceof MailImpl) {
                                oos.writeObject(((MailImpl) mc).getAttributesRaw());
                            } else {
                                HashMap<String, Serializable> temp = new HashMap<>();
                                for (Iterator<String> i = mc.getAttributeNames(); i.hasNext();) {
                                    String hashKey = i.next();
                                    temp.put(hashKey, mc.getAttribute(hashKey));
                                }
                                oos.writeObject(temp);
                            }
                            oos.flush();
                            ByteArrayInputStream attrInputStream = new ByteArrayInputStream(baos.toByteArray());
                            updateMessageAttr.setBinaryStream(1, attrInputStream, baos.size());
                        } finally {
                            try {
                                if (oos != null) {
                                    oos.close();
                                }
                            } catch (IOException ioe) {
                                LOGGER.debug("JDBCMailRepository: Unexpected exception while closing output stream.", ioe);
                            }
                        }
                        updateMessageAttr.setString(2, mc.getName());
                        updateMessageAttr.setString(3, repositoryName);
                        updateMessageAttr.execute();
                    } catch (SQLException sqle) {
                        LOGGER.info("JDBCMailRepository: Trying to update mail attributes failed.", sqle);

                    } finally {
                        theJDBCUtil.closeJDBCStatement(updateMessageAttr);
                    }
                }

                if (saveBody) {

                    PreparedStatement updateMessageBody = conn.prepareStatement(sqlQueries.getSqlString("updateMessageBodySQL", true));
                    try {
                        updateMessageBody.setBinaryStream(1, is, (int) is.getSize());
                        updateMessageBody.setString(2, mc.getName());
                        updateMessageBody.setString(3, repositoryName);
                        updateMessageBody.execute();

                    } finally {
                        theJDBCUtil.closeJDBCStatement(updateMessageBody);
                    }
                }

            } else {
                // Insert the record into the database
                PreparedStatement insertMessage = null;
                try {
                    String insertMessageSQL = sqlQueries.getSqlString("insertMessageSQL", true);
                    int number_of_parameters = getNumberOfParameters(insertMessageSQL);
                    insertMessage = conn.prepareStatement(insertMessageSQL);
                    insertMessage.setString(1, mc.getName());
                    insertMessage.setString(2, repositoryName);
                    insertMessage.setString(3, mc.getState());
                    insertMessage.setString(4, mc.getErrorMessage());
                    if (mc.getSender() == null) {
                        insertMessage.setNull(5, java.sql.Types.VARCHAR);
                    } else {
                        insertMessage.setString(5, mc.getSender().toString());
                    }
                    StringBuilder recipients = new StringBuilder();
                    for (Iterator<MailAddress> i = mc.getRecipients().iterator(); i.hasNext();) {
                        recipients.append(i.next().toString());
                        if (i.hasNext()) {
                            recipients.append("\r\n");
                        }
                    }
                    insertMessage.setString(6, recipients.toString());
                    insertMessage.setString(7, mc.getRemoteHost());
                    insertMessage.setString(8, mc.getRemoteAddr());
                    insertMessage.setTimestamp(9, new java.sql.Timestamp(mc.getLastUpdated().getTime()));

                    insertMessage.setBinaryStream(10, is, (int) is.getSize());

                    // Store attributes
                    if (number_of_parameters > 10) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(baos);
                        try {
                            if (mc instanceof MailImpl) {
                                oos.writeObject(((MailImpl) mc).getAttributesRaw());
                            } else {
                                HashMap<String, Serializable> temp = new HashMap<>();
                                for (Iterator<String> i = mc.getAttributeNames(); i.hasNext();) {
                                    String hashKey = i.next();
                                    temp.put(hashKey, mc.getAttribute(hashKey));
                                }
                                oos.writeObject(temp);
                            }
                            oos.flush();
                            ByteArrayInputStream attrInputStream = new ByteArrayInputStream(baos.toByteArray());
                            insertMessage.setBinaryStream(11, attrInputStream, baos.size());
                        } finally {
                            try {
                                if (oos != null) {
                                    oos.close();
                                }
                            } catch (IOException ioe) {
                                LOGGER.debug("JDBCMailRepository: Unexpected exception while closing output stream.", ioe);
                            }
                        }
                    }

                    insertMessage.execute();
                } finally {
                    theJDBCUtil.closeJDBCStatement(insertMessage);
                }
            }

            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            LOGGER.debug("Failed to store internal mail", e);
            throw new IOException(e.getMessage());
        } finally {
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * @see org.apache.james.mailrepository.api.MailRepository#retrieve(String)
     */
    @SuppressWarnings("unchecked")
    public Mail retrieve(String key) throws MessagingException {
        if (DEEP_DEBUG) {
            System.err.println("retrieving " + key);
        }
        Connection conn = null;
        PreparedStatement retrieveMessage = null;
        ResultSet rsMessage = null;
        try {
            conn = datasource.getConnection();
            if (DEEP_DEBUG) {
                System.err.println("got a conn " + key);
            }

            retrieveMessage = conn.prepareStatement(sqlQueries.getSqlString("retrieveMessageSQL", true));
            retrieveMessage.setString(1, key);
            retrieveMessage.setString(2, repositoryName);
            rsMessage = retrieveMessage.executeQuery();
            if (DEEP_DEBUG) {
                System.err.println("ran the query " + key);
            }
            if (!rsMessage.next()) {
                if (LOGGER.isDebugEnabled()) {
                    String debugBuffer = "Did not find a record " + key + " in " + repositoryName;
                    LOGGER.debug(debugBuffer);
                }
                return null;
            }
            // Determine whether attributes are used and retrieve them
            PreparedStatement retrieveMessageAttr = null;
            HashMap<String, Object> attributes = null;
            if (jdbcMailAttributesReady) {
                String retrieveMessageAttrSql = sqlQueries.getSqlString("retrieveMessageAttributesSQL", false);
                ResultSet rsMessageAttr = null;
                try {
                    retrieveMessageAttr = conn.prepareStatement(retrieveMessageAttrSql);

                    retrieveMessageAttr.setString(1, key);
                    retrieveMessageAttr.setString(2, repositoryName);
                    rsMessageAttr = retrieveMessageAttr.executeQuery();

                    if (rsMessageAttr.next()) {
                        try {
                            byte[] serialized_attr;
                            String getAttributesOption = sqlQueries.getDbOption("getAttributes");
                            if (getAttributesOption != null && (getAttributesOption.equalsIgnoreCase("useBlob") || getAttributesOption.equalsIgnoreCase("useBinaryStream"))) {
                                Blob b = rsMessageAttr.getBlob(1);
                                serialized_attr = b.getBytes(1, (int) b.length());
                            } else {
                                serialized_attr = rsMessageAttr.getBytes(1);
                            }
                            // this check is for better backwards compatibility
                            if (serialized_attr != null) {
                                ByteArrayInputStream bais = new ByteArrayInputStream(serialized_attr);
                                ObjectInputStream ois = new ObjectInputStream(bais);
                                attributes = (HashMap<String, Object>) ois.readObject();
                                ois.close();
                            }
                        } catch (IOException ioe) {
                            if (LOGGER.isDebugEnabled()) {
                                String debugBuffer = "Exception reading attributes " + key + " in " + repositoryName;
                                LOGGER.debug(debugBuffer, ioe);
                            }
                        }
                    } else {
                        if (LOGGER.isDebugEnabled()) {
                            String debugBuffer = "Did not find a record (attributes) " + key + " in " + repositoryName;
                            LOGGER.debug(debugBuffer);
                        }
                    }
                } catch (SQLException sqle) {
                    String errorBuffer = "Error retrieving message" + sqle.getMessage() + sqle.getErrorCode() + sqle.getSQLState() + sqle.getNextException();
                    LOGGER.error(errorBuffer);
                } finally {
                    theJDBCUtil.closeJDBCResultSet(rsMessageAttr);
                    theJDBCUtil.closeJDBCStatement(retrieveMessageAttr);
                }
            }

            MailImpl mc = new MailImpl();
            mc.setAttributesRaw(attributes);
            mc.setName(key);
            mc.setState(rsMessage.getString(1));
            mc.setErrorMessage(rsMessage.getString(2));
            String sender = rsMessage.getString(3);
            if (sender == null) {
                mc.setSender(null);
            } else {
                mc.setSender(new MailAddress(sender));
            }
            StringTokenizer st = new StringTokenizer(rsMessage.getString(4), "\r\n", false);
            Set<MailAddress> recipients = new HashSet<>();
            while (st.hasMoreTokens()) {
                recipients.add(new MailAddress(st.nextToken()));
            }
            mc.setRecipients(recipients);
            mc.setRemoteHost(rsMessage.getString(5));
            mc.setRemoteAddr(rsMessage.getString(6));
            mc.setLastUpdated(rsMessage.getTimestamp(7));

            MimeMessageJDBCSource source = new MimeMessageJDBCSource(this, key, sr);
            MimeMessageCopyOnWriteProxy message = new MimeMessageCopyOnWriteProxy(source);
            mc.setMessage(message);
            return mc;
        } catch (SQLException sqle) {
            String errorBuffer = "Error retrieving message" + sqle.getMessage() + sqle.getErrorCode() + sqle.getSQLState() + sqle.getNextException();
            LOGGER.error(errorBuffer);
            LOGGER.debug("Failed to retrieve mail", sqle);
            throw new MessagingException("Exception while retrieving mail: " + sqle.getMessage(), sqle);
        } catch (Exception me) {
            throw new MessagingException("Exception while retrieving mail: " + me.getMessage(), me);
        } finally {
            theJDBCUtil.closeJDBCResultSet(rsMessage);
            theJDBCUtil.closeJDBCStatement(retrieveMessage);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * @see org.apache.james.mailrepository.lib.AbstractMailRepository#internalRemove(String)
     */
    protected void internalRemove(String key) throws MessagingException {
        Connection conn = null;
        PreparedStatement removeMessage = null;
        try {
            conn = datasource.getConnection();
            removeMessage = conn.prepareStatement(sqlQueries.getSqlString("removeMessageSQL", true));
            removeMessage.setString(1, key);
            removeMessage.setString(2, repositoryName);
            removeMessage.execute();

            if (sr != null) {
                sr.remove(key);
            }
        } catch (Exception me) {
            throw new MessagingException("Exception while removing mail: " + me.getMessage(), me);
        } finally {
            theJDBCUtil.closeJDBCStatement(removeMessage);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * @see org.apache.james.mailrepository.api.MailRepository#list()
     */
    public Iterator<String> list() throws MessagingException {
        // System.err.println("listing messages");
        Connection conn = null;
        PreparedStatement listMessages = null;
        ResultSet rsListMessages = null;
        try {
            conn = datasource.getConnection();
            listMessages = conn.prepareStatement(sqlQueries.getSqlString("listMessagesSQL", true));
            listMessages.setString(1, repositoryName);
            rsListMessages = listMessages.executeQuery();

            List<String> messageList = new ArrayList<>();
            while (rsListMessages.next() && !Thread.currentThread().isInterrupted()) {
                messageList.add(rsListMessages.getString(1));
            }
            return messageList.iterator();
        } catch (Exception me) {
            throw new MessagingException("Exception while listing mail: " + me.getMessage(), me);
        } finally {
            theJDBCUtil.closeJDBCResultSet(rsListMessages);
            theJDBCUtil.closeJDBCStatement(listMessages);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Gets the SQL connection to be used by this JDBCMailRepository
     * 
     * @return the connection
     * @throws SQLException
     *             if there is an issue with getting the connection
     */
    protected Connection getConnection() throws SQLException {
        return datasource.getConnection();
    }

    /**
     * @see java.lang.Object#equals(Object)
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof JDBCMailRepository)) {
            return false;
        }
        // TODO: Figure out whether other instance variables should be part of
        // the equals equation
        JDBCMailRepository repository = (JDBCMailRepository) obj;
        return ((repository.tableName.equals(tableName)) || ((repository.tableName != null) && repository.tableName.equals(tableName))) && ((repository.repositoryName.equals(repositoryName)) || ((repository.repositoryName != null) && repository.repositoryName.equals(repositoryName)));
    }

    /**
     * Provide a hash code that is consistent with equals for this class
     * 
     * @return the hash code
     */
    public int hashCode() {
        int result = 17;
        if (tableName != null) {
            result = 37 * tableName.hashCode();
        }
        if (repositoryName != null) {
            result = 37 * repositoryName.hashCode();
        }
        return result;
    }

    /**
     * This method calculates number of parameters in a prepared statement SQL
     * String. It does so by counting the number of '?' in the string
     * 
     * @param sqlstring
     *            to return parameter count for
     * @return number of parameters
     **/
    private int getNumberOfParameters(String sqlstring) {
        // it is alas a java 1.4 feature to be able to call
        // getParameterMetaData which could provide us with the parameterCount
        char[] chars = sqlstring.toCharArray();
        int count = 0;
        for (char aChar : chars) {
            count += aChar == '?' ? 1 : 0;
        }
        return count;
    }
}
