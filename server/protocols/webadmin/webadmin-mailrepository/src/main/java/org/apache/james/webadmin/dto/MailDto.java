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

import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.mail.Header;
import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.Multimap;

public class MailDto {
    public static MailDto fromMail(Mail mail, List<AdditionalFields> additionalFields) throws MessagingException, MissingRequestedField {
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

    private static Optional<Long> fetchMessageSize(List<AdditionalFields> additionalFields, Mail mail) throws MissingRequestedField {
        if (!additionalFields.contains(AdditionalFields.MESSAGE_SIZE)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(new Long(mail.getMessageSize()));
        } catch (MessagingException e) {
            throw new MissingRequestedField("messageSize");
        }
    }

    private static Optional<String> fetchBody(List<AdditionalFields> additionalFields, Mail mail) throws MissingRequestedField {
        if (!additionalFields.contains(AdditionalFields.BODY)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mail.getMessage().getContent().toString());
        } catch (IOException | MessagingException | NullPointerException e) {
            throw new MissingRequestedField("body");
        }
    }

    private static Optional<Map<String, List<String>>> fetchHeaders(List<AdditionalFields> additionalFields, Mail mail) throws MissingRequestedField {
        if (!additionalFields.contains(AdditionalFields.HEADERS)) {
            return Optional.empty();
        }
        Map<String, List<String>> headers = new HashMap<>();

        Enumeration<Header> rawHeaders;
        try {
            rawHeaders = mail.getMessage().getAllHeaders();
            while (rawHeaders.hasMoreElements()) {
                Header header = rawHeaders.nextElement();
                String name = header.getName();
                if (!headers.containsKey(name)) {
                    headers.put(name, new ArrayList<>());
                }

                headers.get(name).add(header.getValue());
            }

            return Optional.of(headers);
        } catch (NullPointerException | MessagingException e) {
            throw new MissingRequestedField("headers");
        }
    }

    private static Optional<Map<String, Map<String, List<String>>>> fetchPerRecipientsHeaders(List<AdditionalFields> additionalFields, Mail mail) {
        if (!additionalFields.contains(AdditionalFields.PER_RECIPIENTS_HEADERS)) {
            return Optional.empty();
        }
        Map<String, Map<String, List<String>>> headers = new HashMap<>();
        PerRecipientHeaders specificHeaders = mail.getPerRecipientSpecificHeaders();
        Multimap<MailAddress, org.apache.mailet.PerRecipientHeaders.Header> headersByRecipient = specificHeaders.getHeadersByRecipient();

        for (MailAddress address : headersByRecipient.keySet()) {
            headers.put(address.asString(), fetchPerRecipientHeader(headersByRecipient, address));
        }

        return Optional.of(headers);
    }

    private static Map<String, List<String>> fetchPerRecipientHeader(
            Multimap<MailAddress, org.apache.mailet.PerRecipientHeaders.Header> headersByRecipient,
            MailAddress address) {
        Map<String, List<String>> header = new HashMap<>();
        for (org.apache.mailet.PerRecipientHeaders.Header rawHeader : headersByRecipient.get(address)) {
            String name = rawHeader.getName();
            if (!header.containsKey(name)) {
                header.put(name, new ArrayList<>());
            }

            header.get(name).add(rawHeader.getValue());
        }
        return header;
    }

    private static Optional<Map<String, String>> fetchAttributes(List<AdditionalFields> additionalFields, Mail mail) {
        if (!additionalFields.contains(AdditionalFields.ATTRIBUTES)) {
            return Optional.empty();
        }
        Map<String, String> attributes = new HashMap<>();

        Iterator<String> attributeNames = mail.getAttributeNames();
        while (attributeNames.hasNext()) {
            String attributeName = (String) attributeNames.next();
            attributes.put(attributeName, mail.getAttribute(attributeName).toString());
        }

        return Optional.of(attributes);
    }

    private final String name;
    private final Optional<String> sender;
    private final List<String> recipients;
    private final Optional<String> error;
    private final Optional<String> state;
    private final Optional<String> remoteHost;
    private final Optional<String> remoteAddr;
    private final Optional<Date> lastUpdated;
    private final Optional<Map<String,String>> attributes;
    private final Optional<Map<String,Map<String,List<String>>>> perRecipientsHeaders;
    private final Optional<Map<String,List<String>>> headers;
    private final Optional<String> body;
    private final Optional<Long> messageSize;

    public enum AdditionalFields {
        ATTRIBUTES,
        PER_RECIPIENTS_HEADERS,
        HEADERS,
        BODY,
        MESSAGE_SIZE
    }


    private MailDto(String name, Optional<String> sender, List<String> recipients, Optional<String> error,
            Optional<String> state, Optional<String> remoteHost, Optional<String> remoteAddr,
            Optional<Date> lastUpdated, Optional<Map<String, String>> attributes,
            Optional<Map<String, Map<String, List<String>>>> perRecipientsHeaders,
            Optional<Map<String, List<String>>> headers, Optional<String> body, Optional<Long> messageSize) {
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

    public Optional<Map<String, Map<String, List<String>>>> getPerRecipientsHeaders() {
        return perRecipientsHeaders;
    }

    public Optional<Map<String, List<String>>> getHeaders() {
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
                && Objects.equals(this.state, mailDto.state);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name, sender, recipients, error, state);
    }
}
