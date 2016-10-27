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
package org.apache.james.transport.mailets.delivery;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
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
import org.apache.james.core.MimeMessageInputStream;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.transport.mailets.jsieve.ActionDispatcher;
import org.apache.james.transport.mailets.jsieve.CommonsLoggingAdapter;
import org.apache.james.transport.mailets.jsieve.Poster;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.james.transport.mailets.jsieve.SieveMailAdapter;
import org.apache.james.transport.util.MailetContextLog;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.jsieve.ConfigurationManager;
import org.apache.jsieve.SieveConfigurationException;
import org.apache.jsieve.SieveFactory;
import org.apache.jsieve.exception.SieveException;
import org.apache.jsieve.parser.generated.ParseException;
import org.apache.jsieve.parser.generated.TokenMgrError;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;

import com.google.common.base.Optional;

/**
 * Contains resource bindings.
 */
public class SieveMailet  extends GenericMailet implements Poster {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UsersRepository usersRepos;
        private MailboxManager mailboxManager;
        private String folder;
        private ResourceLocator resourceLocator;
        private String deliveryHeader;
        private boolean resetReturnPath;
        private boolean consume;
        private Optional<Boolean> verbose = Optional.absent();
        private Optional<Boolean> quiet = Optional.absent();

        public Builder userRepository(UsersRepository usersRepository) {
            this.usersRepos = usersRepository;
            return this;
        }

        public Builder mailboxManager(MailboxManager mailboxManager) {
            this.mailboxManager = mailboxManager;
            return this;
        }

        public Builder folder(String folder) {
            this.folder = folder;
            return this;
        }

        public Builder resourceLocator(ResourceLocator resourceLocator) {
            this.resourceLocator = resourceLocator;
            return this;
        }

        public Builder deliveryHeader(String deliveryHeader) {
            this.deliveryHeader = deliveryHeader;
            return this;
        }

        public Builder resetReturnPath(boolean resetReturnPath) {
            this.resetReturnPath = resetReturnPath;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = Optional.of(verbose);
            return this;
        }

        public Builder consume(boolean consume) {
            this.consume = consume;
            return this;
        }

        public Builder quiet(boolean quiet) {
            this.quiet = Optional.of(quiet);
            return this;
        }

        public SieveMailet build() throws MessagingException {
            if (resourceLocator == null) {
                throw new MailetException("Not initialised. Please ensure that the mailet container supports either setter or constructor injection");
            }
            return new SieveMailet(usersRepos,mailboxManager, resourceLocator, folder, deliveryHeader, resetReturnPath, consume, verbose.or(false), quiet.or(false));
        }

    }

    private final UsersRepository usersRepos;
    private final MailboxManager mailboxManager;
    private final String folder;
    private final ResourceLocator resourceLocator;
    private final String deliveryHeader;
    private final boolean resetReturnPath;
    private final boolean isInfo;
    private final boolean verbose;
    private final boolean consume;
    private final SieveFactory factory;
    private final ActionDispatcher actionDispatcher;
    private final Log log;

    private SieveMailet(UsersRepository usersRepos, MailboxManager mailboxManager, ResourceLocator resourceLocator, String folder, String deliveryHeader,
                       boolean resetReturnPath, boolean consume, boolean verbose, boolean quiet) throws MessagingException {

        this.usersRepos = usersRepos;
        this.resourceLocator = resourceLocator;
        this.mailboxManager = mailboxManager;
        this.folder = folder;
        this.actionDispatcher = new ActionDispatcher();
        this.deliveryHeader = deliveryHeader;
        this.resetReturnPath = resetReturnPath;
        this.consume = consume;
        this.isInfo = verbose || !quiet;
        this.verbose = verbose;
        this.log = new CommonsLoggingAdapter(this, computeLogLevel(quiet, verbose));
        try {
            final ConfigurationManager configurationManager = new ConfigurationManager();
            configurationManager.setLog(log);
            factory = configurationManager.build();
        } catch (SieveConfigurationException e) {
            throw new MessagingException("Failed to load standard Sieve configuration.", e);
        }
    }

    private int computeLogLevel(boolean quiet, boolean verbose) {
        if (verbose) {
            return CommonsLoggingAdapter.TRACE;
        } else if (quiet) {
            return CommonsLoggingAdapter.FATAL;
        } else {
            return CommonsLoggingAdapter.WARN;
        }
    }

    protected String getUsername(MailAddress m) {
        try {
            if (usersRepos.supportVirtualHosting()) {
                return m.toString();
            } else {
                return m.getLocalPart() + "@localhost";
            }
        } catch (UsersRepositoryException e) {
            log.error("Unable to access UsersRepository", e);
            return m.getLocalPart() + "@localhost";

        }
    }

    public void storeMail(MailAddress sender, MailAddress recipient, Mail mail) throws MessagingException {
        if (recipient == null) {
            throw new IllegalArgumentException(
                "Recipient for mail to be spooled cannot be null.");
        }
        if (mail.getMessage() == null) {
            throw new IllegalArgumentException(
                "Mail message to be spooled cannot be null.");
        }

        sieveMessage(recipient, mail);
        String s;
        if (sender != null) {
            s = sender.toString();
        } else {
            s = "<>";
        }
        // If no exception was thrown the message was successfully stored in the
        // mailbox
        log.info("Local delivered mail " + mail.getName() + " sucessfully from " + s + " to " + recipient.toString()
                + " in folder " + this.folder);
    }

    @Override
    public void post(String url, MimeMessage mail) throws MessagingException {

        final int endOfScheme = url.indexOf(':');

        if (endOfScheme < 0) {
            throw new MessagingException("Malformed URI");
        }

        else {

            final String scheme = url.substring(0, endOfScheme);
            if ("mailbox".equals(scheme)) {
                int startOfUser = endOfScheme + 3;
                int endOfUser = url.indexOf('@', startOfUser);
                if (endOfUser < 0) {
                    // TODO: When user missing, append to a default location
                    throw new MessagingException("Shared mailbox is not supported");
                } else {
                    // lowerCase the user - see
                    // https://issues.apache.org/jira/browse/JAMES-1369
                    String user = url.substring(startOfUser, endOfUser).toLowerCase();
                    int startOfHost = endOfUser + 1;
                    int endOfHost = url.indexOf('/', startOfHost);
                    String host = url.substring(startOfHost, endOfHost);
                    String urlPath;
                    int length = url.length();
                    if (endOfHost + 1 == length) {
                        urlPath = this.folder;
                    } else {
                        urlPath = url.substring(endOfHost, length);
                    }

                    // Check if we should use the full email address as username
                    try {
                        if (usersRepos.supportVirtualHosting()) {
                            user = user + "@" + host;
                        }
                    } catch (UsersRepositoryException e) {
                        throw new MessagingException("Unable to accessUsersRepository", e);
                    }

                    MailboxSession session;
                    try {
                        session = mailboxManager.createSystemSession(user, new MailetContextLog(getMailetContext()));
                    } catch (BadCredentialsException e) {
                        throw new MessagingException("Unable to authenticate to mailbox", e);
                    } catch (MailboxException e) {
                        throw new MessagingException("Can not access mailbox", e);
                    }

                    // Start processing request
                    mailboxManager.startProcessingRequest(session);

                    // This allows Sieve scripts to use a standard delimiter
                    // regardless of mailbox implementation
                    String destination = urlPath.replace('/', session.getPathDelimiter());

                    if (destination == null || "".equals(destination)) {
                        destination = this.folder;
                    }
                    if (destination.startsWith(session.getPathDelimiter() + ""))
                        destination = destination.substring(1);

                    // Use the MailboxSession to construct the MailboxPath - See
                    // JAMES-1326
                    final MailboxPath path = new MailboxPath(MailboxConstants.USER_NAMESPACE, user, destination);
                    try {
                        if (this.folder.equalsIgnoreCase(destination) && !(mailboxManager.mailboxExists(path, session))) {
                            mailboxManager.createMailbox(path, session);
                        }
                        final MessageManager mailbox = mailboxManager.getMailbox(path, session);
                        if (mailbox == null) {
                            final String error = "Mailbox for user " + user + " was not found on this server.";
                            throw new MessagingException(error);
                        }

                        mailbox.appendMessage(new MimeMessageInputStream(mail), new Date(), session, true, null);

                    } catch (MailboxException e) {
                        throw new MessagingException("Unable to access mailbox.", e);
                    } finally {
                        session.close();
                        try {
                            mailboxManager.logout(session, true);
                        } catch (MailboxException e) {
                            throw new MessagingException("Can logout from mailbox", e);
                        }

                        // Stop processing request
                        mailboxManager.endProcessingRequest(session);

                    }
                }

            }

            else {
                // TODO: add support for more protocols
                // TODO: - for example mailto: for forwarding over SMTP
                // TODO: - for example xmpp: for forwarding over Jabber
                throw new MessagingException("Unsupported protocol");
            }
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
                log.error("Error while storing mail.", ex);
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

    protected void sieveMessage(MailAddress recipient, Mail aMail) throws MessagingException {
        String username = getUsername(recipient);
        try {
            final ResourceLocator.UserSieveInformation userSieveInformation = resourceLocator.get(getScriptUri(recipient));
            sieveMessageEvaluate(recipient, aMail, userSieveInformation);
        } catch (Exception ex) {
            // SIEVE is a mail filtering protocol.
            // Rejecting the mail because it cannot be filtered
            // seems very unfriendly.
            // So just log and store in INBOX
            if (isInfo) {
                log.error("Cannot evaluate Sieve script. Storing mail in user INBOX.", ex);
            }
            storeMessageInbox(username, aMail.getMessage());
        }
    }

    private void sieveMessageEvaluate(MailAddress recipient, Mail aMail, ResourceLocator.UserSieveInformation userSieveInformation) throws MessagingException, IOException {
        try {
            SieveMailAdapter aMailAdapter = new SieveMailAdapter(aMail,
                getMailetContext(), actionDispatcher, this, userSieveInformation.getScriptActivationDate(),
                userSieveInformation.getScriptInterpretationDate(), recipient);
            aMailAdapter.setLog(log);
            // This logging operation is potentially costly
            if (verbose) {
                log.error("Evaluating " + aMailAdapter.toString() + "against \""
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
        post("mailbox://" + username + "/", message);
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
