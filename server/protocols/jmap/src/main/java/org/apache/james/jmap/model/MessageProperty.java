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

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.apache.james.util.streams.Collectors;

import com.google.common.base.Preconditions;

public class MessageProperty implements Property {
    public static MessageProperty id = valueOf("id");
    public static MessageProperty blobId = valueOf("blobId");
    public static MessageProperty threadId = valueOf("threadId");
    public static MessageProperty mailboxIds = valueOf("mailboxIds");
    public static MessageProperty inReplyToMessageId = valueOf("inReplyToMessageId");
    public static MessageProperty isUnread = valueOf("isUnread");
    public static MessageProperty isFlagged = valueOf("isFlagged");
    public static MessageProperty isAnswered = valueOf("isAnswered");
    public static MessageProperty isDraft = valueOf("isDraft");
    public static MessageProperty hasAttachment = valueOf("hasAttachment");
    public static MessageProperty headers = valueOf("headers");
    public static MessageProperty from = valueOf("from");
    public static MessageProperty to = valueOf("to");
    public static MessageProperty cc = valueOf("cc");
    public static MessageProperty bcc = valueOf("bcc");
    public static MessageProperty replyTo = valueOf("replyTo");
    public static MessageProperty subject = valueOf("subject");
    public static MessageProperty date = valueOf("date");
    public static MessageProperty size = valueOf("size");
    public static MessageProperty preview = valueOf("preview");
    public static MessageProperty textBody = valueOf("textBody");
    public static MessageProperty htmlBody = valueOf("htmlBody");
    public static MessageProperty attachments = valueOf("attachments");
    public static MessageProperty attachedMessages = valueOf("attachedMessages");
    public static MessageProperty body = valueOf("body");
    
    private static final String HEADER_PROPERTY_PREFIX = "headers.";
    
    private final String property;
    
    private MessageProperty(String property) {
        this.property = property.toLowerCase(Locale.US);
    }

    public static MessageProperty valueOf(String property) {
        return new MessageProperty(property);
    }
    
    public static MessageProperty headerValueOf(String headerProperty) {
        Preconditions.checkNotNull(headerProperty);
        return new MessageProperty(HEADER_PROPERTY_PREFIX + headerProperty);
    }
    
    public static Set<MessageProperty> selectHeadersProperties(Set<MessageProperty> properties) {
        return properties.stream()
                .filter(MessageProperty::isHeaderProperty)
                .collect(Collectors.toImmutableSet());
    }

    @Override
    public String asFieldName() {
        return property;
    }
    
    public boolean isHeaderProperty() {
        return property.startsWith(HEADER_PROPERTY_PREFIX);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageProperty) {
            MessageProperty other = (MessageProperty) obj;
            return Objects.equals(this.property, other.property);
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(property);
    }
    
    @Override
    public String toString() {
        return Objects.toString(property);
    }
}
