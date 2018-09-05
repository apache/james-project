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

package org.apache.james.queue.rabbitmq;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.core.MailAddress;
import org.apache.james.util.SerializationUtil;
import org.apache.james.util.streams.Iterators;
import org.apache.mailet.Mail;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class MailDTO {

    static MailDTO fromMail(Mail mail, MimeMessagePartsId partsId) {
        return new MailDTO(
            mail.getRecipients().stream()
                .map(MailAddress::asString)
                .collect(Guavate.toImmutableList()),
            mail.getName(),
            mail.getSender().asString(),
            mail.getState(),
            mail.getErrorMessage(),
            mail.getLastUpdated().toInstant(),
            serializedAttributes(mail),
            mail.getRemoteAddr(),
            mail.getRemoteHost(),
            SerializationUtil.serialize(mail.getPerRecipientSpecificHeaders()),
            partsId.getHeaderBlobId().asString(),
            partsId.getBodyBlobId().asString());
    }

    private static ImmutableMap<String, String> serializedAttributes(Mail mail) {
        return Iterators.toStream(mail.getAttributeNames())
            .collect(Guavate.toImmutableMap(
                name -> name,
                name -> SerializationUtil.serialize(mail.getAttribute(name))));
    }

    private final ImmutableList<String> recipients;
    private final String name;
    private final String sender;
    private final String state;
    private final String errorMessage;
    private final Instant lastUpdated;
    private final ImmutableMap<String, String> attributes;
    private final String remoteAddr;
    private final String remoteHost;
    private final String perRecipientHeaders;
    private final String headerBlobId;
    private final String bodyBlobId;

    @JsonCreator
    private MailDTO(@JsonProperty("recipients") ImmutableList<String> recipients,
                    @JsonProperty("name") String name,
                    @JsonProperty("sender") String sender,
                    @JsonProperty("state") String state,
                    @JsonProperty("errorMessage") String errorMessage,
                    @JsonProperty("lastUpdated") Instant lastUpdated,
                    @JsonProperty("attributes") ImmutableMap<String, String> attributes,
                    @JsonProperty("remoteAddr") String remoteAddr,
                    @JsonProperty("remoteHost") String remoteHost,
                    @JsonProperty("perRecipientHeaders") String perRecipientHeaders,
                    @JsonProperty("headerBlobId") String headerBlobId,
                    @JsonProperty("bodyBlobId") String bodyBlobId) {
        this.recipients = recipients;
        this.name = name;
        this.sender = sender;
        this.state = state;
        this.errorMessage = errorMessage;
        this.lastUpdated = lastUpdated;
        this.attributes = attributes;
        this.remoteAddr = remoteAddr;
        this.remoteHost = remoteHost;
        this.perRecipientHeaders = perRecipientHeaders;
        this.headerBlobId = headerBlobId;
        this.bodyBlobId = bodyBlobId;
    }

    @JsonProperty("recipients")
    Collection<String> getRecipients() {
        return recipients;
    }

    @JsonProperty("name")
    String getName() {
        return name;
    }

    @JsonProperty("sender")
    String getSender() {
        return sender;
    }

    @JsonProperty("state")
    String getState() {
        return state;
    }

    @JsonProperty("errorMessage")
    String getErrorMessage() {
        return errorMessage;
    }

    @JsonProperty("lastUpdated")
    Instant getLastUpdated() {
        return lastUpdated;
    }

    @JsonProperty("attributes")
    Map<String, String> getAttributes() {
        return attributes;
    }

    @JsonProperty("remoteAddr")
    String getRemoteAddr() {
        return remoteAddr;
    }

    @JsonProperty("remoteHost")
    String getRemoteHost() {
        return remoteHost;
    }

    @JsonProperty("perRecipientHeaders")
    String getPerRecipientHeaders() {
        return perRecipientHeaders;
    }

    @JsonProperty("headerBlobId")
    String getHeaderBlobId() {
        return headerBlobId;
    }

    @JsonProperty("bodyBlobId")
    String getBodyBlobId() {
        return bodyBlobId;
    }
}
