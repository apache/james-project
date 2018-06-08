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

package org.apache.james.webadmin.dto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.james.util.mime.MessageContentExtractor.MessageContent;
import org.apache.james.util.streams.Iterators;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.Multimap;

public class MailDto {
    public static MailDto fromMail(Mail mail, List<AdditionalField> additionalFields) throws MessagingException, InnaccessibleFieldException {
        return new MailDto(mail.getName(),
            Optional.ofNullable(mail.getSender()).map(MailAddress::asString),
            mail.getRecipients().stream().map(MailAddress::asString).collect(Guavate.toImmutableList()),
            Optional.ofNullable(mail.getErrorMessage()),
            Optional.ofNullable(mail.getState()),
            Optional.ofNullable(mail.getRemoteHost()),
            Optional.ofNullable(mail.getRemoteAddr()),
            Optional.ofNullable(mail.getLastUpdated()),
            fetchAttributes(additionalFields, mail),
            fetchPerRecipientsHeaders(additionalFields, mail),
            fetchHeaders(additionalFields, mail),
            fetchBody(additionalFields, mail),
            fetchMessageSize(additionalFields, mail));
    }

    private static Optional<Long> fetchMessageSize(List<AdditionalField> additionalFields, Mail mail) throws InnaccessibleFieldException {
        if (!additionalFields.contains(AdditionalField.MESSAGE_SIZE)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Long(mail.getMessageSize()));
        } catch (MessagingException e) {
            throw new InnaccessibleFieldException(AdditionalField.MESSAGE_SIZE, e);
        }
    }

    private static Optional<String> fetchBody(List<AdditionalField> additionalFields, Mail mail) throws InnaccessibleFieldException {
        if (!additionalFields.contains(AdditionalField.BODY)) {
            return Optional.empty();
        }

        try {
            MessageContentExtractor extractor = new MessageContentExtractor();
            return Optional.ofNullable(mail.getMessage())
                    .map(Throwing.function(MailDto::convertMessage).sneakyThrow())
                    .map(Throwing.function((Message message) -> extractor.extract(message)).sneakyThrow())
                    .flatMap(extractBody());
        } catch (MessagingException e) {
            throw new InnaccessibleFieldException(AdditionalField.BODY, e);
        }
    }

    private static Function<? super MessageContent, Optional<String>> extractBody() {
        return (content) -> {
            Optional<String> body = content.getTextBody();
            if (body.isPresent()) {
                return body;
            } else {
                return content.getHtmlBody();
            }
        };
    }

    private static Message convertMessage(MimeMessage message) throws IOException, MessagingException {
        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        message.writeTo(rawMessage);
        return Message.Builder
                .of()
                .use(MimeConfig.PERMISSIVE)
                .parse(new ByteArrayInputStream(rawMessage.toByteArray()))
                .build();
    }

    private static Optional<HeadersDto> fetchHeaders(List<AdditionalField> additionalFields, Mail mail) throws InnaccessibleFieldException {
        if (!additionalFields.contains(AdditionalField.HEADERS)) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(mail.getMessage())
                    .map(Throwing.function((MimeMessage message) -> {
                        HeadersDto headers = new HeadersDto();
                        Collections
                            .list(message.getAllHeaders())
                            .forEach((header) -> headers.add(header.getName(), header.getValue()));
                        return headers;
                    }).sneakyThrow());
        } catch (MessagingException e) {
            throw new InnaccessibleFieldException(AdditionalField.HEADERS, e);
        }
    }

    private static Optional<Map<String, HeadersDto>> fetchPerRecipientsHeaders(List<AdditionalField> additionalFields, Mail mail) {
        if (!additionalFields.contains(AdditionalField.PER_RECIPIENTS_HEADERS)) {
            return Optional.empty();
        }
        Map<String, HeadersDto> headers = new HashMap<>();
        PerRecipientHeaders specificHeaders = mail.getPerRecipientSpecificHeaders();
        Multimap<MailAddress, PerRecipientHeaders.Header> headersByRecipient = specificHeaders.getHeadersByRecipient();

        for (MailAddress address : headersByRecipient.keySet()) {
            headers.put(address.asString(), fetchPerRecipientHeader(headersByRecipient, address));
        }

        return Optional.of(headers);
    }

    private static HeadersDto fetchPerRecipientHeader(
            Multimap<MailAddress, PerRecipientHeaders.Header> headersByRecipient,
            MailAddress address) {
        HeadersDto header = new HeadersDto();
        headersByRecipient.get(address).forEach((rawHeader) -> header.add(rawHeader.getName(), rawHeader.getValue()));
        return header;
    }

    private static Optional<Map<String, String>> fetchAttributes(List<AdditionalField> additionalFields, Mail mail) {
        if (!additionalFields.contains(AdditionalField.ATTRIBUTES)) {
            return Optional.empty();
        }

        return Optional.of(Iterators.toStream(mail.getAttributeNames())
                                    .collect(Guavate.toImmutableMap(attributeName -> attributeName,
                                                                    attributeName -> mail.getAttribute(attributeName).toString())));
    }

    private final String name;
    private final Optional<String> sender;
    private final List<String> recipients;
    private final Optional<String> error;
    private final Optional<String> state;
    private final Optional<String> remoteHost;
    private final Optional<String> remoteAddr;
    private final Optional<Date> lastUpdated;
    private final Optional<Map<String, String>> attributes;
    private final Optional<Map<String, HeadersDto>> perRecipientsHeaders;
    private final Optional<HeadersDto> headers;
    private final Optional<String> body;
    private final Optional<Long> messageSize;

    public enum AdditionalField {
        ATTRIBUTES("attributes"),
        PER_RECIPIENTS_HEADERS("perRecipientsHeaders"),
        BODY("body"),
        HEADERS("headers"),
        MESSAGE_SIZE("messageSize");

        public static Optional<AdditionalField> find(String fieldName) {
            return Arrays.stream(values())
                .filter(value -> value.fieldName.equalsIgnoreCase(fieldName))
                .findAny();
        }

        private final String fieldName;

        AdditionalField(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getName() {
            return fieldName;
        }
    }

    private MailDto(String name, Optional<String> sender, List<String> recipients, Optional<String> error,
            Optional<String> state, Optional<String> remoteHost, Optional<String> remoteAddr,
            Optional<Date> lastUpdated, Optional<Map<String, String>> attributes,
            Optional<Map<String, HeadersDto>> perRecipientsHeaders,
            Optional<HeadersDto> headers, Optional<String> body, Optional<Long> messageSize) {
        this.name = name;
        this.sender = sender;
        this.recipients = recipients;
        this.error = error;
        this.state = state;
        this.remoteHost = remoteHost;
        this.remoteAddr = remoteAddr;
        this.lastUpdated = lastUpdated;
        this.attributes = attributes;
        this.perRecipientsHeaders = perRecipientsHeaders;
        this.headers = headers;
        this.body = body;
        this.messageSize = messageSize;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getSender() {
        return sender;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public Optional<String> getError() {
        return error;
    }

    public Optional<String> getState() {
        return state;
    }

    public Optional<String> getRemoteHost() {
        return remoteHost;
    }

    public Optional<String> getRemoteAddr() {
        return remoteAddr;
    }

    public Optional<Date> getLastUpdated() {
        return lastUpdated;
    }

    public Optional<Map<String, String>> getAttributes() {
        return attributes;
    }

    public Optional<Map<String, HeadersDto>> getPerRecipientsHeaders() {
        return perRecipientsHeaders;
    }

    public Optional<HeadersDto> getHeaders() {
        return headers;
    }

    public Optional<String> getBody() {
        return body;
    }

    public Optional<Long> getMessageSize() {
        return messageSize;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailDto) {
            MailDto mailDto = (MailDto) o;

            return Objects.equals(this.name, mailDto.name)
                && Objects.equals(this.sender, mailDto.sender)
                && Objects.equals(this.recipients, mailDto.recipients)
                && Objects.equals(this.error, mailDto.error)
                && Objects.equals(this.state, mailDto.state)
                && Objects.equals(this.remoteHost, mailDto.remoteHost)
                && Objects.equals(this.remoteAddr, mailDto.remoteAddr)
                && Objects.equals(this.lastUpdated, mailDto.lastUpdated)
                && Objects.equals(this.attributes, mailDto.attributes)
                && Objects.equals(this.perRecipientsHeaders, mailDto.perRecipientsHeaders)
                && Objects.equals(this.headers, mailDto.headers)
                && Objects.equals(this.body, mailDto.body)
                && Objects.equals(this.messageSize, mailDto.messageSize);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name, sender, recipients, error, state, remoteHost, remoteAddr, lastUpdated, attributes, perRecipientsHeaders, headers, body, messageSize);
    }
}
