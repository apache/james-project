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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageWrapper;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.HostAddress;
import org.apache.mailet.LookupException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@SuppressWarnings("deprecation")
public class FakeMailContext implements MailetContext {

    public static Builder builder() {
        return new Builder();
    }

    public static SentMail.Builder sentMailBuilder() {
        return new SentMail.Builder();
    }

    public static SentMail.Builder fromMail(Mail mail) throws MessagingException {
        return sentMailBuilder()
            .sender(mail.getMaybeSender().asOptional())
            .recipients(mail.getRecipients())
            .message(mail.getMessage())
            .state(mail.getState())
            .attributes(mail.attributes().collect(ImmutableList.toImmutableList()))
            .fromMailet();
    }

    public static FakeMailContext defaultContext() {
        return builder().build();
    }

    public static class Builder {

        private Logger logger;
        private Optional<MailAddress> postmaster;

        private Builder() {
            postmaster = Optional.empty();
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder postmaster(MailAddress postmaster) {
            this.postmaster = Optional.of(postmaster);
            return this;
        }

        public FakeMailContext build() {
            return new FakeMailContext(Optional.ofNullable(logger), postmaster.orElse(null));
        }
    }

    public static class SentMail {

        private static MimeMessage tryCopyMimeMessage(MimeMessage msg) throws MessagingException {
            ThrowingFunction<MimeMessage, MimeMessage> throwingFunction = MimeMessageWrapper::wrap;

            return Optional.ofNullable(msg)
                .map(Throwing.function(throwingFunction).sneakyThrow())
                .orElse(null);
        }

        public static class Builder {
            private MailAddress sender;
            private Optional<Collection<MailAddress>> recipients = Optional.empty();
            private MimeMessage msg;
            private Map<AttributeName, Attribute> attributes = new HashMap<>();
            private Optional<String> state = Optional.empty();
            private Optional<Boolean> fromMailet = Optional.empty();
            private Optional<Delay> delay = Optional.empty();

            public Builder sender(MailAddress sender) {
                this.sender = sender;
                return this;
            }

            public Builder sender(Optional<MailAddress> sender) {
                sender.ifPresent(this::sender);
                return this;
            }

            public Builder sender(String sender) throws AddressException {
                return sender(new MailAddress(sender));
            }

            public Builder recipients(Collection<MailAddress> recipients) {
                this.recipients = Optional.of(recipients);
                return this;
            }

            public Builder fromMailet() {
                this.fromMailet = Optional.of(true);
                return this;
            }

            public Builder recipients(MailAddress... recipients) {
                this.recipients = Optional.<Collection<MailAddress>>of(ImmutableList.copyOf(recipients));
                return this;
            }

            public Builder recipient(MailAddress recipient) {
                Preconditions.checkNotNull(recipient);
                return recipients(ImmutableList.of(recipient));
            }

            public Builder recipient(String recipient) throws AddressException {
                Preconditions.checkNotNull(recipient);
                return recipients(new MailAddress(recipient));
            }

            public Builder message(MimeMessage mimeMessage) {
                this.msg = mimeMessage;
                return this;
            }

            public Builder attributes(List<Attribute> attributes) {
                this.attributes.putAll(attributes
                    .stream()
                    .collect(ImmutableMap.toImmutableMap(
                            attribute -> attribute.getName(),
                            Function.identity())));
                return this;
            }

            @Deprecated
            public Builder attribute(String key, Serializable value) {
                return attribute(Attribute.convertToAttribute(key, value));
            }

            public Builder attribute(Attribute attribute) {
                this.attributes.put(attribute.getName(), attribute);
                return this;
            }

            public Builder state(String state) {
                this.state = Optional.of(state);
                return this;
            }

            public Builder delay(Delay delay) {
                this.delay = Optional.of(delay);
                return this;
            }

            public SentMail build() throws MessagingException {
                if (fromMailet.orElse(false)) {
                    this.attribute(Mail.SENT_BY_MAILET_ATTRIBUTE);
                }
                return new SentMail(sender, recipients.orElse(ImmutableList.<MailAddress>of()), msg,
                    ImmutableMap.copyOf(attributes), state.orElse(Mail.DEFAULT), delay);
            }
        }

        private final MailAddress sender;
        private final Collection<MailAddress> recipients;
        private final MimeMessage msg;
        private final Optional<String> subject;
        private final Map<AttributeName, Attribute> attributes;
        private final String state;
        private final Optional<Delay> delay;

        private SentMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage msg, Map<AttributeName, Attribute> attributes, String state, Optional<Delay> delay) throws MessagingException {
            this.sender = sender;
            this.recipients = ImmutableList.copyOf(recipients);
            this.msg = tryCopyMimeMessage(msg);
            this.subject = getSubject(msg);
            this.attributes = ImmutableMap.copyOf(attributes);
            this.state = state;
            this.delay = delay;
        }

        private Optional<String> getSubject(MimeMessage msg) {
            try {
                return Optional.ofNullable(msg.getSubject());
            } catch (Exception e) {
                return Optional.empty();
            }
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

        public Optional<String> getSubject() {
            return subject;
        }

        public Map<AttributeName, Attribute> getAttributes() {
            return ImmutableMap.copyOf(attributes);
        }

        public Optional<Delay> getDelay() {
            return delay;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SentMail)) {
                return false;
            }

            SentMail sentMail = (SentMail) o;

            return Objects.equals(this.sender, sentMail.sender)
                && Objects.equals(this.recipients, sentMail.recipients)
                && Objects.equals(this.attributes, sentMail.attributes)
                && Objects.equals(this.state, sentMail.state);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sender, recipients, attributes, state);
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

    public static class Delay {
        private final long duration;
        private final TimeUnit timeUnit;

        public Delay(long duration, TimeUnit timeUnit) {
            this.duration = duration;
            this.timeUnit = timeUnit;
        }

        public long getDuration() {
            return duration;
        }

        public TimeUnit getTimeUnit() {
            return timeUnit;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Delay) {
                Delay delay = (Delay) o;

                return Objects.equals(this.duration, delay.duration)
                    && Objects.equals(this.timeUnit, delay.timeUnit);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(duration, timeUnit);
        }
    }

    public static class BouncedMail {
        private final SentMail sentMail;
        private final String message;
        private final Optional<MailAddress> bouncer;

        public BouncedMail(SentMail sentMail, String message, Optional<MailAddress> bouncer) {
            this.sentMail = sentMail;
            this.message = message;
            this.bouncer = bouncer;
        }

        public BouncedMail(SentMail.Builder sentMail, String message, Optional<MailAddress> bouncer) throws MessagingException {
            this(sentMail.build(), message, bouncer);
        }

        public SentMail getSentMail() {
            return sentMail;
        }

        public String getMessage() {
            return message;
        }

        public Optional<MailAddress> getBouncer() {
            return bouncer;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof BouncedMail) {
                BouncedMail that = (BouncedMail) o;
                return Objects.equals(this.sentMail, that.sentMail)
                    && Objects.equals(this.message, that.message)
                    && Objects.equals(this.bouncer, that.bouncer);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(sentMail, message, bouncer);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("sentMail", sentMail)
                .add("message", message)
                .add("bouncer", bouncer)
                .toString();
        }
    }

    private final HashMap<AttributeName, Attribute> attributes;
    private final Collection<SentMail> sentMails;
    private final Collection<BouncedMail> bouncedMails;
    private final Optional<Logger> logger;
    private final MailAddress postmaster;

    private FakeMailContext(Optional<Logger> logger, MailAddress postmaster) {
        attributes = new HashMap<>();
        sentMails = new ConcurrentLinkedQueue<>();
        bouncedMails = new ConcurrentLinkedQueue<>();
        this.logger = logger;
        this.postmaster = postmaster;
    }

    @Override
    public void bounce(Mail mail, String message) throws MessagingException {
        bouncedMails.add(new BouncedMail(fromMail(mail), message, Optional.empty()));
    }

    @Override
    public void bounce(Mail mail, String message, MailAddress bouncer) throws MessagingException {
        bouncedMails.add(new BouncedMail(fromMail(mail), message, Optional.ofNullable(bouncer)));
    }

    /**
     * @deprecated use the generic dnsLookup method
     */
    @Deprecated
    @Override
    public Collection<String> getMailServers(Domain host) {
        return null;  // trivial implementation
    }

    @Override
    public MailAddress getPostmaster() {
        return postmaster;
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(AttributeName.of(name));
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return attributes
            .keySet()
            .stream()
            .map(AttributeName::asString)
            .iterator();
    }

    @Override
    public int getMajorVersion() {
        return 0;  // trivial implementation
    }

    @Override
    public int getMinorVersion() {
        return 0;  // trivial implementation
    }

    @Override
    public String getServerInfo() {
        return "Mock Server";
    }

    @Override
    public boolean isLocalServer(Domain domain) {
        return domain.equals(Domain.LOCALHOST);
    }

    /**
     * @deprecated use {@link #isLocalEmail(MailAddress)} instead 
     */
    @Deprecated
    @Override
    public boolean isLocalUser(String userAccount) {
        return false;  // trivial implementation
    }

    @Override
    public boolean isLocalEmail(MailAddress mailAddress) {
        return false;  // trivial implementation
    }

    /**
     * @deprecated use {@link #log(LogLevel level, String message)}
     */
    @Deprecated
    @Override
    public void log(String message) {
        System.out.println(message);
    }

    /**
     * @deprecated use {@link #log(LogLevel level, String message, Throwable t)}
     */
    @Deprecated
    @Override
    public void log(String message, Throwable t) {
        System.out.println(message);
        t.printStackTrace(System.out);
    }

    @Override
    public void removeAttribute(String name) {
        // trivial implementation
    }

    @Override
    public void sendMail(MimeMessage mimemessage) throws MessagingException {
        sentMails.add(sentMailBuilder()
            .message(mimemessage)
            .fromMailet()
            .build());
    }

    @Override
    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage msg) throws MessagingException {
        sentMails.add(sentMailBuilder()
            .recipients(recipients)
            .sender(sender)
            .message(msg)
            .fromMailet()
            .build());
    }

    @Override
    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage msg, String state) throws MessagingException {
        sentMails.add(sentMailBuilder()
            .recipients(recipients)
            .message(msg)
            .state(state)
            .sender(sender)
            .fromMailet()
            .build());
    }

    @Override
    public void sendMail(Mail mail) throws MessagingException {
        sendMail(mail, Optional.ofNullable(mail.getState()).orElse(Mail.DEFAULT));
    }

    @Override
    public void sendMail(Mail mail, long delay, TimeUnit unit) throws MessagingException {
        sendMail(mail, Mail.DEFAULT, delay, unit);
    }

    @Override
    public void sendMail(Mail mail, String state) throws MessagingException {
        mail.setState(state);
        sentMails.add(fromMail(mail).build());
    }

    @Override
    public void sendMail(Mail mail, String state, long delay, TimeUnit unit) throws MessagingException {
        mail.setState(state);
        sentMails.add(
            fromMail(mail)
                .delay(new Delay(delay, unit))
                .build());
    }

    @Deprecated
    public void setAttribute(String name, Serializable object) {
        setAttribute(Attribute.convertToAttribute(name, object));
    }

    public void setAttribute(Attribute attribute) {
        attributes.put(attribute.getName(), attribute);
    }

    /**
     * @deprecated use the generic dnsLookup method
     */
    @Deprecated
    @Override
    public Iterator<HostAddress> getSMTPHostAddresses(Domain domainName) {
        return null;  // trivial implementation
    }

    @Override
    public void setAttribute(String name, Object value) {
        throw new UnsupportedOperationException("MOCKed method");
    }

    @Override
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

    @Override
    public void log(LogLevel level, String message, Throwable t) {
        if (logger.isPresent()) {
            switch (level) {
            case INFO:
                logger.get().info(message, t);
                break;
            case WARN:
                logger.get().warn(message, t);
                break;
            case ERROR:
                logger.get().error(message, t);
                break;
            default:
                logger.get().debug(message, t);
            }
        } else {
            System.out.println("[" + level + "]" + message);
            t.printStackTrace(System.out);
        }
    }

    @Override
    public List<String> dnsLookup(String name, RecordType type) throws LookupException {
        return null;   // trivial implementation
    }

    public List<SentMail> getSentMails() {
        return ImmutableList.copyOf(sentMails);
    }

    public void resetSentMails() {
        sentMails.clear();
    }

    public List<BouncedMail> getBouncedMails() {
        return ImmutableList.copyOf(bouncedMails);
    }

    @Override
    public Logger getLogger() {
        return logger.orElse(null);
    }
}
