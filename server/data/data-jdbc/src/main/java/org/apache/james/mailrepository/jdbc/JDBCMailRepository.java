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
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.sql.DataSource;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.lib.AbstractMailRepository;
import org.apache.james.repository.file.FilePersistentStreamRepository;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.james.util.sql.JDBCUtil;
import org.apache.james.util.sql.SqlResources;
import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

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

    @Override
    protected void doConfigure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        super.doConfigure(configuration);
        LOGGER.debug("{}.configure()", getClass().getName());
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

        LOGGER.debug("Parsed URL: table = '{}', repositoryName = '{}'", tableName, repositoryName);

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
        LOGGER.debug("{}.initialize()", getClass().getName());

        try {
            if (filestore != null) {

                // prepare Configurations for stream repositories
                HierarchicalConfiguration<ImmutableNode> streamConfiguration = new BaseHierarchicalConfiguration();

                streamConfiguration.addProperty("[@destinationURL]", filestore);

                sr = new FilePersistentStreamRepository();
                sr.setFileSystem(fileSystem);
                sr.configure(streamConfiguration);
                sr.init();

                LOGGER.debug("Got filestore for JdbcMailRepository: {}", filestore);
            }

            LOGGER.debug("{} created according to {}", getClass().getName(), destination);
        } catch (Exception e) {
            LOGGER.error("Failed to retrieve Store component", e);
            throw new ConfigurationException("Failed to retrieve Store component", e);
        }

        theJDBCUtil = new JDBCUtil();

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

            LOGGER.debug("Reading SQL resources from file: {}, section {}.", sqlFileName, getClass().getName());

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

                LOGGER.info("JdbcMailRepository: Created table '{}'.", tableName);
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
            String logBufferAsString = logBuffer.toString();
            LOGGER.error(logBufferAsString);
            throw new SQLException(logBufferAsString);
        }
        if (!hasUpdateMessageAttributesSQL && hasRetrieveMessageAttributesSQL) {
            logBuffer.append("JDBC Mail Attributes support was activated for retrieval but not for update" + "(found 'retrieveMessageAttributesSQL' but not 'updateMessageAttributesSQL'" + "in table '").append(tableName).append("'.");
            String logBufferAsString = logBuffer.toString();
            LOGGER.error(logBufferAsString);
            throw new SQLException(logBufferAsString);
        }
        if (!hasMessageAttributesColumn && (hasUpdateMessageAttributesSQL || hasRetrieveMessageAttributesSQL)) {
            logBuffer.append("JDBC Mail Attributes support was activated but column '").append(attributesColumnName).append("' is missing in table '").append(tableName).append("'.");
            String logBufferAsString = logBuffer.toString();
            LOGGER.error(logBufferAsString);
            throw new SQLException(logBufferAsString);
        }
        if (hasUpdateMessageAttributesSQL && hasRetrieveMessageAttributesSQL) {
            jdbcMailAttributesReady = true;
            logBuffer.append("JDBC Mail Attributes support ready.");
            LOGGER.info("{}", logBuffer);
        } else {
            jdbcMailAttributesReady = false;
            logBuffer.append("JDBC Mail Attributes support not activated. " + "Missing both 'updateMessageAttributesSQL' " + "and 'retrieveMessageAttributesSQL' " + "statements for table '").append(tableName).append("' in sqlResources.xml. ").append("Will not persist in the repository '").append(repositoryName).append("'.");
            LOGGER.warn("{}", logBuffer);
        }
    }

    @Override
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
                    if (mc.getMaybeSender().isNullSender()) {
                        updateMessage.setNull(3, java.sql.Types.VARCHAR);
                    } else {
                        updateMessage.setString(3, mc.getMaybeSender().get().toString());
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
                                Map<String, Serializable> temp = mc.attributes()
                                    .collect(Guavate.toImmutableMap(
                                            attribute -> attribute.getName().asString(),
                                            attribute -> (Serializable) attribute.getValue().value()
                                    ));

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
                    insertMessage = conn.prepareStatement(insertMessageSQL);
                    int numberOfParameters = insertMessage.getParameterMetaData().getParameterCount();
                    insertMessage.setString(1, mc.getName());
                    insertMessage.setString(2, repositoryName);
                    insertMessage.setString(3, mc.getState());
                    insertMessage.setString(4, mc.getErrorMessage());
                    if (mc.getMaybeSender().isNullSender()) {
                        insertMessage.setNull(5, Types.VARCHAR);
                    } else {
                        insertMessage.setString(5, mc.getMaybeSender().get().toString());
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
                    if (mc.getPerRecipientSpecificHeaders().getHeadersByRecipient().isEmpty()) {
                        insertMessage.setObject(9, null);
                    } else {
                        byte[] bytes = SerializationUtils.serialize(mc.getPerRecipientSpecificHeaders());
                        insertMessage.setBinaryStream(9, new ByteArrayInputStream(bytes), bytes.length);
                    }
                    insertMessage.setTimestamp(10, new java.sql.Timestamp(mc.getLastUpdated().getTime()));

                    insertMessage.setBinaryStream(11, is, (int) is.getSize());

                    // Store attributes
                    if (numberOfParameters > 11) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(baos);
                        try {
                            if (mc instanceof MailImpl) {
                                oos.writeObject(((MailImpl) mc).getAttributesRaw());
                            } else {
                                Map<String, Serializable> temp = mc.attributes()
                                        .collect(Guavate.toImmutableMap(
                                                attribute -> attribute.getName().asString(),
                                                attribute -> (Serializable) attribute.getValue().value()
                                        ));

                                oos.writeObject(temp);
                            }
                            oos.flush();
                            ByteArrayInputStream attrInputStream = new ByteArrayInputStream(baos.toByteArray());
                            insertMessage.setBinaryStream(12, attrInputStream, baos.size());
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

    @Override
    @SuppressWarnings("unchecked")
    public Mail retrieve(MailKey key) throws MessagingException {
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
            retrieveMessage.setString(1, key.asString());
            retrieveMessage.setString(2, repositoryName);
            rsMessage = retrieveMessage.executeQuery();
            if (DEEP_DEBUG) {
                System.err.println("ran the query " + key);
            }
            if (!rsMessage.next()) {
                LOGGER.debug("Did not find a record {} in {}", key, repositoryName);
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

                    retrieveMessageAttr.setString(1, key.asString());
                    retrieveMessageAttr.setString(2, repositoryName);
                    rsMessageAttr = retrieveMessageAttr.executeQuery();

                    if (rsMessageAttr.next()) {
                        try {
                            byte[] serializedAttr;
                            String getAttributesOption = sqlQueries.getDbOption("getAttributes");
                            if (getAttributesOption != null && (getAttributesOption.equalsIgnoreCase("useBlob") || getAttributesOption.equalsIgnoreCase("useBinaryStream"))) {
                                Blob b = rsMessageAttr.getBlob(1);
                                serializedAttr = b.getBytes(1, (int) b.length());
                            } else {
                                serializedAttr = rsMessageAttr.getBytes(1);
                            }
                            // this check is for better backwards compatibility
                            if (serializedAttr != null) {
                                ByteArrayInputStream bais = new ByteArrayInputStream(serializedAttr);
                                ObjectInputStream ois = new ObjectInputStream(bais);
                                attributes = (HashMap<String, Object>) ois.readObject();
                                ois.close();
                            }
                        } catch (IOException ioe) {
                            LOGGER.debug("Exception reading attributes {} in {}", key, repositoryName, ioe);
                        }
                    } else {
                        LOGGER.debug("Did not find a record (attributes) {} in {}", key, repositoryName);
                    }
                } catch (SQLException sqle) {
                    LOGGER.error("Error retrieving message{}{}{}{}", sqle.getMessage(), sqle.getErrorCode(), sqle.getSQLState(), String.valueOf(sqle.getNextException()));
                } finally {
                    theJDBCUtil.closeJDBCResultSet(rsMessageAttr);
                    theJDBCUtil.closeJDBCStatement(retrieveMessageAttr);
                }
            }

            MailImpl.Builder mc = MailImpl.builder().name(key.asString());
            mc.addAttributes(toAttributes(attributes));
            mc.state(rsMessage.getString(1));
            mc.errorMessage(rsMessage.getString(2));
            String sender = rsMessage.getString(3);
            if (sender == null) {
                mc.sender((MailAddress)null);
            } else {
                mc.sender(new MailAddress(sender));
            }
            StringTokenizer st = new StringTokenizer(rsMessage.getString(4), "\r\n", false);
            while (st.hasMoreTokens()) {
                mc.addRecipient(st.nextToken());
            }
            mc.remoteHost(rsMessage.getString(5));
            mc.remoteAddr(rsMessage.getString(6));
            try (InputStream is = rsMessage.getBinaryStream(7)) {
                if (is != null) {
                    mc.addAllHeadersForRecipients(SerializationUtils.deserialize(is));
                }
            }

            mc.lastUpdated(rsMessage.getTimestamp(8));

            MimeMessageJDBCSource source = new MimeMessageJDBCSource(this, key.asString(), sr);
            MimeMessageCopyOnWriteProxy message = new MimeMessageCopyOnWriteProxy(source);
            mc.mimeMessage(message);
            return mc.build();
        } catch (SQLException sqle) {
            LOGGER.error("Error retrieving message{}{}{}{}", sqle.getMessage(), sqle.getErrorCode(), sqle.getSQLState(), String.valueOf(sqle.getNextException()));
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

    private ImmutableList<Attribute> toAttributes(HashMap<String, Object> attributes) {
        return Optional.ofNullable(attributes)
            .orElse(new HashMap<>())
            .entrySet()
            .stream()
            .map(entry -> Attribute.convertToAttribute(entry.getKey(), entry.getValue()))
            .collect(Guavate.toImmutableList());
    }

    @Override
    protected void internalRemove(MailKey key) throws MessagingException {
        Connection conn = null;
        PreparedStatement removeMessage = null;
        try {
            conn = datasource.getConnection();
            removeMessage = conn.prepareStatement(sqlQueries.getSqlString("removeMessageSQL", true));
            removeMessage.setString(1, key.asString());
            removeMessage.setString(2, repositoryName);
            removeMessage.execute();

            if (sr != null) {
                sr.remove(key.asString());
            }
        } catch (Exception me) {
            throw new MessagingException("Exception while removing mail: " + me.getMessage(), me);
        } finally {
            theJDBCUtil.closeJDBCStatement(removeMessage);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    @Override
    public long size() throws MessagingException {
        try (Connection conn = datasource.getConnection();
             PreparedStatement count = conn.prepareStatement(sqlQueries.getSqlString("countMessagesSQL", true));
             ResultSet resultSet = count.executeQuery()) {

            return resultSet.next() ? resultSet.getLong(1) : 0;
        } catch (Exception e) {
            throw new MessagingException("Exception while fetching size: " + e.getMessage(), e);
        }
    }

    @Override
    public Iterator<MailKey> list() throws MessagingException {
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
            return messageList.stream()
                .map(MailKey::new)
                .iterator();
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

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JDBCMailRepository)) {
            return false;
        }
        // TODO: Figure out whether other instance variables should be part of
        // the equals equation
        JDBCMailRepository repository = (JDBCMailRepository) obj;
        return ((repository.tableName.equals(tableName)) || ((repository.tableName != null) && repository.tableName.equals(tableName))) && ((repository.repositoryName.equals(repositoryName)) || ((repository.repositoryName != null) && repository.repositoryName.equals(repositoryName)));
    }

    @Override
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
}
