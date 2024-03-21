/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.transport.mailets.managesieve;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.Username;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SieveParser;
import org.apache.james.managesieve.core.CoreProcessor;
import org.apache.james.managesieve.transcode.ArgumentParser;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.managesieve.util.SettableSession;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.transport.mailets.managesieve.transcode.MessageToCoreToMessage;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * <code>ManageSieveMailet</code> interprets mail from a local sender as
 * commands to manage Sieve scripts stored on the mail server. The commands are
 * a subset of those defined by <a
 * href=http://tools.ietf.org/html/rfc5804#section
 * -2>http://tools.ietf.org/html/rfc5804#section-2 "MessageToCoreToMessage"</a>.
 * 
 * <p>
 * For each supported command and associated response, the format is the same as
 * defined by RFC 5804, with the exception that when Sieve scripts are involved
 * in the exchange they are attached to the mail with the MIME type of
 * 'application/sieve' rather than being embedded in the command.
 * 
 * <p>
 * The command is written in the subject header of a mail received by this
 * mailet. Responses from this mailet are sent to the sender as mail with the
 * message body containing the response.
 * 
 * <p>
 * The following commands are supported:
 * <ul>
 * <li><a href=http://tools.ietf.org/html/rfc5804#section-2.4>CAPABILITY</a>
 * <li><a href=http://tools.ietf.org/html/rfc5804#section-2.5>HAVESPACE</a>
 * <li><a href=http://tools.ietf.org/html/rfc5804#section-2.6>PUTSCRIPT</a>
 * <li><a href=http://tools.ietf.org/html/rfc5804#section-2.7>LISTSCRIPTS</a>
 * <li><a href=http://tools.ietf.org/html/rfc5804#section-2.8>SETACTIVE</a>
 * <li><a href=http://tools.ietf.org/html/rfc5804#section-2.9>GETSCRIPT</a>
 * <li><a href=http://tools.ietf.org/html/rfc5804#section-2.10>DELETESCRIPT</a>
 * <li><a href=http://tools.ietf.org/html/rfc5804#section-2.11>RENAMESCRIPT</a>
 * <li><a href=http://tools.ietf.org/html/rfc5804#section-2.12>CHECKSCRIPT</a>
 * </ul>
 * 
 * <h2>An Important Note About Security</h2>
 * <p>
 * The mail server on which this mailet is deployed MUST robustly authenticate
 * the sender, who MUST be local.
 * <p>
 * Sieve provides powerful email processing capabilities that if hijacked can
 * expose the mail of individuals and organisations to intruders.
 */
@Experimental
public class ManageSieveMailet extends GenericMailet implements MessageToCoreToMessage.HelpProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManageSieveMailet.class);

    // Injected
    private SieveRepository sieveRepository = null;
    // Injected
    private SieveParser sieveParser = null;
    private UsersRepository usersRepository;
    private MessageToCoreToMessage transcoder = null;
    private URL helpURL = null;
    private String help = null;
    private boolean cache = true;

    @Override
    public void init() throws MessagingException {
        super.init();
        // Validate resources
        if (null == sieveParser) {
            throw new MessagingException("Missing resource \"sieveparser\"");
        }
        if (null == sieveRepository) {
            throw new MessagingException("Missing resource \"sieverepository\"");
        }
        
        setHelpURL(getInitParameter("helpURL"));
        cache = getInitParameter("cache", true);
        transcoder = new MessageToCoreToMessage(new ManageSieveProcessor(
                new ArgumentParser(new CoreProcessor(sieveRepository, usersRepository, sieveParser), false)),
            this);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        // Sanity checks
        if (!mail.hasSender()) {
            LOGGER.error("Sender is null");
            return;
        }
        if (!getMailetContext().isLocalServer(mail.getMaybeSender().get().getDomain())) {
            LOGGER.error("Sender not local");
            return;
        }

        // Update the Session for the current mail and execute
        SettableSession session = new SettableSession();
        if (mail.getAttribute(Mail.SMTP_AUTH_USER).isPresent()) {
            session.setState(Session.State.AUTHENTICATED);
        } else {
            session.setState(Session.State.UNAUTHENTICATED);
        }
        session.setUser(Username.of(mail.getMaybeSender().get().asString()));
        getMailetContext().sendMail(
            mail.getRecipients().iterator().next(),
            mail.getMaybeSender().asList(),
            transcoder.execute(session, mail.getMessage()));
        mail.setState(Mail.GHOST);
        
        // And tidy up
        clearCaches();
    }

    @Inject
    public void setSieveRepository(SieveRepository repository) {
        sieveRepository = repository;
    }

    @Inject
    public void setSieveParser(SieveParser sieveParser) {
        this.sieveParser = sieveParser;
    }

    @Inject
    public void setUsersRepository(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public String getMailetInfo() {
        return getClass().getName();
    }

    private void setHelpURL(String helpURL) throws MessagingException {
        try {
            this.helpURL = new URL(helpURL);
        } catch (MalformedURLException ex) {
            throw new MessagingException("Invalid helpURL", ex);
        }
    }

    private void clearCaches() {
        if (!cache) {
            help = null;
        }
    }

    @Override
    @VisibleForTesting
    public String getHelp() throws MessagingException {
        if (null == help) {
            help = computeHelp();
        }
        return help;
    }

    private String computeHelp() throws MessagingException {
        try (InputStream stream = helpURL.openStream();
                Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").next();
        } catch (IOException ex) {
            throw new MessagingException("Unable to access help URL: " + helpURL.toExternalForm(), ex);
        }
    }

}
