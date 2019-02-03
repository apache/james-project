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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.OutputStream;
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

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;

import org.apache.commons.lang.RandomStringUtils;
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
import com.github.steveash.guavate.Guavate;
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
        return new MailImpl(mail, deriveNewName(mail.getName()));
    }

    public static MailImpl fromMimeMessage(String name, MimeMessage mimeMessage) throws MessagingException {
        MailAddress sender = getSender(mimeMessage);
        ImmutableList<MailAddress> recipients = getRecipients(mimeMessage);
        return new MailImpl(name, sender, recipients, mimeMessage);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Optional<MimeMessage> mimeMessage;
        private List<MailAddress> recipients;
        private Optional<String> name;
        private Optional<MailAddress> sender;
        private Optional<String> state;
        private Optional<String> errorMessage;
        private Optional<Date> lastUpdated;
        private Map<AttributeName, Attribute> attributes;
        private Optional<String> remoteAddr;
        private Optional<String> remoteHost;
        private PerRecipientHeaders perRecipientHeaders;

        private Builder() {
            mimeMessage = Optional.empty();
            recipients = Lists.newArrayList();
            name = Optional.empty();
            sender = Optional.empty();
            state = Optional.empty();
            errorMessage = Optional.empty();
            lastUpdated = Optional.empty();
            attributes = Maps.newHashMap();
            remoteAddr = Optional.empty();
            remoteHost = Optional.empty();
            perRecipientHeaders = new PerRecipientHeaders();
        }

        public Builder mimeMessage(MimeMessage mimeMessage) {
            this.mimeMessage = Optional.ofNullable(mimeMessage);
            return this;
        }

        public Builder mimeMessage(MimeMessageBuilder mimeMessage) throws MessagingException {
            this.mimeMessage = Optional.ofNullable(mimeMessage.build());
            return this;
        }

        public Builder recipients() {
            return this;
        }

        public Builder recipients(List<MailAddress> recipients) {
            this.recipients.addAll(recipients);
            return this;
        }

        public Builder recipients(MailAddress... recipients) {
            return recipients(ImmutableList.copyOf(recipients));
        }

        public Builder recipients(String... recipients) {
            return recipients(Arrays.stream(recipients)
                .map(Throwing.function(MailAddress::new))
                .collect(ImmutableList.toImmutableList()));
        }

        public Builder recipient(MailAddress recipient) {
            return recipients(recipient);
        }

        public Builder recipient(String recipient) throws AddressException {
            return recipients(recipient);
        }

        public Builder name(String name) {
            this.name = Optional.ofNullable(name);
            return this;
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
        public Builder attribute(String name, Serializable object) {
            attribute(Attribute.convertToAttribute(name, object));
            return this;
        }

        @Deprecated
        public Builder attributes(Map<String, Serializable> attributes) {
            this.attributes(toAttributeMap(attributes).values());
            return this;
        }

        public Builder attribute(Attribute attribute) {
            this.attributes.put(attribute.getName(), attribute);
            return this;
        }

        public Builder attributes(Collection<Attribute> attributes) {
            this.attributes.putAll(attributes.stream()
                .collect(ImmutableMap.toImmutableMap(
                    Attribute::getName,
                    Function.identity())));
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
            MailImpl mail = new MailImpl();
            mimeMessage.ifPresent(Throwing.consumer(mail::setMessage).sneakyThrow());
            name.ifPresent(mail::setName);
            sender.ifPresent(mail::setSender);
            mail.setRecipients(recipients);
            state.ifPresent(mail::setState);
            errorMessage.ifPresent(mail::setErrorMessage);
            lastUpdated.ifPresent(mail::setLastUpdated);
            mail.setAttributes(attributes);
            remoteAddr.ifPresent(mail::setRemoteAddr);
            remoteHost.ifPresent(mail::setRemoteHost);
            mail.perRecipientSpecificHeaders.addAll(perRecipientHeaders);
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
    private MimeMessageCopyOnWriteProxy message;
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

    /**
     * A constructor that creates a new, uninitialized MailImpl
     */
    public MailImpl() {
        setState(Mail.DEFAULT);
        attributes = new HashMap<>();
        perRecipientSpecificHeaders = new PerRecipientHeaders();
        this.recipients = null;
    }

    /**
     * A constructor that creates a MailImpl with the specified name, sender,
     * and recipients.
     *
     * @param name       the name of the MailImpl
     * @param sender     the sender for this MailImpl
     * @param recipients the collection of recipients of this MailImpl
     */
    public MailImpl(String name, MailAddress sender, Collection<MailAddress> recipients) {
        this(name, Optional.ofNullable(sender), recipients);
    }

    public MailImpl(String name, Optional<MailAddress> sender, Collection<MailAddress> recipients) {
        this();
        setName(name);
        sender.ifPresent(this::setSender);

        // Copy the recipient list
        if (recipients != null) {
            setRecipients(recipients);
        }
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    private MailImpl(Mail mail, String newName) throws MessagingException {
        this(newName, mail.getSender(), mail.getRecipients(), mail.getMessage());
        setRemoteHost(mail.getRemoteHost());
        setRemoteAddr(mail.getRemoteAddr());
        setLastUpdated(mail.getLastUpdated());
        setErrorMessage(mail.getErrorMessage());
        try {
            if (mail instanceof MailImpl) {
                setAttributesRaw((Map<String, Object>) cloneSerializableObject(((MailImpl) mail).getAttributesRaw()));
            } else {
                ImmutableMap<String, Object> attributesMap = mail.attributes()
                    .collect(Guavate.toImmutableMap(
                            attribute -> attribute.getName().asString(),
                            Throwing.function(attribute -> cloneSerializableObject(attribute.getValue().getValue()))));

                setAttributesRaw(attributesMap);
            }
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.error("Error while deserializing attributes", e);
            setAttributesRaw(new HashMap<>());
        }
    }

    /**
     * A constructor that creates a MailImpl with the specified name, sender,
     * recipients, and message data.
     *
     * @param name       the name of the MailImpl
     * @param sender     the sender for this MailImpl
     * @param recipients the collection of recipients of this MailImpl
     * @param messageIn  a stream containing the message source
     */
    public MailImpl(String name, MailAddress sender, Collection<MailAddress> recipients, InputStream messageIn) throws MessagingException {
        this(name, sender, recipients);
        MimeMessageSource source = new MimeMessageInputStreamSource(name, messageIn);
        // if MimeMessageCopyOnWriteProxy throws an error in the constructor we
        // have to manually care disposing our source.
        try {
            this.setMessage(new MimeMessageCopyOnWriteProxy(source));
        } catch (MessagingException e) {
            LifecycleUtil.dispose(source);
            throw e;
        }
    }

    /**
     * A constructor that creates a MailImpl with the specified name, sender,
     * recipients, and MimeMessage.
     */
    public MailImpl(String name, MailAddress sender, Collection<MailAddress> recipients, MimeMessage message) throws MessagingException {
        this(name, sender, recipients);
        this.setMessage(new MimeMessageCopyOnWriteProxy(message));
    }

    /**
     * Duplicate the MailImpl, replacing the mail name with the one passed in as
     * an argument.
     *
     * @param newName the name for the duplicated mail
     * @return a MailImpl that is a duplicate of this one with a different name
     */
    @VisibleForTesting Mail duplicate(String newName) {
        try {
            return new MailImpl(this, newName);
        } catch (MessagingException me) {
            // Ignored. Return null in the case of an error.
        }
        return null;
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

        // TODO: We should use the MimeMessageCopyOnWriteProxy
        // everytime we set the MimeMessage. We should
        // investigate if we should wrap it here

        if (this.message != message) {
            // If a setMessage is called on a Mail that already have a message
            // (discouraged) we have to make sure that the message we remove is
            // correctly unreferenced and disposed, otherwise it will keep locks
            if (this.message != null) {
                LifecycleUtil.dispose(this.message);
            }
            if (message instanceof MimeMessageCopyOnWriteProxy) {
                this.message = (MimeMessageCopyOnWriteProxy) message;
            } else {
                this.message = new MimeMessageCopyOnWriteProxy(message);
            }
        }
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

    /**
     * Writes the message out to an OutputStream.
     *
     * @param out the OutputStream to which to write the content
     * @throws MessagingException if the MimeMessage is not set for this MailImpl
     * @throws IOException        if an error occurs while reading or writing from the stream
     */
    public void writeMessageTo(OutputStream out) throws IOException, MessagingException {
        if (message != null) {
            message.writeTo(out);
        } else {
            throw new MessagingException("No message set for this MailImpl.");
        }
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
        perRecipientSpecificHeaders = (PerRecipientHeaders) in.readObject();
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
        out.writeObject(getAttributesRaw());
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
     * This methods provide cloning for serializable objects. Mail Attributes
     * are Serializable but not Clonable so we need a deep copy
     *
     * @param o Object to be cloned
     * @return the cloned Object
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private static Object cloneSerializableObject(Object o) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(b)) {
            out.writeObject(o);
            out.flush();
        }
        ByteArrayInputStream bi = new ByteArrayInputStream(b.toByteArray());
        try (ObjectInputStream in = new ObjectInputStream(bi)) {
            return in.readObject();
        }
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
}
