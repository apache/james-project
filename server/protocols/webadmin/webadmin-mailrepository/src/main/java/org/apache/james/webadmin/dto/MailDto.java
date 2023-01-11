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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.james.util.mime.MessageContentExtractor.MessageContent;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

public class MailDto {
    public static MailDto fromMail(Mail mail, Set<AdditionalField> additionalFields) throws MessagingException, InaccessibleFieldException {
        Optional<MessageContent> messageContent = fetchMessage(additionalFields, mail);
        return new MailDto(mail.getName(),
            mail.getMaybeSender().asOptional().map(MailAddress::asString),
            mail.getRecipients().stream().map(MailAddress::asString).collect(ImmutableList.toImmutableList()),
            Optional.ofNullable(mail.getErrorMessage()),
            Optional.ofNullable(mail.getState()),
            Optional.ofNullable(mail.getRemoteHost()),
            Optional.ofNullable(mail.getRemoteAddr()),
            Optional.ofNullable(mail.getLastUpdated()),
            fetchAttributes(additionalFields, mail),
            fetchPerRecipientsHeaders(additionalFields, mail),
            fetchHeaders(additionalFields, mail),
            fetchTextBody(additionalFields, messageContent),
            fetchHtmlBody(additionalFields, messageContent),
            fetchMessageSize(additionalFields, mail));
    }

    private static Optional<Long> fetchMessageSize(Set<AdditionalField> additionalFields, Mail mail) throws InaccessibleFieldException {
        if (!additionalFields.contains(AdditionalField.MESSAGE_SIZE)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mail.getMessageSize());
        } catch (MessagingException e) {
            throw new InaccessibleFieldException(AdditionalField.MESSAGE_SIZE, e);
        }
    }

    private static Optional<String> fetchTextBody(Set<AdditionalField> additionalFields, Optional<MessageContent> messageContent) throws InaccessibleFieldException {
        if (!additionalFields.contains(AdditionalField.TEXT_BODY)) {
            return Optional.empty();
        }

        return messageContent.flatMap(MessageContent::getTextBody);
    }

    private static Optional<String> fetchHtmlBody(Set<AdditionalField> additionalFields, Optional<MessageContent> messageContent) throws InaccessibleFieldException {
        if (!additionalFields.contains(AdditionalField.HTML_BODY)) {
            return Optional.empty();
        }

        return messageContent.flatMap(MessageContent::getHtmlBody);
    }

    private static Optional<MessageContent> fetchMessage(Set<AdditionalField> additionalFields, Mail mail) throws InaccessibleFieldException {
        if (!additionalFields.contains(AdditionalField.TEXT_BODY) && !additionalFields.contains(AdditionalField.HTML_BODY)) {
            return Optional.empty();
        }

        try {
            MessageContentExtractor extractor = new MessageContentExtractor();
            return Optional.ofNullable(mail.getMessage())
                .map(Throwing.<MimeMessage, MessageContent>function(message -> {
                    Message mimeMessage = MailDto.convertMessage(message);
                    MessageContent result = extractor.extract(mimeMessage);
                    mimeMessage.dispose();
                    return result;
                }).sneakyThrow());
        } catch (MessagingException e) {
            if (additionalFields.contains(AdditionalField.TEXT_BODY)) {
                throw new InaccessibleFieldException(AdditionalField.TEXT_BODY, e);
            } else {
                throw new InaccessibleFieldException(AdditionalField.HTML_BODY, e);
            }
        }
    }

    private static Message convertMessage(MimeMessage message) throws IOException, MessagingException {
        DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
        defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        return defaultMessageBuilder.parseMessage(new MimeMessageInputStream(message));
    }

    private static Optional<HeadersDto> fetchHeaders(Set<AdditionalField> additionalFields, Mail mail) throws InaccessibleFieldException {
        if (!additionalFields.contains(AdditionalField.HEADERS)) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(mail.getMessage())
                    .map(Throwing.function(MailDto::extractHeaders).sneakyThrow());
        } catch (MessagingException e) {
            throw new InaccessibleFieldException(AdditionalField.HEADERS, e);
        }
    }

    private static HeadersDto extractHeaders(MimeMessage message) throws MessagingException {
        return new HeadersDto(Collections
            .list(message.getAllHeaders())
            .stream()
            .collect(ImmutableListMultimap.toImmutableListMultimap(Header::getName, (header) -> MimeUtil.unscrambleHeaderValue(header.getValue()))));
    }

    private static Optional<ImmutableMap<String, HeadersDto>> fetchPerRecipientsHeaders(Set<AdditionalField> additionalFields, Mail mail) {
        if (!additionalFields.contains(AdditionalField.PER_RECIPIENTS_HEADERS)) {
            return Optional.empty();
        }
        Multimap<MailAddress, PerRecipientHeaders.Header> headersByRecipient = mail
                .getPerRecipientSpecificHeaders()
                .getHeadersByRecipient();

        return Optional.of(headersByRecipient
            .keySet()
            .stream()
            .collect(ImmutableMap.toImmutableMap(MailAddress::asString, (address) -> fetchPerRecipientHeader(headersByRecipient, address))));
    }

    private static HeadersDto fetchPerRecipientHeader(
            Multimap<MailAddress, PerRecipientHeaders.Header> headersByRecipient,
            MailAddress address) {
        return new HeadersDto(headersByRecipient.get(address)
            .stream()
            .collect(ImmutableListMultimap.toImmutableListMultimap(PerRecipientHeaders.Header::getName, PerRecipientHeaders.Header::getValue)));
    }

    private static Optional<ImmutableMap<String, String>> fetchAttributes(Set<AdditionalField> additionalFields, Mail mail) {
        if (!additionalFields.contains(AdditionalField.ATTRIBUTES)) {
            return Optional.empty();
        }

        return Optional.of(mail.attributes()
            .collect(ImmutableMap.toImmutableMap(
                attribute -> attribute.getName().asString(),
                attribute -> attribute.getValue().value().toString())));
    }

    private final String name;
    private final Optional<String> sender;
    private final List<String> recipients;
    private final Optional<String> error;
    private final Optional<String> state;
    private final Optional<String> remoteHost;
    private final Optional<String> remoteAddr;
    private final Optional<Date> lastUpdated;
    private final Optional<ImmutableMap<String, String>> attributes;
    private final Optional<ImmutableMap<String, HeadersDto>> perRecipientsHeaders;
    private final Optional<HeadersDto> headers;
    private final Optional<String> textBody;
    private final Optional<String> htmlBody;
    private final Optional<Long> messageSize;

    public enum AdditionalField {
        ATTRIBUTES("attributes"),
        PER_RECIPIENTS_HEADERS("perRecipientsHeaders"),
        TEXT_BODY("textBody"),
        HTML_BODY("htmlBody"),
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

    public MailDto(String name, Optional<String> sender, List<String> recipients, Optional<String> error,
            Optional<String> state, Optional<String> remoteHost, Optional<String> remoteAddr,
            Optional<Date> lastUpdated, Optional<ImmutableMap<String, String>> attributes,
            Optional<ImmutableMap<String, HeadersDto>> perRecipientsHeaders, Optional<HeadersDto> headers,
            Optional<String> textBody, Optional<String> htmlBody, Optional<Long> messageSize) {
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
        this.textBody = textBody;
        this.htmlBody = htmlBody;
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

    public Optional<ImmutableMap<String, String>> getAttributes() {
        return attributes;
    }

    public Optional<ImmutableMap<String, HeadersDto>> getPerRecipientsHeaders() {
        return perRecipientsHeaders;
    }

    public Optional<HeadersDto> getHeaders() {
        return headers;
    }

    public Optional<String> getTextBody() {
        return textBody;
    }

    public Optional<String> getHtmlBody() {
        return htmlBody;
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
                && Objects.equals(this.textBody, mailDto.textBody)
                && Objects.equals(this.htmlBody, mailDto.htmlBody)
                && Objects.equals(this.messageSize, mailDto.messageSize);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name, sender, recipients, error, state, remoteHost, remoteAddr, lastUpdated, attributes, perRecipientsHeaders, headers, textBody, htmlBody, messageSize);
    }
}
