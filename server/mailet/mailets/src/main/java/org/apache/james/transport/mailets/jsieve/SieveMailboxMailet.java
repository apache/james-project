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

package org.apache.james.transport.mailets.jsieve;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.logging.Log;
import org.apache.jsieve.ConfigurationManager;
import org.apache.jsieve.SieveConfigurationException;
import org.apache.jsieve.SieveFactory;
import org.apache.jsieve.exception.SieveException;
import org.apache.jsieve.parser.generated.ParseException;
import org.apache.jsieve.parser.generated.TokenMgrError;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;

/**
 * <p>Executes a <a href='http://www.rfc-editor.org/rfc/rfc3028.txt'>Sieve</a>
 * script against incoming mail. The script applied is based on the recipient.</p>
 * <h4>Init Parameters</h4>
 * <table>
 * <thead><tr><th>Name</th><th>Required</th><th>Values</th><th>Role</th></thead>
 * <tr><td>verbose</td><td>No - defaults to false</td><td>true (ignoring case) to enable, otherwise disable</td>
 * <td>
 * Enables verbose logging.
 * </td></tr>
 * </table>
 */
public class SieveMailboxMailet extends GenericMailet {
    
    /**
     * The delivery header
     */
    private String deliveryHeader;

    /**
     * resetReturnPath
     */
    private boolean resetReturnPath;
    /** Experimental */
    private Poster poster;
    /** Experimental */
    private ResourceLocator locator;
    
    /** Indicates whether this mailet should log verbosely */
    private boolean verbose = false;
    
    private boolean consume = true;
    /** Indicates whether this mailet should log minimal information */
    private boolean quiet = true;

    private SieveFactory factory;

    private ActionDispatcher actionDispatcher;

    private Log log;

    /**
     * For SDI
     */
    public SieveMailboxMailet() {}
    
    /**
     * CDI
     * @param poster not null
     */
    public SieveMailboxMailet(Poster poster, ResourceLocator locator) {
        this();
        this.poster = poster;
        this.locator = locator;
    }

    
    public ResourceLocator getLocator() {
        return locator;
    }

    /**
     * For SDI
     * @param locator not null
     */
    public void setLocator(ResourceLocator locator) {
        this.locator = locator;
    }

    public Poster getPoster() {
        return poster;
    }
    
    /**
     * For SDI
     * @param poster not null
     */
    public void setPoster(Poster poster) {
        this.poster = poster;
    }

    /**
     * Is this mailet GHOSTing all mail it processes?
     * @return true when mailet consumes all mail, false otherwise
     */
    public boolean isConsume() {
        return consume;
    }

    /**
     * Sets whether this mailet should GHOST all mail.
     * @param consume true when the mailet should consume all mail, 
     * false otherwise
     */
    public void setConsume(boolean consume) {
        this.consume = consume;
    }

    /**
     * Is this mailet logging verbosely?
     * This property is set by init parameters.
     * @return true if logging should be verbose, false otherwise
     */
    public boolean isVerbose() {
        return verbose;
    }


    /**
     * Sets whether logging should be verbose for this mailet.
     * This property is set by init parameters.
     * This setting overrides {@link #isQuiet()}.
     * @param verbose true when logging should be verbose,
     * false otherwise
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Is the logging for this mailet set to minimal?
     * @return true
     */
    public boolean isQuiet() {
        return quiet;
    }

    /**
     * Sets the logging for this mailet to minimal.
     * This is overriden by {@link #setVerbose(boolean)}.
     * @param quiet true for minimal logging, false otherwise
     */
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }
    
   
    /**
     * Is informational logging turned on? 
     * @return true when minimal logging is off,
     * false when logging is minimal
     */
    public boolean isInfoLoggingOn() {
        return verbose || !quiet;
    }

    @Override
    public void init(MailetConfig config) throws MessagingException {
        
        super.init(config);

        try {
            final ConfigurationManager configurationManager = new ConfigurationManager();
            final int logLevel;
            if (verbose) {
                logLevel = CommonsLoggingAdapter.TRACE;
            } else if (quiet) {
                logLevel = CommonsLoggingAdapter.FATAL;
            } else {
                logLevel = CommonsLoggingAdapter.WARN;
            }
            log = new CommonsLoggingAdapter(this, logLevel);
            configurationManager.setLog(log);
            factory = configurationManager.build();
        } catch (SieveConfigurationException e) {
            throw new MessagingException("Failed to load standard Sieve configuration.", e);
        }
    }

    /**
     * Delivers a mail to a local mailbox.
     * 
     * @param mail
     *            the mail being processed
     * 
     * @throws MessagingException
     *             if an error occurs while storing the mail
     */
    @SuppressWarnings("unchecked")
    @Override
    public void service(Mail mail) throws MessagingException {
        Collection<MailAddress> recipients = mail.getRecipients();
        Collection<MailAddress> errors = new Vector<MailAddress>();

        MimeMessage message = null;
        if (deliveryHeader != null || resetReturnPath) {
            message = mail.getMessage();
        }

        if (resetReturnPath) {
            // Set Return-Path and remove all other Return-Path headers from the
            // message
            // This only works because there is a placeholder inserted by
            // MimeMessageWrapper
            message.setHeader(RFC2822Headers.RETURN_PATH,
                    (mail.getSender() == null ? "<>" : "<" + mail.getSender()
                            + ">"));
        }

        Enumeration headers;
        InternetHeaders deliveredTo = new InternetHeaders();
        if (deliveryHeader != null) {
            // Copy any Delivered-To headers from the message
            headers = message
                    .getMatchingHeaders(new String[] { deliveryHeader });
            while (headers.hasMoreElements()) {
                Header header = (Header) headers.nextElement();
                deliveredTo.addHeader(header.getName(), header.getValue());
            }
        }

        for (Iterator<MailAddress> i = recipients.iterator(); i.hasNext();) {
            MailAddress recipient = i.next();
            try {
                if (deliveryHeader != null) {
                    // Add qmail's de facto standard Delivered-To header
                    message.addHeader(deliveryHeader, recipient.toString());
                }

                storeMail(mail.getSender(), recipient, mail);

                if (deliveryHeader != null) {
                    if (i.hasNext()) {
                        // Remove headers but leave all placeholders
                        message.removeHeader(deliveryHeader);
                        headers = deliveredTo.getAllHeaders();
                        // And restore any original Delivered-To headers
                        while (headers.hasMoreElements()) {
                            Header header = (Header) headers.nextElement();
                            message.addHeader(header.getName(), header
                                    .getValue());
                        }
                    }
                }
            } catch (Exception ex) {
                log("Error while storing mail.", ex);
                errors.add(recipient);
            }
        }

        if (!errors.isEmpty()) {
            // If there were errors, we redirect the email to the ERROR
            // processor.
            // In order for this server to meet the requirements of the SMTP
            // specification, mails on the ERROR processor must be returned to
            // the sender. Note that this email doesn't include any details
            // regarding the details of the failure(s).
            // In the future we may wish to address this.
            getMailetContext().sendMail(mail.getSender(), errors,
                    mail.getMessage(), Mail.ERROR);
        }
        if (consume) {
            // Consume this message
            mail.setState(Mail.GHOST);
        }
    }

    /**
     * Return a string describing this mailet.
     * 
     * @return a string describing this mailet
     */
    @Override
    public String getMailetInfo() {
        return "Sieve Mailbox Mailet";
    }

    /**
     * 
     * @param sender
     * @param recipient
     * @param mail
     * @throws MessagingException
     */
    public void storeMail(MailAddress sender, MailAddress recipient,
            Mail mail) throws MessagingException {
        if (recipient == null) {
            throw new IllegalArgumentException(
                    "Recipient for mail to be spooled cannot be null.");
        }
        if (mail.getMessage() == null) {
            throw new IllegalArgumentException(
                    "Mail message to be spooled cannot be null.");
        }
        
        sieveMessage(recipient, mail);
 
    }
    
    protected void sieveMessage(MailAddress recipient, Mail aMail) throws MessagingException {
        String username = getUsername(recipient);
        try {
            final ResourceLocator.UserSieveInformation userSieveInformation = locator.get(getScriptUri(recipient));
            sieveMessageEvaluate(recipient, aMail, userSieveInformation);
        } catch (Exception ex) {
            // SIEVE is a mail filtering protocol.
            // Rejecting the mail because it cannot be filtered
            // seems very unfriendly.
            // So just log and store in INBOX
            if (isInfoLoggingOn()) {
                log("Cannot evaluate Sieve script. Storing mail in user INBOX.", ex);
            }
            storeMessageInbox(username, aMail.getMessage());
        }
    }
    
    private void sieveMessageEvaluate(MailAddress recipient, Mail aMail, ResourceLocator.UserSieveInformation userSieveInformation) throws MessagingException, IOException {
            try {
                SieveMailAdapter aMailAdapter = new SieveMailAdapter(aMail,
                    getMailetContext(), actionDispatcher, poster, userSieveInformation.getScriptActivationDate(),
                    userSieveInformation.getScriptInterpretationDate(), recipient);
                aMailAdapter.setLog(log);
                // This logging operation is potentially costly
                if (verbose) {
                    log("Evaluating " + aMailAdapter.toString() + "against \""
                            + getScriptUri(recipient) + "\"");
                }
                factory.evaluate(aMailAdapter, factory.parse(userSieveInformation.getScriptContent()));
            } catch (SieveException ex) {
                handleFailure(recipient, aMail, ex);
            }
            catch (ParseException ex) {
                handleFailure(recipient, aMail, ex);
            }
            catch (TokenMgrError ex)
            {
                handleFailure(recipient, aMail, new SieveException(ex));
            }
    }
    
    protected void storeMessageInbox(String username, MimeMessage message) throws MessagingException {
        String url = "mailbox://" + username + "/";
        poster.post(url, message);
    }

    /**
     * @see org.apache.mailet.base.GenericMailet#init()
     */
    @Override
    public void init() throws MessagingException {
        super.init();
        if (poster == null || locator == null) {
            throw new MailetException("Not initialised. Please ensure that the mailet container supports either" +
                    " setter or constructor injection");
        }
        
        this.deliveryHeader = getInitParameter("addDeliveryHeader");
        this.resetReturnPath = getInitParameter("resetReturnPath", true);
        this.consume = getInitParameter("consume", true);
        this.verbose = getInitParameter("verbose", false);
        this.quiet = getInitParameter("quiet", false);
        
        actionDispatcher = new ActionDispatcher();
    }
    
    /**
     * Return the username to use for sieve processing for the given MailAddress
     * 
     * @param m
     * @return username
     */
    protected String getUsername(MailAddress m) {
        return m.getLocalPart() + "@localhost";
    }
    
    /**
     * Return the URI for the sieve script
     *
     * @param m
     * @return
     */
    protected String getScriptUri(MailAddress m) {
        return "//" + getUsername(m) + "/sieve";
    }
    
    /**
     * Deliver the original mail as an attachment with the main part being an error report.
     *
     * @param recipient
     * @param aMail
     * @param ex
     * @throws MessagingException
     * @throws IOException 
     */
    protected void handleFailure(MailAddress recipient, Mail aMail, Exception ex)
            throws MessagingException, IOException {
        String user = getUsername(recipient);

        MimeMessage originalMessage = aMail.getMessage();
        MimeMessage message = new MimeMessage(originalMessage);
        MimeMultipart multipart = new MimeMultipart();
        
        MimeBodyPart noticePart = new MimeBodyPart();
        noticePart.setText("An error was encountered while processing this mail with the active sieve script for user \""
                + user + "\". The error encountered was:\r\n" + ex.getLocalizedMessage() + "\r\n");
        multipart.addBodyPart(noticePart);
        
        MimeBodyPart originalPart = new MimeBodyPart();
        originalPart.setContent(originalMessage, "message/rfc822");
        if ((originalMessage.getSubject() != null) && (!originalMessage.getSubject().trim().isEmpty())) {
            originalPart.setFileName(originalMessage.getSubject().trim());
        } else {
            originalPart.setFileName("No Subject");
        }
        originalPart.setDisposition(MimeBodyPart.INLINE);
        multipart.addBodyPart(originalPart);
        
        message.setContent(multipart);
        message.setSubject("[SIEVE ERROR] " + originalMessage.getSubject());
        message.setHeader("X-Priority", "1");
        message.saveChanges();
        
        storeMessageInbox(user, message);
    }
   
}
