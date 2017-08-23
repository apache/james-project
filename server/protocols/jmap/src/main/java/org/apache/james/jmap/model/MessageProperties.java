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
        id("id"),
        blobId("blobId"),
        threadId("threadId"),
        mailboxIds("mailboxIds"),
        inReplyToMessageId("inReplyToMessageId"),
        isUnread("isUnread"),
        isFlagged("isFlagged"),
        isAnswered("isAnswered"),
        isDraft("isDraft"),
        hasAttachment("hasAttachment"),
        headers("headers"),
        from("from"),
        to("to"),
        cc("cc"),
        bcc("bcc"),
        replyTo("replyTo"),
        subject("subject"),
        date("date"),
        size("size"),
        preview("preview"),
        textBody("textBody"),
        htmlBody("htmlBody"),
        attachments("attachments"),
        attachedMessages("attachedMessages"),
        body("body", PropertyType.INPUTONLY);
    
        private final String property;
        private final PropertyType type;

        MessageProperty(String property) {
            this(property, PropertyType.INPUTOUTPUT);
        }

        MessageProperty(String property, PropertyType type) {
            this.property = property;
            this.type = type;
        }
    
        @Override
        public String asFieldName() {
            return property;
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
