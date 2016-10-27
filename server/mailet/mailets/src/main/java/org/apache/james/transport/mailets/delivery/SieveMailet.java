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
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.logging.Log;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.transport.mailets.jsieve.ActionDispatcher;
import org.apache.james.transport.mailets.jsieve.CommonsLoggingAdapter;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.james.transport.mailets.jsieve.SieveMailAdapter;
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
import org.apache.mailet.MailetContext;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Contains resource bindings.
 */
public class SieveMailet  extends GenericMailet {

    public static final String DELIVERED_TO = "Delivered-To";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UsersRepository usersRepos;
        private MailboxManager mailboxManager;
        private String folder;
        private ResourceLocator resourceLocator;
        private MailetContext mailetContext;
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

        public Builder mailetContext(MailetContext mailetContext) {
            this.mailetContext = mailetContext;
            return this;
        }

        public SieveMailet build() throws MessagingException {
            if (resourceLocator == null) {
                throw new MailetException("Not initialised. Please ensure that the mailet container supports either setter or constructor injection");
            }
            return new SieveMailet(usersRepos, mailboxManager, resourceLocator, mailetContext, folder, consume, verbose.or(false), quiet.or(false));
        }

    }

    private final UsersRepository usersRepos;
    private final SievePoster sievePoster;
    private final String folder;
    private final ResourceLocator resourceLocator;
    private final boolean isInfo;
    private final boolean verbose;
    private final boolean consume;
    private final SieveFactory factory;
    private final ActionDispatcher actionDispatcher;
    private final Log log;

    private SieveMailet(UsersRepository usersRepos, MailboxManager mailboxManager, ResourceLocator resourceLocator, MailetContext mailetContext, String folder,
                        boolean consume, boolean verbose, boolean quiet) throws MessagingException {
        this.sievePoster = new SievePoster(mailboxManager, folder, usersRepos, mailetContext);
        this.usersRepos = usersRepos;
        this.resourceLocator = resourceLocator;
        this.folder = folder;
        this.actionDispatcher = new ActionDispatcher();
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
        Preconditions.checkNotNull(recipient, "Recipient for mail to be spooled cannot be null.");
        Preconditions.checkNotNull(mail.getMessage(), "Mail message to be spooled cannot be null.");

        sieveMessage(recipient, mail);
        // If no exception was thrown the message was successfully stored in the mailbox
        log.info("Local delivered mail " + mail.getName() + " sucessfully from " + prettyPrint(sender) + " to " + prettyPrint(recipient)
                + " in folder " + this.folder);
    }

    private String prettyPrint(MailAddress mailAddress) {
        if (mailAddress != null) {
            return  "<" + mailAddress.toString() + ">";
        } else {
            return  "<>";
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

        MimeMessage message = mail.getMessage();

        // Set Return-Path and remove all other Return-Path headers from the
        // message
        // This only works because there is a placeholder inserted by
        // MimeMessageWrapper
        message.setHeader(RFC2822Headers.RETURN_PATH, prettyPrint(mail.getSender()));

        List<String> deliveredToHeader = Collections.list(message.getMatchingHeaders(new String[] { DELIVERED_TO }));
        message.removeHeader(DELIVERED_TO);

        for (MailAddress recipient : recipients) {
            try {
                // Add qmail's de facto standard Delivered-To header
                message.addHeader(DELIVERED_TO, recipient.toString());
                storeMail(mail.getSender(), recipient, mail);
                message.removeHeader(DELIVERED_TO);
            } catch (Exception ex) {
                log.error("Error while storing mail.", ex);
                errors.add(recipient);
            }
        }
        for (String deliveredTo : deliveredToHeader) {
            message.addHeader(DELIVERED_TO, deliveredTo);
        }
        if (!errors.isEmpty()) {
            // If there were errors, we redirect the email to the ERROR
            // processor.
            // In order for this server to meet the requirements of the SMTP
            // specification, mails on the ERROR processor must be returned to
            // the sender. Note that this email doesn't include any details
            // regarding the details of the failure(s).
            // In the future we may wish to address this.
            getMailetContext().sendMail(mail.getSender(), errors, mail.getMessage(), Mail.ERROR);
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
                getMailetContext(), actionDispatcher, sievePoster, userSieveInformation.getScriptActivationDate(),
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
        sievePoster.post("mailbox://" + username + "/", message);
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

    protected void handleFailure(MailAddress recipient, Mail aMail, Exception ex) throws MessagingException, IOException {
        String user = getUsername(recipient);
        storeMessageInbox(user, SieveFailureMessageComposer.composeMessage(aMail, ex, user));
    }

}
