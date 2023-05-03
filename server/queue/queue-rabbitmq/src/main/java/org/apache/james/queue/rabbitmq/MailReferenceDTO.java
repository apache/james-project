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
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingBiConsumer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class MailReferenceDTO {

    static MailReferenceDTO fromMailReference(MailReference mailReference) {
        Mail mail = mailReference.getMail();
        MimeMessagePartsId partsId = mailReference.getPartsId();

        return new MailReferenceDTO(
            mailReference.getEnqueueId().serialize(),
            Optional.ofNullable(mail.getRecipients()).map(Collection::stream)
                .orElse(Stream.empty())
                .map(MailAddress::asString)
                .collect(ImmutableList.toImmutableList()),
            mail.getName(),
            mail.getMaybeSender().asOptional().map(MailAddress::asString),
            mail.getState(),
            mail.getErrorMessage(),
            Optional.ofNullable(mail.getLastUpdated()).map(Date::toInstant),
            serializedAttributes(mail),
            mail.getRemoteAddr(),
            mail.getRemoteHost(),
            fromPerRecipientHeaders(mail.getPerRecipientSpecificHeaders()),
            partsId.getHeaderBlobId().asString(),
            partsId.getBodyBlobId().asString());
    }

    private static Map<String, HeadersDto> fromPerRecipientHeaders(PerRecipientHeaders perRecipientHeaders) {
        return perRecipientHeaders.getHeadersByRecipient()
            .asMap()
            .entrySet()
            .stream()
            .collect(ImmutableMap.toImmutableMap(
                entry -> entry.getKey().asString(),
                entry -> HeadersDto.from(entry.getValue())));
    }

    private static ImmutableMap<String, String> serializedAttributes(Mail mail) {
        return mail.attributes()
            .flatMap(attribute -> attribute.getValue().toJson().map(JsonNode::toString).map(value -> Pair.of(attribute.getName().asString(), value)).stream())
            .collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight));
    }

    private final String enqueueId;
    private final ImmutableList<String> recipients;
    private final String name;
    private final Optional<String> sender;
    private final String state;
    private final String errorMessage;
    private final Optional<Instant> lastUpdated;
    private final ImmutableMap<String, String> attributes;
    private final String remoteAddr;
    private final String remoteHost;
    private final Map<String, HeadersDto> perRecipientHeaders;
    private final String headerBlobId;
    private final String bodyBlobId;

    @JsonCreator
    private MailReferenceDTO(@JsonProperty("enqueueId") String enqueueId,
                             @JsonProperty("recipients") ImmutableList<String> recipients,
                             @JsonProperty("name") String name,
                             @JsonProperty("sender") Optional<String> sender,
                             @JsonProperty("state") String state,
                             @JsonProperty("errorMessage") String errorMessage,
                             @JsonProperty("lastUpdated") Optional<Instant> lastUpdated,
                             @JsonProperty("attributes") ImmutableMap<String, String> attributes,
                             @JsonProperty("remoteAddr") String remoteAddr,
                             @JsonProperty("remoteHost") String remoteHost,
                             @JsonProperty("perRecipientHeaders") Map<String, HeadersDto> perRecipientHeaders,
                             @JsonProperty("headerBlobId") String headerBlobId,
                             @JsonProperty("bodyBlobId") String bodyBlobId) {
        this.enqueueId = enqueueId;
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

    @JsonProperty("enqueueId")
    public String getEnqueueId() {
        return enqueueId;
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
    Optional<String> getSender() {
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
    Optional<Instant> getLastUpdated() {
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
    Map<String, HeadersDto>  getPerRecipientHeaders() {
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

    MailReference toMailReference(BlobId.Factory blobIdFactory) {
        MimeMessagePartsId messagePartsId = MimeMessagePartsId.builder()
            .headerBlobId(blobIdFactory.from(headerBlobId))
            .bodyBlobId(blobIdFactory.from(bodyBlobId))
            .build();

        return new MailReference(EnqueueId.ofSerialized(enqueueId), mailMetadata(), messagePartsId);
    }

    private MailImpl mailMetadata() {
        MailImpl.Builder builder = MailImpl.builder()
            .name(name)
            .sender(sender.map(MaybeSender::getMailSender).orElse(MaybeSender.nullSender()))
            .addRecipients(recipients.stream()
                .map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow())
                .collect(ImmutableList.toImmutableList()))
            .errorMessage(errorMessage)
            .remoteAddr(remoteAddr)
            .remoteHost(remoteHost)
            .state(state);

        lastUpdated
            .map(Instant::toEpochMilli)
            .map(Date::new)
            .ifPresent(builder::lastUpdated);

        ThrowingBiConsumer<String, String> attributeSetter = (name, value) ->
            builder.addAttribute(new Attribute(AttributeName.of(name), AttributeValue.fromJsonString(value)));

        attributes
            .forEach(Throwing.biConsumer(attributeSetter).sneakyThrow());

        builder.addAllHeadersForRecipients(retrievePerRecipientHeaders());

        return builder.build();
    }

    private PerRecipientHeaders retrievePerRecipientHeaders() {
        PerRecipientHeaders perRecipientHeaders = new PerRecipientHeaders();
        this.perRecipientHeaders.entrySet()
            .stream()
            .flatMap(entry -> entry.getValue().toHeaders().stream()
                .map(Throwing.function(header -> Pair.of(new MailAddress(entry.getKey()), header))))
            .forEach(pair -> perRecipientHeaders.addHeaderForRecipient(pair.getValue(), pair.getKey()));
        return perRecipientHeaders;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailReferenceDTO) {
            MailReferenceDTO mailDTO = (MailReferenceDTO) o;

            return Objects.equals(this.enqueueId, mailDTO.enqueueId)
                && Objects.equals(this.recipients, mailDTO.recipients)
                && Objects.equals(this.name, mailDTO.name)
                && Objects.equals(this.sender, mailDTO.sender)
                && Objects.equals(this.state, mailDTO.state)
                && Objects.equals(this.errorMessage, mailDTO.errorMessage)
                && Objects.equals(this.lastUpdated, mailDTO.lastUpdated)
                && Objects.equals(this.attributes, mailDTO.attributes)
                && Objects.equals(this.remoteAddr, mailDTO.remoteAddr)
                && Objects.equals(this.remoteHost, mailDTO.remoteHost)
                && Objects.equals(this.perRecipientHeaders, mailDTO.perRecipientHeaders)
                && Objects.equals(this.headerBlobId, mailDTO.headerBlobId)
                && Objects.equals(this.bodyBlobId, mailDTO.bodyBlobId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(enqueueId, recipients, name, sender, state, errorMessage, lastUpdated, attributes, remoteAddr, remoteHost, perRecipientHeaders, headerBlobId, bodyBlobId);
    }
}
