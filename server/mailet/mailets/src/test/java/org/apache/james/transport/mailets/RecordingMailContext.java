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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.HostAddress;
import org.apache.mailet.LookupException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.apache.mailet.TemporaryLookupException;
import org.apache.mailet.base.test.FakeMailContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @since 15.12.12 12:02
 */
@SuppressWarnings("deprecation")
public class RecordingMailContext implements MailetContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordingMailContext.class);

    private final List<SendMailEvent> sendmails = new LinkedList<>();
    private final FakeMailContext context;

    public RecordingMailContext() {
        context = FakeMailContext.defaultContext();
    }

    @Override
    public boolean isLocalServer(String serverName) {
        return "localhost".equals(serverName);
    }

    @Override
    public void sendMail(Mail mail) throws MessagingException {
        sendmails.add(new SendMailEvent(mail));
    }

    @Override
    public void sendMail(MimeMessage msg) throws MessagingException {
       sendmails.add(new SendMailEvent(msg));
    }

    @Override
    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage msg) throws MessagingException {
        sendmails.add(new SendMailEvent(msg, sender, recipients));
    }

    @Override
    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage msg, String state) throws MessagingException {
        sendmails.add(new SendMailEvent(msg, sender, recipients, state));
    }

    public List<SendMailEvent> getSendmails() {
        return sendmails;
    }

    public static class SendMailEvent {
        private final Object message;
        private final MailAddress sender;
        private final Collection<MailAddress> recipients;
        private final String state;

        private SendMailEvent(Object message, MailAddress sender, Collection<MailAddress> recipients, String state) {
            this.message = message;
            this.sender = sender;
            this.recipients = recipients;
            this.state = state;
        }

        public SendMailEvent(Mail mail) {
            this(mail, null, null, null);
        }

        public SendMailEvent(MimeMessage mail) {
            this(mail, null, null, null);
        }

        public SendMailEvent(MimeMessage mail, MailAddress sender, Collection<MailAddress> recipients) {
            this(mail, sender, recipients, null);
        }
        public SendMailEvent(MimeMessage mail, MailAddress sender, Collection<MailAddress> recipients, String state) {
            this((Object)mail, sender, recipients, state);
        }

        public Mail getMail() {
            if (message instanceof Mail) {
                return (Mail) message;
            }
            return null;
        }
        public MimeMessage getMessage() {
            if (message instanceof MimeMessage) {
                return  (MimeMessage) message;
            }
            return null;
        }

        public MailAddress getSender() {
            return sender;
        }

        public Collection<MailAddress> getRecipients() {
            return recipients;
        }

        public String getState() {
            return state;
        }
    }

    @Override
    public int getMajorVersion() {
        return context.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return context.getMinorVersion();
    }

    @Override
    public String getServerInfo() {
        return context.getServerInfo();
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return context.getAttributeNames();
    }

    @Override
    public Object getAttribute(String name) {
        return context.getAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        context.setAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        context.removeAttribute(name);
    }

    @Override
    public void log(String message) {
        LOGGER.info(message);
    }

    @Override
    public void log(String message, Throwable t) {
        LOGGER.error(message, t);
    }

    @Override
    public void log(LogLevel level, String message) {
        context.log(level, message);
    }

    @Override
    public void log(LogLevel level, String message, Throwable t) {
        context.log(level, message, t);
    }

    @Override
    public MailAddress getPostmaster() {
        return context.getPostmaster();
    }

    @Override
    public boolean isLocalUser(String userAccount) {
        return context.isLocalUser(userAccount);
    }

    @Override
    public boolean isLocalEmail(MailAddress mailAddress) {
        return context.isLocalEmail(mailAddress);
    }

    @Override
    public Collection<String> getMailServers(String domain) {
        return context.getMailServers(domain);
    }

    @Override
    public Iterator<HostAddress> getSMTPHostAddresses(String domain) {
        return context.getSMTPHostAddresses(domain);
    }

    @Override
    public void bounce(Mail mail, String message) throws MessagingException {
        context.bounce(mail, message);
    }

    @Override
    public void bounce(Mail mail, String message, MailAddress bouncer) throws MessagingException {
        context.bounce(mail, message, bouncer);
    }

    @Override
    public List<String> dnsLookup(String name, RecordType type) throws TemporaryLookupException, LookupException {
        return context.dnsLookup(name, type);
    }

    @Override
    public Logger getLogger() {
        return context.getLogger();
    }
}
