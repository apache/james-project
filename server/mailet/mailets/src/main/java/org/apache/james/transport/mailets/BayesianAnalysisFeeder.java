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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.util.Enumeration;

import javax.inject.Inject;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.sql.DataSource;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.util.bayesian.JDBCBayesianAnalyzer;
import org.apache.james.util.sql.JDBCUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

/**
 * <p>
 * Feeds ham OR spam messages to train the {@link BayesianAnalysis} mailet.
 * </p>
 * 
 * <p>
 * The new token frequencies will be stored in a JDBC database.
 * </p>
 * 
 * <p>
 * Sample configuration:
 * </p>
 * 
 * <pre>
 * <code>
 * &lt;processor name="root"&gt;
 * 
 *   &lt;mailet match="RecipientIs=not.spam@thisdomain.com" class="BayesianAnalysisFeeder"&gt;
 *     &lt;repositoryPath&gt; db://maildb &lt;/repositoryPath&gt;
 *     &lt;feedType&gt;ham&lt;/feedType&gt;
 *     &lt;!--
 *       Set this to the maximum message size (in bytes) that a message may have
 *       to be analyzed (default is 100000).
 *     --&gt;
 *     &lt;maxSize&gt;100000&lt;/maxSize&gt;
 *   &lt;/mailet&gt;
 * 
 *   &lt;mailet match="RecipientIs=spam@thisdomain.com" class="BayesianAnalysisFeeder"&gt;
 *     &lt;repositoryPath&gt; db://maildb &lt;/repositoryPath&gt;
 *     &lt;feedType&gt;spam&lt;/feedType&gt;
 *     &lt;!--
 *       Set this to the maximum message size (in bytes) that a message may have
 *       to be analyzed (default is 100000).
 *     --&gt;
 *     &lt;maxSize&gt;100000&lt;/maxSize&gt;
 *   &lt;/mailet&gt;
 * 
 * &lt;processor&gt;
 * </code>
 * </pre>
 * 
 * <p>
 * The previous example will allow the user to send messages to the server and
 * use the recipient email address as the indicator for whether the message is
 * ham or spam.
 * </p>
 * 
 * <p>
 * Using the example above, send good messages (ham not spam) to the email
 * address "not.spam@thisdomain.com" to pump good messages into the feeder, and
 * send spam messages (spam not ham) to the email address "spam@thisdomain.com"
 * to pump spam messages into the feeder.
 * </p>
 * 
 * <p>
 * The bayesian database tables will be updated during the training reflecting
 * the new data
 * </p>
 * 
 * <p>
 * At the end the mail will be destroyed (ghosted).
 * </p>
 * 
 * <p>
 * <b>The correct approach is to send the original ham/spam message as an
 * attachment to another message sent to the feeder; all the headers of the
 * enveloping message will be removed and only the original message's tokens
 * will be analyzed.</b>
 * </p>
 * 
 * <p>
 * After a training session, the frequency <i>Corpus</i> used by
 * <code>BayesianAnalysis</code> must be rebuilt from the database, in order to
 * take advantage of the new token frequencies. Every 10 minutes a special
 * thread in the <code>BayesianAnalysis</code> mailet will check if any change
 * was made to the database, and rebuild the corpus if necessary.
 * </p>
 * 
 * <p>
 * Only one message at a time is scanned (the database update activity is
 * <i>synchronized</i>) in order to avoid too much database locking, as
 * thousands of rows may be updated just for one message fed.
 * </p>
 * 
 * @see BayesianAnalysis
 * @see org.apache.james.util.bayesian.BayesianAnalyzer
 * @see org.apache.james.util.bayesian.JDBCBayesianAnalyzer
 * @since 2.3.0
 */

public class BayesianAnalysisFeeder extends GenericMailet {
    /**
     * The JDBCUtil helper class
     */
    private final JDBCUtil theJDBCUtil = new JDBCUtil() {
        protected void delegatedLog(String logString) {
            log("BayesianAnalysisFeeder: " + logString);
        }
    };

    /**
     * The JDBCBayesianAnalyzer class that does all the work.
     */
    private final JDBCBayesianAnalyzer analyzer = new JDBCBayesianAnalyzer() {
        protected void delegatedLog(String logString) {
            log("BayesianAnalysisFeeder: " + logString);
        }
    };

    private DataSource datasource;

    private String feedType;

    /**
     * Return a string describing this mailet.
     * 
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "BayesianAnalysisFeeder Mailet";
    }

    /**
     * Holds value of property maxSize.
     */
    private int maxSize = 100000;

    private FileSystem fs;

    /**
     * Getter for property maxSize.
     * 
     * @return Value of property maxSize.
     */
    public int getMaxSize() {

        return this.maxSize;
    }

    @Inject
    public void setDataSource(DataSource datasource) {
        this.datasource = datasource;
    }

    /**
     * Setter for property maxSize.
     * 
     * @param maxSize
     *            New value of property maxSize.
     */
    public void setMaxSize(int maxSize) {

        this.maxSize = maxSize;
    }

    @Inject
    public void setFileSystem(FileSystem fs) {
        this.fs = fs;
    }

    /**
     * Mailet initialization routine.
     * 
     * @throws MessagingException
     *             if a problem arises
     */
    public void init() throws MessagingException {
        String repositoryPath = getInitParameter("repositoryPath");

        if (repositoryPath == null) {
            throw new MessagingException("repositoryPath is null");
        }

        feedType = getInitParameter("feedType");
        if (feedType == null) {
            throw new MessagingException("feedType is null");
        }

        String maxSizeParam = getInitParameter("maxSize");
        if (maxSizeParam != null) {
            setMaxSize(Integer.parseInt(maxSizeParam));
        }
        log("maxSize: " + getMaxSize());

        initDb();

    }

    private void initDb() throws MessagingException {

        try {
            analyzer.initSqlQueries(datasource.getConnection(), fs.getFile("file://conf/sqlResources.xml"));
        } catch (Exception e) {
            throw new MessagingException("Exception initializing queries", e);
        }

    }

    /**
     * Scans the mail and updates the token frequencies in the database.
     * 
     * The method is synchronized in order to avoid too much database locking,
     * as thousands of rows may be updated just for one message fed.
     * 
     * @param mail
     *            The Mail message to be scanned.
     */
    public void service(Mail mail) {
        boolean dbUpdated = false;

        mail.setState(Mail.GHOST);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Connection conn = null;

        try {

            MimeMessage message = mail.getMessage();

            String messageId = message.getMessageID();

            if (message.getSize() > getMaxSize()) {
                log(messageId + " Feeding HAM/SPAM ignored because message size > " + getMaxSize() + ": " + message.getSize());
                return;
            }

            clearAllHeaders(message);

            message.writeTo(baos);

            BufferedReader br = new BufferedReader(new StringReader(baos.toString()));

            // this is synchronized to avoid concurrent update of the corpus
            synchronized (JDBCBayesianAnalyzer.DATABASE_LOCK) {

                conn = datasource.getConnection();

                if (conn.getAutoCommit()) {
                    conn.setAutoCommit(false);
                }

                dbUpdated = true;

                // Clear out any existing word/counts etc..
                analyzer.clear();

                if ("ham".equalsIgnoreCase(feedType)) {
                    log(messageId + " Feeding HAM");
                    // Process the stream as ham (not spam).
                    analyzer.addHam(br);

                    // Update storage statistics.
                    analyzer.updateHamTokens(conn);
                } else {
                    log(messageId + " Feeding SPAM");
                    // Process the stream as spam.
                    analyzer.addSpam(br);

                    // Update storage statistics.
                    analyzer.updateSpamTokens(conn);
                }

                // Commit our changes if necessary.
                if (conn != null && dbUpdated && !conn.getAutoCommit()) {
                    conn.commit();
                    dbUpdated = false;
                    log(messageId + " Training ended successfully");
                    JDBCBayesianAnalyzer.touchLastDatabaseUpdateTime();
                }

            }

        } catch (java.sql.SQLException se) {
            log("SQLException: " + se.getMessage());
        } catch (java.io.IOException ioe) {
            log("IOException: " + ioe.getMessage());
        } catch (javax.mail.MessagingException me) {
            log("MessagingException: " + me.getMessage());
        } finally {
            // Rollback our changes if necessary.
            try {
                if (conn != null && dbUpdated && !conn.getAutoCommit()) {
                    conn.rollback();
                    dbUpdated = false;
                }
            } catch (Exception e) {
            }
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    private void clearAllHeaders(MimeMessage message) throws javax.mail.MessagingException {
        Enumeration headers = message.getAllHeaders();

        while (headers.hasMoreElements()) {
            Header header = (Header) headers.nextElement();
            try {
                message.removeHeader(header.getName());
            } catch (javax.mail.MessagingException me) {
            }
        }
        message.saveChanges();
    }

}
