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

package org.apache.james.transport.mailets;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.inject.Inject;
import javax.sql.DataSource;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.sql.JDBCUtil;
import org.apache.james.util.sql.SqlResources;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

/**
 * <p>
 * Manages for each local user a "white list" of remote addresses whose messages
 * should never be blocked as spam.
 * </p>
 * <p>
 * The normal behaviour is to check, for a local sender, if a remote recipient
 * is already in the list: if not, it will be automatically inserted. This is
 * under the interpretation that if a local sender <i>X</i> sends a message to a
 * remote recipient <i>Y</i>, then later on if a message is sent by <i>Y</i> to
 * <i>X</i> it should be considered always valid and never blocked; hence
 * <i>Y</i> should be in the white list of <i>X</i>.
 * </p>
 * <p>
 * Another mode of operations is when a local sender sends a message to
 * <i>whitelistManagerAddress</i> with one of three specific values in the
 * subject, to (i) send back a message displaying a list of the addresses in his
 * own list; (ii) insert some new addresses in his own list; (iii) remove some
 * addresses from his own list. In all this cases the message will be ghosted
 * and the postmaster will reply to the sender.
 * </p>
 * <p>
 * The sender name is always converted to its primary name (handling aliases).
 * </p>
 * <p>
 * Sample configuration:
 * </p>
 * 
 * <pre>
 * <code>
 * &lt;mailet match="SMTPAuthSuccessful" class="WhiteListManager"&gt;
 *   &lt;repositoryPath&gt; db://maildb &lt;/repositoryPath&gt;
 *   &lt;!--
 *     If true automatically inserts the local sender to remote recipients entries in the whitelist (default is false).
 *   --&gt;
 *   &lt;automaticInsert&gt;true&lt;/automaticInsert&gt;
 *   &lt;!--
 *     Set this to an email address of the "whitelist manager" to send commands to (default is null).
 *   --&gt;
 *   &lt;whitelistManagerAddress&gt;whitelist.manager@xxx.yyy&lt;/whitelistManagerAddress&gt;
 *   &lt;!--
 *     Set this to a unique text that you can use (by sending a message to the "whitelist manager" above)
 *     to tell the mailet to send back the contents of the white list (default is null).
 *   --&gt;
 *   &lt;displayFlag&gt;display whitelist&lt;/displayFlag&gt;
 *   &lt;!--
 *     Set this to a unique text that you can use (by sending a message to the "whitelist manager" above)
 *     to tell the mailet to insert some new remote recipients to the white list (default is null).
 *   --&gt;
 *   &lt;insertFlag&gt;insert whitelist&lt;/insertFlag&gt;
 *   &lt;!--
 *     Set this to a unique text that you can use (by sending a message to the "whitelist manager" above)
 *     to tell the mailet to remove some remote recipients from the white list (default is null).
 *   --&gt;
 *   &lt;removeFlag&gt;remove whitelist&lt;/removeFlag&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * 
 * @see org.apache.james.transport.matchers.IsInWhiteList
 * @since 2.3.0
 */
@Experimental
@SuppressWarnings("deprecation")
public class WhiteListManager extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhiteListManager.class);

    private boolean automaticInsert;
    private String displayFlag;
    private String insertFlag;
    private String removeFlag;
    private MailAddress whitelistManagerAddress;

    private String selectByPK;
    private String selectBySender;
    private String insert;
    private String deleteByPK;

    private DataSource datasource;

    /**
     * The user repository for this mail server. Contains all the users with
     * inboxes on this server.
     */
    private UsersRepository localusers;

    /**
     * The JDBCUtil helper class
     */
    private final JDBCUtil theJDBCUtil = new JDBCUtil();

    /**
     * Contains all of the sql strings for this component.
     */
    private final SqlResources sqlQueries = new SqlResources();

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

    /**
     * Getter for property sqlParameters.
     * 
     * @return Value of property sqlParameters.
     */
    private Map<String, String> getSqlParameters() {

        return this.sqlParameters;
    }

    @Override
    public void init() throws MessagingException {
        automaticInsert = Boolean.parseBoolean(getInitParameter("automaticInsert"));
        LOGGER.debug("automaticInsert: {}", automaticInsert);

        displayFlag = getInitParameter("displayFlag");
        insertFlag = getInitParameter("insertFlag");
        removeFlag = getInitParameter("removeFlag");

        String whitelistManagerAddressString = getInitParameter("whitelistManagerAddress");
        if (whitelistManagerAddressString != null) {
            whitelistManagerAddressString = whitelistManagerAddressString.trim();
            LOGGER.debug("whitelistManagerAddress: {}", whitelistManagerAddressString);
            try {
                whitelistManagerAddress = new MailAddress(whitelistManagerAddressString);
            } catch (jakarta.mail.internet.ParseException pe) {
                throw new MessagingException("Bad whitelistManagerAddress", pe);
            }

            if (displayFlag != null) {
                displayFlag = displayFlag.trim();
                LOGGER.debug("displayFlag: {}", displayFlag);
            } else {
                LOGGER.debug("displayFlag is null");
            }
            if (insertFlag != null) {
                insertFlag = insertFlag.trim();
                LOGGER.debug("insertFlag: {}", insertFlag);
            } else {
                LOGGER.debug("insertFlag is null");
            }
            if (removeFlag != null) {
                removeFlag = removeFlag.trim();
                LOGGER.debug("removeFlag: {}", removeFlag);
            } else {
                LOGGER.debug("removeFlag is null");
            }
        } else {
            LOGGER.debug("whitelistManagerAddress is null; will ignore commands");
        }

        String repositoryPath = getInitParameter("repositoryPath");
        if (repositoryPath != null) {
            LOGGER.debug("repositoryPath: {}", repositoryPath);
        } else {
            throw new MessagingException("repositoryPath is null");
        }

        try {
            initSqlQueries(datasource.getConnection(), getMailetContext());
        } catch (Exception e) {
            throw new MessagingException("Exception initializing queries", e);
        }

        selectByPK = sqlQueries.getSqlString("selectByPK", true);
        selectBySender = sqlQueries.getSqlString("selectBySender", true);
        insert = sqlQueries.getSqlString("insert", true);
        deleteByPK = sqlQueries.getSqlString("deleteByPK", true);
    }

    @Override
    public void service(Mail mail) throws MessagingException {

        // check if it's a local sender
        if (!mail.hasSender()) {
            return;
        }
        MailAddress senderMailAddress = mail.getMaybeSender().get();
        if (!getMailetContext().isLocalEmail(senderMailAddress)) {
            // not a local sender, so return
            return;
        }

        Collection<MailAddress> recipients = mail.getRecipients();

        if (recipients.size() == 1 && whitelistManagerAddress != null && whitelistManagerAddress.equals(recipients.toArray()[0])) {

            mail.setState(Mail.GHOST);

            String subject = mail.getMessage().getSubject();
            if (displayFlag != null && displayFlag.equals(subject)) {
                manageDisplayRequest(mail);
            } else if (insertFlag != null && insertFlag.equals(subject)) {
                manageInsertRequest(mail);
            } else if (removeFlag != null && removeFlag.equals(subject)) {
                manageRemoveRequest(mail);
            } else {
                StringWriter sout = new StringWriter();
                PrintWriter out = new PrintWriter(sout, true);
                out.println("Answering on behalf of: " + whitelistManagerAddress);
                out.println("ERROR: Unknown command in the subject line: " + subject);
                sendReplyFromPostmaster(mail, sout.toString());
            }
            return;
        }

        if (automaticInsert) {
            checkAndInsert(senderMailAddress, recipients);
        }

    }

    @Override
    public String getMailetInfo() {
        return "White List Manager mailet";
    }

    /**
     * Loops through each address in the recipient list, checks if in the
     * senders list and inserts in it otherwise.
     */
    private void checkAndInsert(MailAddress senderMailAddress, Collection<MailAddress> recipients) throws MessagingException {
        String senderUser = senderMailAddress.getLocalPart().toLowerCase(Locale.US);
        Domain senderHost = senderMailAddress.getDomain();

        Connection conn = null;
        PreparedStatement selectStmt = null;
        PreparedStatement insertStmt = null;
        boolean dbUpdated = false;

        try {

            for (MailAddress recipient : recipients) {
                ResultSet selectRS = null;
                try {
                    String recipientUser = recipient.getLocalPart().toLowerCase(Locale.US);
                    Domain recipientHost = recipient.getDomain();

                    if (getMailetContext().isLocalServer(recipientHost)) {
                        // not a remote recipient, so skip
                        continue;
                    }

                    if (conn == null) {
                        conn = datasource.getConnection();
                    }

                    if (selectStmt == null) {
                        selectStmt = conn.prepareStatement(selectByPK);
                    }
                    selectStmt.setString(1, senderUser);
                    selectStmt.setString(2, senderHost.asString());
                    selectStmt.setString(3, recipientUser);
                    selectStmt.setString(4, recipientHost.asString());
                    selectRS = selectStmt.executeQuery();
                    if (selectRS.next()) {
                        // This address was already in the list
                        continue;
                    }

                    if (insertStmt == null) {
                        insertStmt = conn.prepareStatement(insert);
                    }
                    insertStmt.setString(1, senderUser);
                    insertStmt.setString(2, senderHost.asString());
                    insertStmt.setString(3, recipientUser);
                    insertStmt.setString(4, recipientHost.asString());
                    insertStmt.executeUpdate();
                    dbUpdated = true;

                } finally {
                    theJDBCUtil.closeJDBCResultSet(selectRS);
                }

                // Commit our changes if necessary.
                if (conn != null && dbUpdated && !conn.getAutoCommit()) {
                    conn.commit();
                    dbUpdated = false;
                }
            }
        } catch (SQLException sqle) {
            LOGGER.error("Error accessing database", sqle);
            throw new MessagingException("Exception thrown", sqle);
        } finally {
            theJDBCUtil.closeJDBCStatement(selectStmt);
            theJDBCUtil.closeJDBCStatement(insertStmt);
            // Rollback our changes if necessary.
            try {
                if (conn != null && dbUpdated && !conn.getAutoCommit()) {
                    conn.rollback();
                    dbUpdated = false;
                }
            } catch (Exception e) {
                LOGGER.error("Ignored exception upon rollback", e);
            }
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Manages a display request.
     */
    private void manageDisplayRequest(Mail mail) throws MessagingException {
        MailAddress senderMailAddress = mail.getMaybeSender().get();
        String senderUser = senderMailAddress.getLocalPart().toLowerCase(Locale.US);
        Domain senderHost = senderMailAddress.getDomain();

        Connection conn = null;
        PreparedStatement selectStmt = null;
        ResultSet selectRS = null;

        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);

        try {
            out.println("Answering on behalf of: " + whitelistManagerAddress);
            out.println("Displaying white list of " + (new MailAddress(senderUser, senderHost)) + ":");
            out.println();

            conn = datasource.getConnection();
            selectStmt = conn.prepareStatement(selectBySender);
            selectStmt.setString(1, senderUser);
            selectStmt.setString(2, senderHost.asString());
            selectRS = selectStmt.executeQuery();
            while (selectRS.next()) {
                MailAddress mailAddress = new MailAddress(selectRS.getString(1), selectRS.getString(2));
                out.println(mailAddress.toString());
            }

            out.println();
            out.println("Finished");

            sendReplyFromPostmaster(mail, sout.toString());

        } catch (SQLException sqle) {
            out.println("Error accessing the database");
            sendReplyFromPostmaster(mail, sout.toString());
            throw new MessagingException("Error accessing database", sqle);
        } finally {
            theJDBCUtil.closeJDBCResultSet(selectRS);
            theJDBCUtil.closeJDBCStatement(selectStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Manages an insert request.
     */
    private void manageInsertRequest(Mail mail) throws MessagingException {
        MailAddress senderMailAddress = mail.getMaybeSender().get();
        String senderUser = senderMailAddress.getLocalPart().toLowerCase(Locale.US);
        Domain senderHost = senderMailAddress.getDomain();

        Connection conn = null;
        PreparedStatement selectStmt = null;
        PreparedStatement insertStmt = null;
        boolean dbUpdated = false;

        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);

        try {
            out.println("Answering on behalf of: " + whitelistManagerAddress);
            out.println("Inserting in the white list of " + (new MailAddress(senderUser, senderHost)) + " ...");
            out.println();

            MimeMessage message = mail.getMessage();

            Object content = message.getContent();

            if (message.getContentType().startsWith("text/plain") && content instanceof String) {
                StringTokenizer st = new StringTokenizer((String) content, " \t\n\r\f,;:<>");
                while (st.hasMoreTokens()) {
                    ResultSet selectRS = null;
                    try {
                        MailAddress recipientMailAddress;
                        try {
                            recipientMailAddress = new MailAddress(st.nextToken());
                        } catch (jakarta.mail.internet.ParseException pe) {
                            continue;
                        }
                        String recipientUser = recipientMailAddress.getLocalPart().toLowerCase(Locale.US);
                        Domain recipientHost = recipientMailAddress.getDomain();

                        if (getMailetContext().isLocalServer(recipientHost)) {
                            // not a remote recipient, so skip
                            continue;
                        }

                        if (conn == null) {
                            conn = datasource.getConnection();
                        }

                        if (selectStmt == null) {
                            selectStmt = conn.prepareStatement(selectByPK);
                        }
                        selectStmt.setString(1, senderUser);
                        selectStmt.setString(2, senderHost.asString());
                        selectStmt.setString(3, recipientUser);
                        selectStmt.setString(4, recipientHost.asString());
                        selectRS = selectStmt.executeQuery();
                        if (selectRS.next()) {
                            // This address was already in the list
                            out.println("Skipped:  " + recipientMailAddress);
                            continue;
                        }

                        if (insertStmt == null) {
                            insertStmt = conn.prepareStatement(insert);
                        }
                        insertStmt.setString(1, senderUser);
                        insertStmt.setString(2, senderHost.asString());
                        insertStmt.setString(3, recipientUser);
                        insertStmt.setString(4, recipientHost.asString());
                        insertStmt.executeUpdate();
                        dbUpdated = true;
                        out.println("Inserted: " + recipientMailAddress);

                    } finally {
                        theJDBCUtil.closeJDBCResultSet(selectRS);
                    }
                }

                if (dbUpdated) {
                    LOGGER.debug("Insertion request issued by {}", senderMailAddress);
                }
                // Commit our changes if necessary.
                if (conn != null && dbUpdated && !conn.getAutoCommit()) {
                    conn.commit();
                    dbUpdated = false;
                }
            } else {
                out.println("The message must be plain - no action");
            }

            out.println();
            out.println("Finished");

            sendReplyFromPostmaster(mail, sout.toString());

        } catch (SQLException sqle) {
            out.println("Error accessing the database");
            sendReplyFromPostmaster(mail, sout.toString());
            throw new MessagingException("Error accessing the database", sqle);
        } catch (IOException ioe) {
            out.println("Error getting message content");
            sendReplyFromPostmaster(mail, sout.toString());
            throw new MessagingException("Error getting message content", ioe);
        } finally {
            theJDBCUtil.closeJDBCStatement(selectStmt);
            theJDBCUtil.closeJDBCStatement(insertStmt);
            // Rollback our changes if necessary.
            try {
                if (conn != null && dbUpdated && !conn.getAutoCommit()) {
                    conn.rollback();
                    dbUpdated = false;
                }
            } catch (Exception e) {
                LOGGER.error("Ignored exception upon rollback", e);
            }
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Manages a remove request.
     */
    private void manageRemoveRequest(Mail mail) throws MessagingException {
        MailAddress senderMailAddress = mail.getMaybeSender().get();
        String senderUser = senderMailAddress.getLocalPart().toLowerCase(Locale.US);
        Domain senderHost = senderMailAddress.getDomain();

        Connection conn = null;
        PreparedStatement selectStmt = null;
        PreparedStatement deleteStmt = null;
        boolean dbUpdated = false;

        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);

        try {
            out.println("Answering on behalf of: " + whitelistManagerAddress);
            out.println("Removing from the white list of " + (new MailAddress(senderUser, senderHost)) + " ...");
            out.println();

            MimeMessage message = mail.getMessage();

            Object content = message.getContent();

            if (message.getContentType().startsWith("text/plain") && content instanceof String) {
                StringTokenizer st = new StringTokenizer((String) content, " \t\n\r\f,;:<>");
                while (st.hasMoreTokens()) {
                    ResultSet selectRS = null;
                    try {
                        MailAddress recipientMailAddress;
                        try {
                            recipientMailAddress = new MailAddress(st.nextToken());
                        } catch (jakarta.mail.internet.ParseException pe) {
                            continue;
                        }
                        String recipientUser = recipientMailAddress.getLocalPart().toLowerCase(Locale.US);
                        Domain recipientHost = recipientMailAddress.getDomain();

                        if (getMailetContext().isLocalServer(recipientHost)) {
                            // not a remote recipient, so skip
                            continue;
                        }

                        if (conn == null) {
                            conn = datasource.getConnection();
                        }

                        if (selectStmt == null) {
                            selectStmt = conn.prepareStatement(selectByPK);
                        }
                        selectStmt.setString(1, senderUser);
                        selectStmt.setString(2, senderHost.asString());
                        selectStmt.setString(3, recipientUser);
                        selectStmt.setString(4, recipientHost.asString());
                        selectRS = selectStmt.executeQuery();
                        if (!selectRS.next()) {
                            // This address was not in the list
                            out.println("Skipped: " + recipientMailAddress);
                            continue;
                        }

                        if (deleteStmt == null) {
                            deleteStmt = conn.prepareStatement(deleteByPK);
                        }
                        deleteStmt.setString(1, senderUser);
                        deleteStmt.setString(2, senderHost.asString());
                        deleteStmt.setString(3, recipientUser);
                        deleteStmt.setString(4, recipientHost.asString());
                        deleteStmt.executeUpdate();
                        dbUpdated = true;
                        out.println("Removed: " + recipientMailAddress);

                    } finally {
                        theJDBCUtil.closeJDBCResultSet(selectRS);
                    }
                }

                if (dbUpdated) {
                    LOGGER.debug("Removal request issued by {}", senderMailAddress);
                }
                // Commit our changes if necessary.
                if (conn != null && dbUpdated && !conn.getAutoCommit()) {
                    conn.commit();
                    dbUpdated = false;
                }
            } else {
                out.println("The message must be plain - no action");
            }

            out.println();
            out.println("Finished");

            sendReplyFromPostmaster(mail, sout.toString());

        } catch (SQLException sqle) {
            out.println("Error accessing the database");
            sendReplyFromPostmaster(mail, sout.toString());
            throw new MessagingException("Error accessing the database", sqle);
        } catch (IOException ioe) {
            out.println("Error getting message content");
            sendReplyFromPostmaster(mail, sout.toString());
            throw new MessagingException("Error getting message content", ioe);
        } finally {
            theJDBCUtil.closeJDBCStatement(selectStmt);
            theJDBCUtil.closeJDBCStatement(deleteStmt);
            // Rollback our changes if necessary.
            try {
                if (conn != null && dbUpdated && !conn.getAutoCommit()) {
                    conn.rollback();
                    dbUpdated = false;
                }
            } catch (Exception e) {
                LOGGER.error("Ignored exception upon rollback", e);
            }
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    private void sendReplyFromPostmaster(Mail mail, String stringContent) {
        try {
            MailAddress notifier = getMailetContext().getPostmaster();

            MailAddress senderMailAddress = mail.getMaybeSender().get();

            MimeMessage message = mail.getMessage();
            // Create the reply message
            MimeMessage reply = new MimeMessage(Session.getDefaultInstance(System.getProperties(), null));

            // Create the list of recipients in the Address[] format
            reply.setRecipients(Message.RecipientType.TO, senderMailAddress.toInternetAddress().stream()
                .collect(ImmutableList.toImmutableList())
                .toArray(new InternetAddress[0]));

            // Set the sender...
            notifier.toInternetAddress()
                .ifPresent(Throwing.<Address>consumer(reply::setFrom).sneakyThrow());

            // Create the message body
            MimeMultipart multipart = new MimeMultipart();
            // Add message as the first mime body part
            MimeBodyPart part = new MimeBodyPart();
            part.setContent(stringContent, "text/plain");
            part.setHeader(RFC2822Headers.CONTENT_TYPE, "text/plain");
            multipart.addBodyPart(part);

            reply.setContent(multipart);
            reply.setHeader(RFC2822Headers.CONTENT_TYPE, multipart.getContentType());

            // Create the list of recipients in our MailAddress format
            Set<MailAddress> recipients = new HashSet<>();
            recipients.add(senderMailAddress);

            // Set additional headers
            if (reply.getHeader(RFC2822Headers.DATE) == null) {
                reply.setHeader(RFC2822Headers.DATE, DateFormats.RFC822_DATE_FORMAT.format(ZonedDateTime.now()));
            }
            String subject = message.getSubject();
            if (subject == null) {
                subject = "";
            }
            if (subject.indexOf("Re:") == 0) {
                reply.setSubject(subject);
            } else {
                reply.setSubject("Re:" + subject);
            }
            reply.setHeader(RFC2822Headers.IN_REPLY_TO, message.getMessageID());

            // Send it off...
            getMailetContext().sendMail(notifier, recipients, reply);
        } catch (Exception e) {
            LOGGER.error("Exception found sending reply", e);
        }
    }

    /**
     * Initializes the sql query environment from the SqlResources file. Will
     * look for conf/sqlResources.xml.
     * 
     * @param conn
     *            The connection for accessing the database
     * @param mailetContext
     *            The current mailet context, for finding the
     *            conf/sqlResources.xml file
     * @throws Exception
     *             If any error occurs
     */
    private void initSqlQueries(Connection conn, org.apache.mailet.MailetContext mailetContext) throws Exception {
        try {
            if (conn.getAutoCommit()) {
                conn.setAutoCommit(false);
            }

            /*
                Holds value of property sqlFile.
            */
            String confDir = getInitParameterAsOptional("confDir")
                .orElseThrow(() -> new MailetException("WhiteListManager has no 'confDir' configured"));

            File sqlFile = new File(confDir, "sqlResources.xml").getCanonicalFile();
            sqlQueries.init(sqlFile, "WhiteList", conn, getSqlParameters());

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

        dbUpdated = createTable(conn, "whiteListTableName", "createWhiteListTable");

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

}
