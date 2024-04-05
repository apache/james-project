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

package org.apache.james.jmap.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class MessageProperties {

    public static final ImmutableSet<MessageProperty> MANDATORY_PROPERTIES = ImmutableSet.of(MessageProperty.id);

    private final Optional<ImmutableSet<MessageProperty>> messageProperties;
    private final Optional<ImmutableSet<HeaderProperty>> headersProperties;

    public MessageProperties(Optional<ImmutableSet<String>> properties) {
        this.messageProperties = properties.map(this::toMessageProperties);
        this.headersProperties = properties.map(this::toHeadersProperties);
    }

    private MessageProperties(Optional<ImmutableSet<MessageProperty>> messageProperties,
                              Optional<ImmutableSet<HeaderProperty>> headersProperties) {
        this.messageProperties = messageProperties;
        this.headersProperties = headersProperties;
    }

    private ImmutableSet<MessageProperty> toMessageProperties(ImmutableSet<String> properties) {
        return properties.stream().flatMap(MessageProperty::find).collect(ImmutableSet.toImmutableSet());
    }

    private ImmutableSet<HeaderProperty> toHeadersProperties(ImmutableSet<String> properties) {
        return properties.stream().flatMap(HeaderProperty::find).collect(ImmutableSet.toImmutableSet());
    }

    public Optional<ImmutableSet<HeaderProperty>> getOptionalHeadersProperties() {
        return headersProperties;
    }

    public Optional<ImmutableSet<MessageProperty>> getOptionalMessageProperties() {
        return messageProperties;
    }

    public MessageProperties toOutputProperties() {
        return this.ensureContains(MANDATORY_PROPERTIES)
                .selectBody()
                .overrideHeadersFilteringOnHeadersMessageProperty()
                .ensureHeadersMessageProperty();
    }

    public ReadProfile computeReadLevel() {
        Stream<ReadProfile> readLevels = Stream.concat(this.buildOutputMessageProperties()
                .stream()
                .map(MessageProperty::getReadProfile),
            headerPropertiesReadLevel());

        // If `null`, all properties will be fetched (JMAP Draft)
        // This defer from RFC-8621 behavior (not implemented here)
        // If omitted, this defaults to: [ "partId", "blobId", "size", "name", "type", "charset", "disposition", "cid",
        // "language", "location" ]
        return readLevels.reduce(ReadProfile::combine)
            .orElse(ReadProfile.Full);
    }

    public Stream<String> asFieldList() {
        return Stream.concat(
            messageProperties.stream()
                .flatMap(Collection::stream)
                .map(MessageProperty::asFieldName),
            headersProperties.stream()
                .flatMap(Collection::stream)
                .map(HeaderProperty::asFieldName));
    }

    private Stream<ReadProfile> headerPropertiesReadLevel() {
        return headersProperties.map(collection ->
            collection.stream()
                .map(any -> ReadProfile.Header))
            .orElse(Stream.of());
    }

    private ImmutableSet<MessageProperty> buildOutputMessageProperties() {
        return this.messageProperties.orElseGet(MessageProperty::allOutputProperties);
    }

    private MessageProperties usingProperties(Sets.SetView<MessageProperty> properties) {
        return new MessageProperties(
            Optional.of(properties.immutableCopy()),
            headersProperties);
    }

    private MessageProperties ensureContains(ImmutableSet<MessageProperty> mandatoryFields) {
        return usingProperties(Sets.union(buildOutputMessageProperties(), mandatoryFields));
    }

    private MessageProperties selectBody() {
        ImmutableSet<MessageProperty> messageProperties = buildOutputMessageProperties();
        if (messageProperties.contains(MessageProperty.body)) {
            return usingProperties(
                Sets.difference(
                    Sets.union(messageProperties, ImmutableSet.of(MessageProperty.textBody)),
                    ImmutableSet.of(MessageProperty.body)));
        }
        return this;
    }

    private MessageProperties ensureHeadersMessageProperty() {
        if (headersProperties.isPresent() && !headersProperties.get().isEmpty()) {
            return usingProperties(Sets.union(
                buildOutputMessageProperties(),
                ImmutableSet.of(MessageProperty.headers)));
        }
        return this;
    }

    private MessageProperties overrideHeadersFilteringOnHeadersMessageProperty() {
        if (buildOutputMessageProperties().contains(MessageProperty.headers)) {
            return new MessageProperties(messageProperties, Optional.empty());
        }
        return this;
    }

    private enum PropertyType {
        INPUTONLY,
        INPUTOUTPUT
    }

    public enum MessageProperty implements Property {
        id("id", ReadProfile.Metadata),
        blobId("blobId", ReadProfile.Metadata),
        threadId("threadId", ReadProfile.Metadata),
        mailboxIds("mailboxIds", ReadProfile.Metadata),
        inReplyToMessageId("inReplyToMessageId", ReadProfile.Header),
        isUnread("isUnread", ReadProfile.Metadata),
        isFlagged("isFlagged", ReadProfile.Metadata),
        isAnswered("isAnswered", ReadProfile.Metadata),
        isDraft("isDraft", ReadProfile.Metadata),
        isForwarded("isForwarded", ReadProfile.Metadata),
        hasAttachment("hasAttachment", ReadProfile.Fast),
        headers("headers", ReadProfile.Header),
        from("from", ReadProfile.Header),
        to("to", ReadProfile.Header),
        cc("cc", ReadProfile.Header),
        bcc("bcc", ReadProfile.Header),
        replyTo("replyTo", ReadProfile.Header),
        subject("subject", ReadProfile.Header),
        date("date", ReadProfile.Header),
        size("size", ReadProfile.Metadata),
        preview("preview", ReadProfile.Fast),
        textBody("textBody", ReadProfile.Full),
        htmlBody("htmlBody", ReadProfile.Full),
        attachments("attachments", ReadProfile.Full),
        attachedMessages("attachedMessages", ReadProfile.Full),
        keywords("keywords", ReadProfile.Metadata),
        body("body", PropertyType.INPUTONLY, ReadProfile.Full);

        private final String property;
        private final PropertyType type;
        private final ReadProfile readProfile;

        MessageProperty(String property, ReadProfile readProfile) {
            this(property, PropertyType.INPUTOUTPUT, readProfile);
        }

        MessageProperty(String property, PropertyType type, ReadProfile readProfile) {
            this.property = property;
            this.type = type;
            this.readProfile = readProfile;
        }

        @Override
        public String asFieldName() {
            return property;
        }

        public ReadProfile getReadProfile() {
            return readProfile;
        }

        private static final ImmutableMap<String, MessageProperty> LOOKUP_MAP = Arrays.stream(values())
            .collect(ImmutableMap.toImmutableMap(v -> v.property, Function.identity()));

        public static Stream<MessageProperty> find(String property) {
            Preconditions.checkNotNull(property);
            return Optional.ofNullable(LOOKUP_MAP.get(property)).stream();
        }

        public static ImmutableSet<MessageProperty> allOutputProperties() {
            return Arrays.stream(values()).filter(MessageProperty::outputProperty).collect(ImmutableSet.toImmutableSet());
        }

        private static boolean outputProperty(MessageProperty p) {
            switch (p.type) {
                case INPUTONLY:
                    return false;
                case INPUTOUTPUT:
                    return true;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    public enum ReadProfile {
        Metadata(0),
        Header(1),
        Fast(2),
        Full(3);

        public static ReadProfile combine(ReadProfile readProfile1, ReadProfile readProfile2) {
            if (readProfile1.priority > readProfile2.priority) {
                return readProfile1;
            }
            return readProfile2;
        }

        private final int priority;

        ReadProfile(int priority) {
            this.priority = priority;
        }
    }

    public static class HeaderProperty implements Property {

        public static final String HEADER_PROPERTY_PREFIX = "headers.";

        public static HeaderProperty fromFieldName(String field) {
            Preconditions.checkArgument(!isMessageHeaderProperty(field));
            return new HeaderProperty(field.toLowerCase(Locale.US));
        }

        public static HeaderProperty valueOf(String property) {
            Preconditions.checkArgument(isMessageHeaderProperty(property));
            return new HeaderProperty(stripPrefix(property).toLowerCase(Locale.US));
        }

        private static String stripPrefix(String property) {
            return property.substring(HEADER_PROPERTY_PREFIX.length());
        }

        public static boolean isMessageHeaderProperty(String property) {
            Preconditions.checkNotNull(property);
            return property.startsWith(HEADER_PROPERTY_PREFIX);
        }

        public static Stream<HeaderProperty> find(String property) {
            if (isMessageHeaderProperty(property)) {
                return Stream.of(valueOf(property));
            } else {
                return Stream.of();
            }
        }

        private String fieldName;

        private HeaderProperty(String fieldName) {
            this.fieldName = fieldName;
        }

        @Override
        public String asFieldName() {
            return fieldName;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof HeaderProperty) {
                HeaderProperty other = (HeaderProperty) obj;
                return Objects.equals(this.fieldName, other.fieldName);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(fieldName);
        }

        @Override
        public String toString() {
            return Objects.toString(fieldName);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("headersProperties", headersProperties)
            .add("messageProperties", messageProperties)
            .toString();
    }
}
