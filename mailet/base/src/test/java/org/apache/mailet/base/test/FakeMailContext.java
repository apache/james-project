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

package org.apache.mailet.base.test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.HostAddress;
import org.apache.mailet.LookupException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

@SuppressWarnings("deprecation")
public class FakeMailContext implements MailetContext {

    public static Builder builder() {
        return new Builder();
    }

    public static SentMail.Builder sentMailBuilder() {
        return new SentMail.Builder();
    }

    public static FakeMailContext defaultContext() {
        return builder().build();
    }

    public static class Builder {

        private Logger logger;

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public FakeMailContext build() {
            return new FakeMailContext(Optional.fromNullable(logger));
        }
    }

    public static class SentMail {

        public static class Builder {
            private MailAddress sender;
            private Optional<Collection<MailAddress>> recipients = Optional.absent();
            private MimeMessage msg;
            private Optional<Map<String, Serializable>> attributes = Optional.absent();
            private Optional<String> state = Optional.absent();

            public Builder sender(MailAddress sender) {
                this.sender = sender;
                return this;
            }

            public Builder recipients(Collection<MailAddress> recipients) {
                this.recipients = Optional.of(recipients);
                return this;
            }

            public Builder recipient(MailAddress recipient) {
                Preconditions.checkNotNull(recipient);
                return recipients(ImmutableList.of(recipient));
            }

            public Builder message(MimeMessage mimeMessage) {
                this.msg = mimeMessage;
                return this;
            }

            public Builder attributes(Map<String, Serializable> attributes) {
                this.attributes = Optional.of(attributes);
                return this;
            }

            public Builder state(String state) {
                this.state = Optional.of(state);
                return this;
            }

            public SentMail build() {
                return new SentMail(sender, recipients.or(ImmutableList.<MailAddress>of()), msg,
                    attributes.or(ImmutableMap.<String, Serializable>of()), state.or(Mail.DEFAULT));
            }
        }

        private final MailAddress sender;
        private final Collection<MailAddress> recipients;
        private final MimeMessage msg;
        private final Map<String, Serializable> attributes;
        private final String state;

        private SentMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage msg, Map<String, Serializable> attributes, String state) {
            this.sender = sender;
            this.recipients = ImmutableList.copyOf(recipients);
            this.msg = msg;
            this.attributes = ImmutableMap.copyOf(attributes);
            this.state = state;
        }

        public MailAddress getSender() {
            return sender;
        }

        public Collection<MailAddress> getRecipients() {
            return recipients;
        }

        public MimeMessage getMsg() {
            return msg;
        }

        public String getState() {
            return state;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SentMail)) {
                return false;
            }

            SentMail sentMail = (SentMail) o;

            return Objects.equal(this.sender, sentMail.sender)
                && Objects.equal(this.recipients, sentMail.recipients)
                && Objects.equal(this.attributes, sentMail.attributes)
                && Objects.equal(this.state, sentMail.state);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(sender, recipients, attributes, state);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("recipients", recipients)
                .add("sender", sender)
                .add("attributeNames", attributes)
                .add("state", state)
                .toString();
        }
    }

    private final HashMap<String, Object> attributes;
    private final List<SentMail> sentMails;
    private final Optional<Logger> logger;

    private FakeMailContext(Optional<Logger> logger) {
        attributes = new HashMap<String, Object>();
        sentMails = new ArrayList<SentMail>();
        this.logger = logger;
    }

    public void bounce(Mail mail, String message) throws MessagingException {
        // trivial implementation
    }

    public void bounce(Mail mail, String message, MailAddress bouncer) throws MessagingException {
        // trivial implementation
    }

    /**
     * @deprecated use the generic dnsLookup method
     */
    public Collection<String> getMailServers(String host) {
        return null;  // trivial implementation
    }

    public MailAddress getPostmaster() {
        return null;  // trivial implementation
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    public int getMajorVersion() {
        return 0;  // trivial implementation
    }

    public int getMinorVersion() {
        return 0;  // trivial implementation
    }

    public String getServerInfo() {
        return "Mock Server";
    }

    public boolean isLocalServer(String serverName) {
        return serverName.equals("localhost");  // trivial implementation
    }

    /**
     * @deprecated use {@link #isLocalEmail(MailAddress)} instead 
     */
    public boolean isLocalUser(String userAccount) {
        return false;  // trivial implementation
    }

    public boolean isLocalEmail(MailAddress mailAddress) {
        return false;  // trivial implementation
    }

    /**
     * @deprecated use {@link #log(LogLevel level, String message)}
     */
    public void log(String message) {
        System.out.println(message);
    }

    /**
     * @deprecated use {@link #log(LogLevel level, String message, Throwable t)}
     */
    public void log(String message, Throwable t) {
        System.out.println(message);
        t.printStackTrace(System.out);
    }

    public void removeAttribute(String name) {
        // trivial implementation
    }

    public void sendMail(MimeMessage mimemessage) throws MessagingException {
        sentMails.add(new SentMail.Builder()
            .message(mimemessage)
            .build());
    }

    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage msg) throws MessagingException {
        sentMails.add(new SentMail.Builder()
            .recipients(recipients)
            .sender(sender)
            .message(msg)
            .build());
    }

    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage msg, String state) throws MessagingException {
        sentMails.add(new SentMail.Builder()
            .recipients(recipients)
            .message(msg)
            .state(state)
            .sender(sender)
            .build());
    }

    public void sendMail(Mail mail) throws MessagingException {
        sentMails.add(new SentMail.Builder()
            .sender(mail.getSender())
            .recipients(mail.getRecipients())
            .message(mail.getMessage())
            .state(mail.getState())
            .attributes(buildAttributesMap(mail))
            .build());
    }

    private ImmutableMap<String, Serializable> buildAttributesMap(Mail mail) {
        Map<String, Serializable> result = new HashMap<String, Serializable>();
        List<String> attributesNames = Lists.newArrayList(mail.getAttributeNames());
        for (String attributeName: attributesNames) {
            result.put(attributeName, mail.getAttribute(attributeName));
        }
        return ImmutableMap.copyOf(result);
    }

    public void setAttribute(String name, Serializable object) {
        attributes.put(name,object);
    }

    public void storeMail(MailAddress sender, MailAddress recipient, MimeMessage msg) throws MessagingException {
        // trivial implementation
    }

    /**
     * @deprecated use the generic dnsLookup method
     */
    public Iterator<HostAddress> getSMTPHostAddresses(String domainName) {
        return null;  // trivial implementation
    }

    public void setAttribute(String name, Object value) {
        throw new UnsupportedOperationException("MOCKed method");
    }

    public void log(LogLevel level, String message) {
        if (logger.isPresent()) {
            switch (level) {
            case INFO:
                logger.get().info(message);
                break;
            case WARN:
                logger.get().warn(message);
                break;
            case ERROR:
                logger.get().error(message);
                break;
            default:
                logger.get().debug(message);
            }
        } else {
            System.out.println("[" + level + "]" + message);
        }
    }

    public void log(LogLevel level, String message, Throwable t) {
        log(level, message);
        log(level, t.getMessage());
    }

    public List<String> dnsLookup(String name, RecordType type) throws LookupException {
        return null;   // trivial implementation
    }

    public List<SentMail> getSentMails() {
        return sentMails;
    }

    @Override
    public Logger getLogger() {
        return logger.orNull();
    }
}
