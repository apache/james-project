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
import java.util.Scanner;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.managesieve.api.SieveParser;
import org.apache.james.managesieve.core.CoreProcessor;
import org.apache.james.managesieve.transcode.LineToCore;
import org.apache.james.managesieve.transcode.LineToCoreToLine;
import org.apache.james.managesieve.util.SettableSession;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.transport.mailets.managesieve.transcode.MessageToCoreToMessage;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;

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
public class ManageSieveMailet extends GenericMailet implements MessageToCoreToMessage.HelpProvider {

    private class MailSession extends SettableSession {

        public MailSession() {
            super();
        }

        public void setMail(Mail mail) {
            setUser(getUser(mail.getSender()));
            setAuthentication(null != mail.getAttribute(SMTP_AUTH_USER_ATTRIBUTE_NAME));
        }

        protected String getUser(MailAddress addr) {
            return addr.getLocalPart() + '@' + (null == addr.getDomain() ? "localhost" : addr.getDomain());
        }

    }

    public final static String SMTP_AUTH_USER_ATTRIBUTE_NAME = "org.apache.james.SMTPAuthUser";

    private MailSession session = null;
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
        session = new MailSession();
        transcoder = new MessageToCoreToMessage(
            new LineToCoreToLine(
                new LineToCore(
                    new CoreProcessor(session, sieveRepository, usersRepository, sieveParser))),
            this);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        // Sanity checks
        if (mail.getSender() == null) {
            getMailetContext().log("ERROR: Sender is null");
            return;
        }

        if (!getMailetContext().isLocalServer(mail.getSender().getDomain().toLowerCase())) {
            getMailetContext().log("ERROR: Sender not local");
            return;
        }

        // Update the Session for the current mail and execute
        session.setMail(mail);
        getMailetContext().sendMail(transcoder.execute(mail.getMessage()));
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

    protected void setHelpURL(String helpURL) throws MessagingException {
        try {
            this.helpURL = new URL(helpURL);
        } catch (MalformedURLException ex) {
            throw new MessagingException("Invalid helpURL", ex);
        }
    }

    protected void clearCaches() {
        if (!cache) {
            help = null;
        }
    }

    public String getHelp() throws MessagingException {
        if (null == help) {
            help = computeHelp();
        }
        return help;
    }

    protected String computeHelp() throws MessagingException {
        InputStream stream = null;
        try {
            stream = helpURL.openStream();
            return new Scanner(stream, "UTF-8").useDelimiter("\\A").next();
        } catch (IOException ex) {
            throw new MessagingException("Unable to access help URL: " + helpURL.toExternalForm(), ex);
        }
        finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ex) {
                    // no op
                }
            }
        }
    }

}
