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

package org.apache.james.ai.classic;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Iterator;

import javax.sql.DataSource;

import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Spam detection mailet using bayesian analysis techniques.
 * </p>
 * 
 * <p>
 * Sets an email message header indicating the probability that an email message
 * is SPAM.
 * </p>
 * 
 * <p>
 * Based upon the principals described in: <a
 * href="http://www.paulgraham.com/spam.html">A Plan For Spam</a> by Paul
 * Graham. Extended to Paul Grahams' <a
 * href="http://paulgraham.com/better.html">Better Bayesian Filtering</a>.
 * </p>
 * 
 * <p>
 * The analysis capabilities are based on token frequencies (the <i>Corpus</i>)
 * learned through a training process (see {@link BayesianAnalysisFeeder}) and
 * stored in a JDBC database. After a training session, the Corpus must be
 * rebuilt from the database in order to acquire the new frequencies. Every 10
 * minutes a special thread in this mailet will check if any change was made to
 * the database by the feeder, and rebuild the corpus if necessary.
 * </p>
 * 
 * <p>
 * A <code>org.apache.james.spam.probability</code> mail attribute will be
 * created containing the computed spam probability as a
 * {@link java.lang.Double}. The <code>headerName</code> message header string
 * will be created containing such probability in floating point representation.
 * </p>
 * 
 * <p>
 * Sample configuration:
 * </p>
 * 
 * <pre>
 * <code>
 * &lt;mailet match="All" class="BayesianAnalysis"&gt;
 *   &lt;repositoryPath&gt;db://maildb&lt;/repositoryPath&gt;
 *   &lt;!--
 *     Set this to the header name to add with the spam probability
 *     (default is "X-MessageIsSpamProbability").
 *   --&gt;
 *   &lt;headerName&gt;X-MessageIsSpamProbability&lt;/headerName&gt;
 *   &lt;!--
 *     Set this to true if you want to ignore messages coming from local senders
 *     (default is false).
 *     By local sender we mean a return-path with a local server part (server listed
 *     in &lt;servernames&gt; in config.xml).
 *   --&gt;
 *   &lt;ignoreLocalSender&gt;true&lt;/ignoreLocalSender&gt;
 *   &lt;!--
 *     Set this to the maximum message size (in bytes) that a message may have
 *     to be considered spam (default is 100000).
 *   --&gt;
 *   &lt;maxSize&gt;100000&lt;/maxSize&gt;
 *   &lt;!--
 *     Set this to false if you not want to tag the message if spam is detected (Default is true).
 *   --&gt;
 *   &lt;tagSubject&gt;true&lt;/tagSubject&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * 
 * <p>
 * The probability of being spam is pre-pended to the subject if it is &gt; 0.1
 * (10%).
 * </p>
 * 
 * <p>
 * The required tables are automatically created if not already there (see
 * sqlResources.xml). The token field in both the ham and spam tables is <b>case
 * sensitive</b>.
 * </p>
 * 
 * @see BayesianAnalysisFeeder
 * @see BayesianAnalyzer
 * @see JDBCBayesianAnalyzer
 * @since 2.3.0
 */
@Experimental
public class BayesianAnalysis extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(BayesianAnalysis.class);
    /**
     * The JDBCUtil helper class
     */
    private final JDBCUtil theJDBCUtil = new JDBCUtil();

    /**
     * The JDBCBayesianAnalyzer class that does all the work.
     */
    private final JDBCBayesianAnalyzer analyzer = new JDBCBayesianAnalyzer();

    DataSource datasource;

    private static final AttributeName MAIL_ATTRIBUTE_NAME = AttributeName.of("org.apache.james.spam.probability");
    private static final String HEADER_NAME = "X-MessageIsSpamProbability";
    static final long CORPUS_RELOAD_INTERVAL = 600000;
    private String headerName;
    private boolean ignoreLocalSender = false;
    private boolean tagSubject = true;

    /**
     * Return a string describing this mailet.
     * 
     * @return a string describing this mailet
     */
    @Override
    public String getMailetInfo() {
        return "BayesianAnalysis Mailet";
    }

    /**
     * Holds value of property maxSize.
     */
    private int maxSize = 100000;

    /**
     * Holds value of property lastCorpusLoadTime.
     */
    private long lastCorpusLoadTime;

    private SystemContext fs;

    /**
     * Getter for property maxSize.
     * 
     * @return Value of property maxSize.
     */
    public int getMaxSize() {

        return this.maxSize;
    }

    /**
     * Getter for property lastCorpusLoadTime.
     * 
     * @return Value of property lastCorpusLoadTime.
     */
    public long getLastCorpusLoadTime() {

        return this.lastCorpusLoadTime;
    }

    @Resource(name = "datasource")
    public void setDataSource(DataSource datasource) {
        this.datasource = datasource;
    }

    @Resource(name = "filesystem")
    public void setFileSystem(SystemContext fs) {
        this.fs = fs;
    }

    /**
     * Sets lastCorpusLoadTime to System.currentTimeMillis().
     */
    private void touchLastCorpusLoadTime() {

        this.lastCorpusLoadTime = System.currentTimeMillis();
    }

    /**
     * Mailet initialization routine.
     * 
     * @throws MessagingException
     *             if a problem arises
     */
    @Override
    public void init() throws MessagingException {
        String repositoryPath = getInitParameter("repositoryPath");

        if (repositoryPath == null) {
            throw new MessagingException("repositoryPath is null");
        }

        headerName = getInitParameter("headerName", HEADER_NAME);

        ignoreLocalSender = Boolean.parseBoolean(getInitParameter("ignoreLocalSender"));

        if (ignoreLocalSender) {
            LOGGER.debug("Will ignore messages coming from local senders");
        } else {
            LOGGER.debug("Will analyze messages coming from local senders");
        }

        String maxSizeParam = getInitParameter("maxSize");
        if (maxSizeParam != null) {
            this.maxSize = Integer.parseInt(maxSizeParam);
        }
        LOGGER.debug("maxSize: {}", getMaxSize());

        String tag = getInitParameter("tagSubject");
        if (tag != null && tag.equals("false")) {
            tagSubject = false;
        }

        initDb();

        CorpusLoaderThread corpusLoader = new CorpusLoaderThread(this);
        corpusLoader.setDaemon(true);
        corpusLoader.start();

    }

    private void initDb() throws MessagingException {

        try {
            analyzer.initSqlQueries(datasource.getConnection(), fs.readXml("sqlResources.xml"));
        } catch (Exception e) {
            throw new MessagingException("Exception initializing queries", e);
        }

        try {
            loadData(datasource.getConnection());
        } catch (java.sql.SQLException se) {
            throw new MessagingException("SQLException loading data", se);
        }
    }

    /**
     * Scans the mail and determines the spam probability.
     * 
     * @param mail
     *            The Mail message to be scanned.
     * @throws MessagingException
     *             if a problem arises
     */
    @Override
    public void service(Mail mail) throws MessagingException {

        try {
            MimeMessage message = mail.getMessage();

            if (ignoreLocalSender && isSenderLocal(mail)) {
                // ignore the message if the sender is local
                return;
            }

            String[] headerArray = message.getHeader(headerName);
            // ignore the message if already analyzed
            if (headerArray != null && headerArray.length > 0) {
                return;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            double probability;

            if (message.getSize() < getMaxSize()) {
                message.writeTo(baos);
                probability = analyzer.computeSpamProbability(new BufferedReader(new StringReader(baos.toString())));
            } else {
                probability = 0.0;
            }

            mail.setAttribute(new Attribute(MAIL_ATTRIBUTE_NAME, AttributeValue.of(probability)));
            message.setHeader(headerName, Double.toString(probability));

            DecimalFormat probabilityForm = (DecimalFormat) DecimalFormat.getInstance();
            probabilityForm.applyPattern("##0.##%");
            String probabilityString = probabilityForm.format(probability);

            String senderString = mail.getMaybeSender().asString("null");
            if (probability > 0.1) {
                final Collection<MailAddress> recipients = mail.getRecipients();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(headerName + ": " + probabilityString + "; From: " + senderString + "; Recipient(s): " + getAddressesString(recipients));
                }

                // Check if we should tag the subject
                if (tagSubject) {
                    appendToSubject(message, " [" + probabilityString + (probability > 0.9 ? " SPAM" : " spam") + "]");
                }
            }

            saveChanges(message);

        } catch (Exception e) {
            LOGGER.error("Exception: {}", e.getMessage(), e);
            throw new MessagingException("Exception thrown", e);
        }
    }

    private boolean isSenderLocal(Mail mail) {
        return mail.getMaybeSender().asOptional()
            .map(MailAddress::getDomain)
            .map(domain -> getMailetContext().isLocalServer(domain))
            .orElse(false);
    }

    void loadData(Connection conn) throws java.sql.SQLException {

        try {
            // this is synchronized to avoid concurrent update of the corpus
            synchronized (JDBCBayesianAnalyzer.DATABASE_LOCK) {
                analyzer.tokenCountsClear();
                analyzer.loadHamNSpam(conn);
                analyzer.buildCorpus();
                analyzer.tokenCountsClear();
            }

            LOGGER.error("BayesianAnalysis Corpus loaded");

            touchLastCorpusLoadTime();

        } finally {
            if (conn != null) {
                theJDBCUtil.closeJDBCConnection(conn);
            }
        }

    }

    private String getAddressesString(Collection<MailAddress> addresses) {
        if (addresses == null) {
            return "null";
        }

        Iterator<MailAddress> iter = addresses.iterator();
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; iter.hasNext(); i++) {
            sb.append(iter.next());
            if (i + 1 < addresses.size()) {
                sb.append(", ");
            }
        }
        sb.append(']');
        return sb.toString();
    }

    private void appendToSubject(MimeMessage message, String toAppend) {
        try {
            String subject = message.getSubject();

            if (subject == null) {
                message.setSubject(toAppend, "iso-8859-1");
            } else {
                message.setSubject(toAppend + " " + subject, "iso-8859-1");
            }
        } catch (MessagingException ex) {
            LOGGER.error("Failure to append to subject phrase: '{}'", toAppend, ex);
        }
    }

    /**
     * Saves changes resetting the original message id.
     */
    private void saveChanges(MimeMessage message) throws MessagingException {
        String messageId = message.getMessageID();
        message.saveChanges();
        if (messageId != null) {
            message.setHeader(RFC2822Headers.MESSAGE_ID, messageId);
        }
    }

}
