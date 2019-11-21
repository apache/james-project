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
package org.apache.james.jmap.draft.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
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
        return properties.stream().flatMap(MessageProperty::find).collect(Guavate.toImmutableSet());
    }
    
    private ImmutableSet<HeaderProperty> toHeadersProperties(ImmutableSet<String> properties) {
        return properties.stream().flatMap(HeaderProperty::find).collect(Guavate.toImmutableSet());
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

    public ReadLevel computeReadLevel() {
        Stream<ReadLevel> readLevels = Stream.concat(this.buildOutputMessageProperties()
                .stream()
                .map(MessageProperty::getReadLevel),
            headerPropertiesReadLevel());

        // If `null`, all properties will be fetched (JMAP Draft)
        // This defer from RFC-8621 behavior (not implemented here)
        // If omitted, this defaults to: [ "partId", "blobId", "size", "name", "type", "charset", "disposition", "cid",
        // "language", "location" ]
        return readLevels.reduce(ReadLevel::combine)
            .orElse(ReadLevel.Full);

    }

    private Stream<ReadLevel> headerPropertiesReadLevel() {
        return headersProperties.map(collection ->
            collection.stream()
                .map(any -> ReadLevel.Header))
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
        id("id", ReadLevel.Metadata),
        blobId("blobId", ReadLevel.Metadata),
        threadId("threadId", ReadLevel.Metadata),
        mailboxIds("mailboxIds", ReadLevel.Metadata),
        inReplyToMessageId("inReplyToMessageId", ReadLevel.Header),
        isUnread("isUnread", ReadLevel.Metadata),
        isFlagged("isFlagged", ReadLevel.Metadata),
        isAnswered("isAnswered", ReadLevel.Metadata),
        isDraft("isDraft", ReadLevel.Metadata),
        isForwarded("isForwarded", ReadLevel.Metadata),
        hasAttachment("hasAttachment", ReadLevel.Full),
        headers("headers", ReadLevel.Header),
        from("from", ReadLevel.Header),
        to("to", ReadLevel.Header),
        cc("cc", ReadLevel.Header),
        bcc("bcc", ReadLevel.Header),
        replyTo("replyTo", ReadLevel.Header),
        subject("subject", ReadLevel.Header),
        date("date", ReadLevel.Header),
        size("size", ReadLevel.Metadata),
        preview("preview", ReadLevel.Full),
        textBody("textBody", ReadLevel.Full),
        htmlBody("htmlBody", ReadLevel.Full),
        attachments("attachments", ReadLevel.Full),
        attachedMessages("attachedMessages", ReadLevel.Full),
        keywords("keywords", ReadLevel.Metadata),
        body("body", PropertyType.INPUTONLY, ReadLevel.Full);
    
        private final String property;
        private final PropertyType type;
        private final ReadLevel readLevel;

        MessageProperty(String property, ReadLevel readLevel) {
            this(property, PropertyType.INPUTOUTPUT, readLevel);
        }

        MessageProperty(String property, PropertyType type, ReadLevel readLevel) {
            this.property = property;
            this.type = type;
            this.readLevel = readLevel;
        }
    
        @Override
        public String asFieldName() {
            return property;
        }

        public ReadLevel getReadLevel() {
            return readLevel;
        }
    
        public static Stream<MessageProperty> find(String property) {
            Preconditions.checkNotNull(property);
            return Arrays.stream(values()).filter(entry -> entry.property.equals(property));
        }

        public static ImmutableSet<MessageProperty> allOutputProperties() {
            return Arrays.stream(values()).filter(MessageProperty::outputProperty).collect(Guavate.toImmutableSet());
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

    public enum ReadLevel {
        Metadata(0),
        Header(1),
        Full(2);

        static ReadLevel combine(ReadLevel readLevel1, ReadLevel readLevel2) {
            if (readLevel1.priority > readLevel2.priority) {
                return readLevel1;
            }
            return readLevel2;
        }

        private final int priority;

        ReadLevel(int priority) {
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
