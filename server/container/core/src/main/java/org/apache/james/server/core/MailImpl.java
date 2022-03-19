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

package org.apache.james.server.core;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.ParseException;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.PerRecipientHeaders.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Chars;

/**
 * <p>
 * Wraps a MimeMessage adding routing information (from SMTP) and some simple
 * API enhancements.
 * </p>
 * <p>
 * From James version > 2.2.0a8 "mail attributes" have been added. Backward and
 * forward compatibility is supported:
 * <ul>
 * <li>messages stored in file repositories <i>without</i> attributes by James
 * version <= 2.2.0a8 will be processed by later versions as having an empty
 * attributes hashmap;</li>
 * <li>messages stored in file repositories <i>with</i> attributes by James
 * version > 2.2.0a8 will be processed by previous versions, ignoring the
 * attributes.</li>
 * </ul>
 * </p>
 */
public class MailImpl implements Disposable, Mail {

    /**
     * Create a copy of the input mail and assign it a new name
     *
     * @param mail original mail
     * @throws MessagingException when the message is not clonable
     */
    public static MailImpl duplicate(Mail mail) throws MessagingException {
        return duplicateWithoutMessage(mail)
            .mimeMessage(mail.getMessage())
            .build();
    }

    public static MailImpl.Builder duplicateWithoutMessage(Mail mail) throws MessagingException {
        return MailImpl.builder()
            .name(deriveNewName(mail.getName()))
            .sender(mail.getMaybeSender())
            .addRecipients(mail.getRecipients())
            .remoteHost(mail.getRemoteHost())
            .remoteAddr(mail.getRemoteAddr())
            .lastUpdated(mail.getLastUpdated())
            .errorMessage(mail.getErrorMessage())
            .addAttributes(duplicateAttributes(mail))
            .addAllHeadersForRecipients(mail.getPerRecipientSpecificHeaders());
    }

    private static ImmutableList<Attribute> duplicateAttributes(Mail mail) {
        try {
            return mail.attributes().map(Attribute::duplicate).collect(ImmutableList.toImmutableList());
        } catch (IllegalStateException e) {
            LOGGER.error("Error while cloning Mail attributes", e);
            return ImmutableList.of();
        }
    }

    public static MailImpl fromMimeMessage(String name, MimeMessage mimeMessage) throws MessagingException {
        return MailImpl.builder()
            .name(name)
            .sender(getSender(mimeMessage))
            .addRecipients(getRecipients(mimeMessage))
            .mimeMessage(mimeMessage)
            .build();
    }

    public static RequireName builder() {
        return Builder::new;
    }

    public interface RequireName {
        Builder name(String name);
    }

    public static class Builder {

        private final String name;
        private Optional<MimeMessage> mimeMessage;
        private List<MailAddress> recipients;
        private Optional<MailAddress> sender;
        private Optional<String> state;
        private Optional<String> errorMessage;
        private Optional<Date> lastUpdated;
        private Map<AttributeName, Attribute> attributes;
        private Optional<String> remoteAddr;
        private Optional<String> remoteHost;
        private PerRecipientHeaders perRecipientHeaders;

        private Builder(String name) {
            Preconditions.checkNotNull(name);
            Preconditions.checkArgument(!name.isEmpty(), "name must not be empty");
            this.name = name;
            mimeMessage = Optional.empty();
            recipients = Lists.newArrayList();
            sender = Optional.empty();
            state = Optional.empty();
            errorMessage = Optional.empty();
            lastUpdated = Optional.empty();
            attributes = Maps.newHashMap();
            remoteAddr = Optional.empty();
            remoteHost = Optional.empty();
            perRecipientHeaders = new PerRecipientHeaders();
        }

        public String getName() {
            return name;
        }

        public Builder mimeMessage(MimeMessage mimeMessage) {
            this.mimeMessage = Optional.ofNullable(mimeMessage);
            return this;
        }

        public Builder mimeMessage(MimeMessageBuilder mimeMessage) throws MessagingException {
            this.mimeMessage = Optional.ofNullable(mimeMessage.build());
            return this;
        }

        public Builder addRecipients(Collection<MailAddress> recipients) {
            this.recipients.addAll(recipients);
            return this;
        }

        public Builder addRecipients(MailAddress... recipients) {
            return addRecipients(ImmutableList.copyOf(recipients));
        }

        public Builder addRecipients(String... recipients) {
            return addRecipients(Arrays.stream(recipients)
                .map(Throwing.function(MailAddress::new))
                .collect(ImmutableList.toImmutableList()));
        }

        public Builder addRecipient(MailAddress recipient) {
            return addRecipients(recipient);
        }

        public Builder addRecipient(String recipient) throws AddressException {
            return addRecipients(recipient);
        }

        public Builder sender(MailAddress sender) {
            return sender(Optional.ofNullable(sender));
        }

        public Builder sender(Optional<MailAddress> sender) {
            this.sender = sender;
            return this;
        }

        public Builder sender(MaybeSender sender) {
            this.sender = sender.asOptional();
            return this;
        }

        public Builder sender(String sender) throws AddressException {
            return sender(new MailAddress(sender));
        }

        public Builder state(String state) {
            this.state = Optional.ofNullable(state);
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = Optional.ofNullable(errorMessage);
            return this;
        }

        public Builder lastUpdated(Date lastUpdated) {
            this.lastUpdated = Optional.ofNullable(lastUpdated);
            return this;
        }

        @Deprecated
        public Builder addAttribute(String name, Serializable object) {
            return addAttribute(Attribute.convertToAttribute(name, object));
        }

        public Builder addAttribute(Attribute attribute) {
            this.attributes.put(attribute.getName(), attribute);
            return this;
        }

        public Builder addAttributes(Collection<Attribute> attributes) {
            attributes.forEach(this::addAttribute);
            return this;
        }

        public Builder remoteAddr(String remoteAddr) {
            this.remoteAddr = Optional.ofNullable(remoteAddr);
            return this;
        }

        public Builder remoteHost(String remoteHost) {
            this.remoteHost = Optional.ofNullable(remoteHost);
            return this;
        }

        public Builder addHeaderForRecipient(Header header, MailAddress recipient) {
            this.perRecipientHeaders.addHeaderForRecipient(header, recipient);
            return this;
        }

        public Builder addAllHeadersForRecipients(PerRecipientHeaders perRecipientHeaders) {
            this.perRecipientHeaders.addAll(perRecipientHeaders);
            return this;
        }

        public MailImpl build() {
            MailImpl mail = new MailImpl(name, state.orElse(DEFAULT), attributes, recipients, perRecipientHeaders);

            mimeMessage.ifPresent(Throwing.consumer(mail::setMessage).sneakyThrow());
            sender.ifPresent(mail::setSender);
            errorMessage.ifPresent(mail::setErrorMessage);
            lastUpdated.ifPresent(mail::setLastUpdated);
            remoteAddr.ifPresent(mail::setRemoteAddr);
            remoteHost.ifPresent(mail::setRemoteHost);
            return mail;
        }
    }

    private static Map<AttributeName, Attribute> toAttributeMap(Map<String, ?> attributes) {
        return attributes.entrySet()
            .stream()
            .map(entry -> Attribute.convertToAttribute(entry.getKey(), entry.getValue()))
            .collect(Collectors.toMap(
                Attribute::getName,
                Function.identity()));
    }

    private static ImmutableList<MailAddress> getRecipients(MimeMessage mimeMessage) throws MessagingException {
        return Arrays.stream(mimeMessage.getAllRecipients())
            .map(Throwing.function(MailImpl::castToMailAddress).sneakyThrow())
            .collect(ImmutableList.toImmutableList());
    }

    private static MailAddress getSender(MimeMessage mimeMessage) throws MessagingException {
        Address[] sender = mimeMessage.getFrom();
        Preconditions.checkArgument(sender.length == 1);
        return castToMailAddress(sender[0]);
    }

    private static MailAddress castToMailAddress(Address address) throws AddressException {
        Preconditions.checkArgument(address instanceof InternetAddress);
        return new MailAddress((InternetAddress) address);
    }

    /**
     * Create a unique new primary key name for the given MailObject.
     * Detect if this has been called more than 8 times recursively
     *
     * @param currentName the mail to use as the basis for the new mail name
     * @return a new name
     */
    @VisibleForTesting static String deriveNewName(String currentName) throws MessagingException {
        char separator = '!';
        int loopThreshold = 7;
        int suffixLength = 9;
        int suffixMaxLength = loopThreshold * suffixLength;
        int nameMaxLength = suffixMaxLength + 13;

        detectPossibleLoop(currentName, loopThreshold, separator);

        // Checking if the original mail name is too long, perhaps because of a
        // loop caused by a configuration error.
        // it could cause a "null pointer exception" in AvalonMailRepository
        // much harder to understand.
        String newName = currentName + generateRandomSuffix(suffixLength, separator);
        return stripFirstCharsIfNeeded(nameMaxLength, newName);
    }

    private static String stripFirstCharsIfNeeded(int nameMaxLength, String newName) {
        return newName.substring(Math.max(0, newName.length() - nameMaxLength));
    }

    private static String generateRandomSuffix(int suffixLength, char separator) {
        return "-" + separator + RandomStringUtils.randomNumeric(suffixLength - 2);
    }

    private static void detectPossibleLoop(String currentName, int loopThreshold, char separator) throws MessagingException {
        long occurrences = currentName.chars().filter(c -> Chars.saturatedCast(c) == separator).count();

        // It looks like a configuration loop. It's better to stop.
        if (occurrences > loopThreshold) {
            throw new MessagingException("Unable to create a new message name: too long. Possible loop in config.xml.");
        }
    }


    private static final Logger LOGGER = LoggerFactory.getLogger(MailImpl.class);

    /**
     * We hardcode the serialVersionUID so that from James 1.2 on, MailImpl will
     * be deserializable (so your mail doesn't get lost)
     */
    public static final long serialVersionUID = -4289663364703986260L;
    /**
     * The error message, if any, associated with this mail.
     */
    private String errorMessage;
    /**
     * The state of this mail, which determines how it is processed.
     */
    private String state;
    /**
     * The MimeMessage that holds the mail data.
     */
    private MimeMessageWrapper message;
    /**
     * The sender of this mail.
     */
    private MailAddress sender;
    /**
     * The collection of recipients to whom this mail was sent.
     */
    private Collection<MailAddress> recipients;
    /**
     * The identifier for this mail message
     */
    private String name;
    /**
     * The remote host from which this mail was sent.
     */
    private String remoteHost = "localhost";
    /**
     * The remote address from which this mail was sent.
     */
    private String remoteAddr = "127.0.0.1";
    /**
     * The last time this message was updated.
     */
    private Date lastUpdated = new Date();
    /**
     * Attributes added to this MailImpl instance
     */
    private Map<AttributeName, Attribute> attributes;
    /**
     * Specific headers for some recipients
     * These headers will be added at delivery time
     */
    private PerRecipientHeaders perRecipientSpecificHeaders;

    private MailImpl(String name,
                     String state,
                     Map<AttributeName, Attribute> attributes,
                     List<MailAddress> recipients,
                     PerRecipientHeaders perRecipientHeaders) {
        setName(name);
        setState(state);
        setAttributes(attributes);
        setRecipients(recipients);
        perRecipientSpecificHeaders = perRecipientHeaders;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public MimeMessage getMessage() throws MessagingException {
        return message;
    }

    @Override
    public void setName(String name) {
        Preconditions.checkNotNull(name);
        Preconditions.checkArgument(!name.isEmpty(), "name must not be empty");
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Collection<MailAddress> getRecipients() {
        return recipients;
    }

    @Override
    public MailAddress getSender() {
        return sender;
    }

    @Override
    public String getState() {
        return state;
    }

    @Override
    public String getRemoteHost() {
        return remoteHost;
    }

    @Override
    public String getRemoteAddr() {
        return remoteAddr;
    }

    @Override
    public Date getLastUpdated() {
        return lastUpdated;
    }

    /**
     * <p>
     * Return the size of the message including its headers.
     * MimeMessage.getSize() method only returns the size of the message body.
     * </p>
     * <p/>
     * <p>
     * Note: this size is not guaranteed to be accurate - see Sun's
     * documentation of MimeMessage.getSize().
     * </p>
     *
     * @return approximate size of full message including headers.
     * @throws MessagingException if a problem occurs while computing the message size
     */
    @Override
    public long getMessageSize() throws MessagingException {
        return MimeMessageUtil.getMessageSize(message);
    }

    @Override
    public void setErrorMessage(String msg) {
        this.errorMessage = msg;
    }

    /**
     * Set the MimeMessage associated with this MailImpl.
     *
     * @param message the new MimeMessage associated with this MailImpl
     */
    @Override
    public void setMessage(MimeMessage message) throws MessagingException {
        setMessageNoCopy(new MimeMessageWrapper(message));
    }

    public void setMessageNoCopy(MimeMessageWrapper message) throws MessagingException {
        if (this.message != message) {
            // If a setMessage is called on a Mail that already have a message
            // (discouraged) we have to make sure that the message we remove is
            // correctly unreferenced and disposed, otherwise it will keep locks
            if (this.message != null) {
                LifecycleUtil.dispose(this.message);
            }
            this.message = message;
        }
    }

    public void setMessageContent(MimeMessageSource message) throws MessagingException {
           setMessageNoCopy(new MimeMessageWrapper(message));
    }

    @Override
    public void setRecipients(Collection<MailAddress> recipients) {
        this.recipients = ImmutableList.copyOf(recipients);
    }

    public void setSender(MailAddress sender) {
        this.sender = sender;
    }

    @Override
    public void setState(String state) {
        this.state = state;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    @Override
    public void setLastUpdated(Date lastUpdated) {
        // Make a defensive copy to ensure that the date
        // doesn't get changed external to the class
        if (lastUpdated != null) {
            lastUpdated = new Date(lastUpdated.getTime());
        }
        this.lastUpdated = lastUpdated;
    }

    // Serializable Methods
    // TODO: These need some work. Currently very tightly coupled to
    // the internal representation.

    /**
     * Read the MailImpl from an <code>ObjectInputStream</code>.
     *
     * @param in the ObjectInputStream from which the object is read
     * @throws IOException            if an error occurs while reading from the stream
     * @throws ClassNotFoundException ?
     * @throws ClassCastException     if the serialized objects are not of the appropriate type
     */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            Object obj = in.readObject();
            if (obj == null) {
                sender = null;
            } else if (obj instanceof String) {
                sender = new MailAddress((String) obj);
            } else if (obj instanceof MailAddress) {
                sender = (MailAddress) obj;
            }
        } catch (ParseException pe) {
            throw new IOException("Error parsing sender address: " + pe.getMessage());
        }
        recipients = (Collection<MailAddress>) in.readObject();
        state = (String) in.readObject();
        errorMessage = (String) in.readObject();
        name = (String) in.readObject();
        remoteHost = (String) in.readObject();
        remoteAddr = (String) in.readObject();
        setLastUpdated((Date) in.readObject());
        try {
            setAttributesUsingJsonable(in);
        } catch (Exception e) {
            setAttributesUsingJavaSerializable(in);
        }
        perRecipientSpecificHeaders = (PerRecipientHeaders) in.readObject();
    }

    /**
     * Newest mailet API introduced {@link AttributeValue} which can encapsulate any class, possible not serializable.
     *
     * As such, algorithm relying on out of the box serialization can not handle non serializable attribute values as well as
     * nested AttributeValue.
     *
     * Thus, rather than Java deserializing attributes we deserialize them as Json using AttributeValue capabilities.
     */
    private void setAttributesUsingJsonable(ObjectInputStream in) throws IOException, ClassNotFoundException {
        @SuppressWarnings("unchecked")
        Map<String, String> attributesAsJson = (Map<String, String>) in.readObject();

        this.attributes = attributesAsJson.entrySet().stream()
            .map(Throwing.function(entry -> new Attribute(AttributeName.of(entry.getKey()), AttributeValue.fromJsonString(entry.getValue()))))
            .collect(Collectors.toMap(
                Attribute::getName,
                Function.identity()));
    }

    /**
     * Fallback to Java deserialization if {@link MailImpl#setAttributesUsingJsonable(ObjectInputStream)} fails.
     */
    @SuppressWarnings("unchecked")
    private void setAttributesUsingJavaSerializable(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // the following is under try/catch to be backwards compatible
        // with messages created with James version <= 2.2.0a8
        try {
            setAttributesRaw((Map<String, Object>) in.readObject());
        } catch (OptionalDataException ode) {
            if (ode.eof) {
                attributes = new HashMap<>();
            } else {
                throw ode;
            }
        }
    }

    /**
     * Write the MailImpl to an <code>ObjectOutputStream</code>.
     *
     * @param out the ObjectOutputStream to which the object is written
     * @throws IOException if an error occurs while writing to the stream
     */
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeObject(sender);
        out.writeObject(recipients);
        out.writeObject(state);
        out.writeObject(errorMessage);
        out.writeObject(name);
        out.writeObject(remoteHost);
        out.writeObject(remoteAddr);
        out.writeObject(lastUpdated);
        out.writeObject(getAttributesAsJson());
        out.writeObject(perRecipientSpecificHeaders);
    }

    @Override
    public void dispose() {
        LifecycleUtil.dispose(message);
        message = null;
    }

    /**
     * <p>
     * This method is necessary, when Mail repositories needs to deal explicitly
     * with storing Mail attributes as a Serializable
     * </p>
     * <p>
     * <strong>Note</strong>: This method is not exposed in the Mail interface,
     * it is for internal use by James only.
     * </p>
     *
     * @return Serializable of the entire attributes collection
     * @since 2.2.0
     */
    public Map<String, Object> getAttributesRaw() {
        return attributes.values()
            .stream()
            .collect(Collectors.toMap(
                attribute -> attribute.getName().asString(),
                attribute -> attribute.getValue().value()));
    }

    /**
     * Newly serialized emails are serialized using {@link AttributeValue}.
     *
     * Upon deserialization, fallback to Java deserialization is handled to not introduce retro-compatibility issues.
     */
    private Map<String, String> getAttributesAsJson() {
        return attributes.values()
            .stream()
            .flatMap(entry -> entry.getValue().toJson().map(value -> Pair.of(entry.getName().asString(), value.toString())).stream())
            .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));
    }

    /**
     * <p>
     * This method is necessary, when Mail repositories needs to deal explicitly
     * with retriving Mail attributes as a Serializable
     * </p>
     * <p>
     * <strong>Note</strong>: This method is not exposed in the Mail interface,
     * it is for internal use by James only.
     * </p>
     *
     * @param attr Serializable of the entire attributes collection
     * @since 2.2.0
     */
    public void setAttributesRaw(Map<String, Object> attr) {
        this.attributes = toAttributeMap(attr);
    }

    private void setAttributes(Map<AttributeName, Attribute> attr) {
        this.attributes = Maps.newHashMap(attr);
    }

    @Override
    public Stream<Attribute> attributes() {
        return this.attributes.values().stream();
    }

    @Override
    public Serializable getAttribute(String key) {
        return toSerializable(attributes.get(AttributeName.of(key)));
    }

    @Override
    public Optional<Attribute> getAttribute(AttributeName name) {
        return Optional.ofNullable(attributes.get(name));
    }

    @Override
    public Serializable setAttribute(String key, Serializable object) {
        Preconditions.checkNotNull(key, "Key of an attribute should not be null");
        Attribute attribute = Attribute.convertToAttribute(key, object);
        Attribute previous = attributes.put(attribute.getName(), attribute);

        return toSerializable(previous);
    }

    @Override
    public Optional<Attribute> setAttribute(Attribute attribute) {
        Preconditions.checkNotNull(attribute.getName().asString(), "AttributeName should not be null");
        return Optional.ofNullable(this.attributes.put(attribute.getName(), attribute));
    }

    @Override
    public Serializable removeAttribute(String key) {
        return toSerializable(attributes.remove(AttributeName.of(key)));
    }

    @Override
    public Optional<Attribute> removeAttribute(AttributeName attributeName) {
        Attribute previous = attributes.remove(attributeName);
        return Optional.ofNullable(previous);
    }

    @Override
    public void removeAllAttributes() {
        attributes.clear();
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return attributes.keySet()
            .stream()
            .map(AttributeName::asString)
            .iterator();
    }

    @Override
    public Stream<AttributeName> attributeNames() {
        return attributes().map(Attribute::getName);
    }

    private Serializable toSerializable(Attribute previous) {
        return (Serializable) Optional.ofNullable(previous)
            .map(Attribute::getValue)
            .map(AttributeValue::getValue)
            .orElse(null);
    }

    @Override
    public boolean hasAttributes() {
        return !attributes.isEmpty();
    }

    /**
     * Generate a new identifier/name for a mail being processed by this server.
     *
     * @return the new identifier
     */
    public static String getId() {
        return "Mail" + System.currentTimeMillis() + "-" + UUID.randomUUID();
    }

    @Override
    public PerRecipientHeaders getPerRecipientSpecificHeaders() {
        return perRecipientSpecificHeaders;
    }

    @Override
    public void addSpecificHeaderForRecipient(Header header, MailAddress recipient) {
        perRecipientSpecificHeaders.addHeaderForRecipient(header, recipient);
    }

    public void addAllSpecificHeaderForRecipient(PerRecipientHeaders perRecipientHeaders) {
        perRecipientSpecificHeaders.addAll(perRecipientHeaders);
    }

    @Override
    public Mail duplicate() throws MessagingException {
        return MailImpl.duplicate(this);
    }

    @Override
    public Map<AttributeName, Attribute> attributesMap() {
        return ImmutableMap.copyOf(attributes);
    }
}
