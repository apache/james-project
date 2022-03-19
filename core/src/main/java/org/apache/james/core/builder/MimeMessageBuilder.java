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

package org.apache.james.core.builder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import jakarta.activation.DataHandler;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import org.apache.commons.io.IOUtils;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Booleans;

public class MimeMessageBuilder {

    public static final String DEFAULT_TEXT_PLAIN_UTF8_TYPE = "text/plain; charset=UTF-8";

    public static class Header {
        private final String name;
        private final String value;

        public Header(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Header) {
                Header header = (Header) o;

                return Objects.equals(this.name, header.name)
                    && Objects.equals(this.value, header.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(name, value);
        }
    }

    public static class MultipartBuilder {
        private ImmutableList.Builder<BodyPart> bodyParts = ImmutableList.builder();
        private Optional<String> subType = Optional.empty();

        public MultipartBuilder subType(String subType) {
            this.subType = Optional.of(subType);
            return this;
        }

        public MultipartBuilder addBody(BodyPart bodyPart) {
            this.bodyParts.add(bodyPart);
            return this;
        }

        public MultipartBuilder addBody(BodyPartBuilder bodyPart) throws IOException, MessagingException {
            this.bodyParts.add(bodyPart.build());
            return this;
        }

        public MultipartBuilder addBody(MimeMessageBuilder builder) throws IOException, MessagingException {
            return addBody(builder.build());
        }

        public MultipartBuilder addBody(MimeMessage mimeMessage) throws IOException, MessagingException {
            MimeBodyPart mimeBodyPart = new MimeBodyPart();
            mimeBodyPart.setContent(mimeMessage, "message/rfc822");
            this.bodyParts.add(mimeBodyPart);
            return this;
        }

        public MultipartBuilder addBodies(BodyPart... bodyParts) {
            this.bodyParts.addAll(Arrays.asList(bodyParts));
            return this;
        }

        public MultipartBuilder addBodies(BodyPartBuilder... bodyParts) {
            this.bodyParts.addAll(Arrays.stream(bodyParts)
                .map(Throwing.function(BodyPartBuilder::build).sneakyThrow())
                .collect(ImmutableList.toImmutableList()));
            return this;
        }

        public MimeMultipart build() throws MessagingException {
            MimeMultipart multipart = new MimeMultipart();
            subType.ifPresent(Throwing.consumer(multipart::setSubType));
            List<BodyPart> bodyParts = this.bodyParts.build();
            for (BodyPart bodyPart : bodyParts) {
                multipart.addBodyPart(bodyPart);
            }
            return multipart;
        }
    }

    public static class BodyPartBuilder {
        public static final String DEFAULT_VALUE = "";

        private Optional<String> cid = Optional.empty();
        private Optional<String> filename = Optional.empty();
        private ImmutableList.Builder<Header> headers = ImmutableList.builder();
        private Optional<String> disposition = Optional.empty();
        private Optional<String> dataAsString = Optional.empty();
        private Optional<byte[]> dataAsBytes = Optional.empty();
        private Optional<Multipart> dataAsMultipart = Optional.empty();
        private Optional<String> type = Optional.empty();

        public BodyPartBuilder cid(String cid) {
            this.cid = Optional.of(cid);
            return this;
        }

        public BodyPartBuilder filename(String filename) {
            this.filename = Optional.of(filename);
            return this;
        }

        public BodyPartBuilder disposition(String disposition) {
            this.disposition = Optional.of(disposition);
            return this;
        }

        public BodyPartBuilder data(String data) {
            this.dataAsString = Optional.of(data);
            return this;
        }

        public BodyPartBuilder data(Multipart data) {
            this.dataAsMultipart = Optional.of(data);
            return this;
        }

        public BodyPartBuilder data(byte[] data) {
            this.dataAsBytes = Optional.of(data);
            return this;
        }

        public BodyPartBuilder type(String type) {
            this.type = Optional.of(type);
            return this;
        }

        public BodyPartBuilder addHeader(String name, String value) {
            this.headers.add(new Header(name, value));
            return this;
        }

        public BodyPartBuilder addHeaders(Header... headers) {
            return addHeaders(Arrays.asList(headers));
        }

        public BodyPartBuilder addHeaders(Collection<Header> headers) {
            this.headers.addAll(headers);
            return this;
        }

        public BodyPart build() throws IOException, MessagingException {
            Preconditions.checkState(Booleans.countTrue(dataAsString.isPresent(),
                dataAsBytes.isPresent(), dataAsMultipart.isPresent()) <= 1, "Can not specify data as bytes, multipart and data as string at the same time");
            MimeBodyPart bodyPart = new MimeBodyPart();
            if (dataAsBytes.isPresent()) {
                bodyPart.setDataHandler(
                    new DataHandler(
                        new ByteArrayDataSource(
                            dataAsBytes.get(),
                            type.orElse(DEFAULT_TEXT_PLAIN_UTF8_TYPE))));
            } else if (dataAsMultipart.isPresent()) {
                bodyPart.setContent(dataAsMultipart.get());
            } else {
                bodyPart.setDataHandler(
                    new DataHandler(
                        new ByteArrayDataSource(
                            dataAsString.orElse(DEFAULT_VALUE),
                            type.orElse(DEFAULT_TEXT_PLAIN_UTF8_TYPE))));
            }
            if (filename.isPresent()) {
                bodyPart.setFileName(filename.get());
            }
            if (cid.isPresent()) {
                bodyPart.setContentID(cid.get());
            }
            if (disposition.isPresent()) {
                bodyPart.setDisposition(disposition.get());
            }
            List<Header> headerList = headers.build();
            for (Header header: headerList) {
                bodyPart.addHeader(header.name, header.value);
            }
            return bodyPart;
        }
    }

    public static MimeMessageBuilder mimeMessageBuilder() {
        return new MimeMessageBuilder();
    }

    public static MultipartBuilder multipartBuilder() {
        return new MultipartBuilder();
    }

    public static BodyPartBuilder bodyPartBuilder() {
        return new BodyPartBuilder();
    }

    public static BodyPart bodyPartFromBytes(byte[] bytes) throws MessagingException {
        return new MimeBodyPart(new ByteArrayInputStream(bytes));
    }

    private Optional<String> text = Optional.empty();
    private Optional<String> textContentType = Optional.empty();
    private Optional<String> subject = Optional.empty();
    private Optional<InternetAddress> sender = Optional.empty();
    private Optional<MimeMultipart> content = Optional.empty();
    private ImmutableList.Builder<InternetAddress> from = ImmutableList.builder();
    private ImmutableList.Builder<InternetAddress> cc = ImmutableList.builder();
    private ImmutableList.Builder<InternetAddress> to = ImmutableList.builder();
    private ImmutableList.Builder<InternetAddress> bcc = ImmutableList.builder();
    private ImmutableList.Builder<Header> headers = ImmutableList.builder();

    public MimeMessageBuilder setText(String text) {
        this.text = Optional.of(text);
        return this;
    }

    public MimeMessageBuilder setText(String text, String contentType) {
        this.text = Optional.of(text);
        this.textContentType = Optional.of(contentType);
        return this;
    }

    public MimeMessageBuilder addToRecipient(String text) throws AddressException {
        this.to.add(new InternetAddress(text));
        return this;
    }

    public MimeMessageBuilder setSubject(String subject) {
        this.subject = Optional.ofNullable(subject);
        return this;
    }

    public MimeMessageBuilder setSender(String sender) throws AddressException {
        this.sender = Optional.of(new InternetAddress(sender));
        return this;
    }

    public MimeMessageBuilder addFrom(String from) throws AddressException {
        this.from.add(new InternetAddress(from));
        return this;
    }

    public MimeMessageBuilder addFrom(InternetAddress... from) throws AddressException {
        this.from.addAll(Arrays.asList(from));
        return this;
    }

    public MimeMessageBuilder addCcRecipient(String text) throws AddressException {
        this.cc.add(new InternetAddress(text));
        return this;
    }

    public MimeMessageBuilder addBccRecipient(String text) throws AddressException {
        this.bcc.add(new InternetAddress(text));
        return this;
    }

    public MimeMessageBuilder addToRecipient(String... tos) throws AddressException {
        this.to.addAll(Arrays.stream(tos)
            .map(Throwing.function(InternetAddress::new))
            .collect(ImmutableList.toImmutableList()));
        return this;
    }

    public MimeMessageBuilder addCcRecipient(String... ccs) throws AddressException {
        this.cc.addAll(Arrays.stream(ccs)
            .map(Throwing.function(InternetAddress::new))
            .collect(ImmutableList.toImmutableList()));
        return this;
    }

    public MimeMessageBuilder addBccRecipient(String... bccs) throws AddressException {
        this.bcc.addAll(Arrays.stream(bccs)
            .map(Throwing.function(InternetAddress::new))
            .collect(ImmutableList.toImmutableList()));
        return this;
    }

    public MimeMessageBuilder addToRecipient(InternetAddress... tos) throws AddressException {
        this.to.addAll(Arrays.asList(tos));
        return this;
    }

    public MimeMessageBuilder addCcRecipient(InternetAddress... ccs) throws AddressException {
        this.cc.addAll(Arrays.asList(ccs));
        return this;
    }

    public MimeMessageBuilder addBccRecipient(InternetAddress... bccs) throws AddressException {
        this.bcc.addAll(Arrays.asList(bccs));
        return this;
    }

    public MimeMessageBuilder setContent(MimeMultipart mimeMultipart) {
        this.content = Optional.of(mimeMultipart);
        return this;
    }

    public MimeMessageBuilder setContent(MultipartBuilder mimeMultipart) throws MessagingException {
        this.content = Optional.of(mimeMultipart.build());
        return this;
    }

    public MimeMessageBuilder setMultipartWithBodyParts(BodyPart... bobyParts) throws MessagingException {
        this.content = Optional.of(MimeMessageBuilder.multipartBuilder()
            .addBodies(bobyParts)
            .build());
        return this;
    }

    public MimeMessageBuilder setMultipartWithBodyParts(BodyPartBuilder... bobyParts) throws MessagingException {
        this.content = Optional.of(MimeMessageBuilder.multipartBuilder()
            .addBodies(bobyParts)
            .build());
        return this;
    }

    public MimeMessageBuilder setMultipartWithSubMessage(MimeMessage mimeMessage) throws MessagingException, IOException {
        return setMultipartWithBodyParts(
            new MimeBodyPart(
                new InternetHeaders(new ByteArrayInputStream("Content-Type: multipart/mixed".getBytes(StandardCharsets.US_ASCII))),
                IOUtils.toByteArray(mimeMessage.getInputStream())));
    }

    public MimeMessageBuilder setMultipartWithSubMessage(MimeMessageBuilder mimeMessage) throws MessagingException, IOException {
        return setMultipartWithSubMessage(mimeMessage.build());
    }

    public MimeMessageBuilder addHeader(String name, String value) {
        this.headers.add(new Header(name, value));
        return this;
    }

    public MimeMessageBuilder addHeaders(Header... headers) {
        return addHeaders(Arrays.asList(headers));
    }

    public MimeMessageBuilder addHeaders(Collection<Header> headers) {
        this.headers.addAll(headers);
        return this;
    }

    public MimeMessage build() throws MessagingException {
        Preconditions.checkState(!(text.isPresent() && content.isPresent()), "Can not get at the same time a text and a content");
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        if (text.isPresent()) {
            mimeMessage.setContent(text.get(), textContentType.orElse(DEFAULT_TEXT_PLAIN_UTF8_TYPE));
        }
        if (content.isPresent()) {
            mimeMessage.setContent(content.get());
        }
        if (sender.isPresent()) {
            mimeMessage.setSender(sender.get());
        }
        if (subject.isPresent()) {
            mimeMessage.setSubject(subject.get());
        }
        ImmutableList<InternetAddress> fromAddresses = from.build();
        if (!fromAddresses.isEmpty()) {
            mimeMessage.addFrom(fromAddresses.toArray(InternetAddress[]::new));
        }
        List<InternetAddress> toAddresses = to.build();
        if (!toAddresses.isEmpty()) {
            mimeMessage.setRecipients(Message.RecipientType.TO, toAddresses.toArray(InternetAddress[]::new));
        }
        List<InternetAddress> ccAddresses = cc.build();
        if (!ccAddresses.isEmpty()) {
            mimeMessage.setRecipients(Message.RecipientType.CC, ccAddresses.toArray(InternetAddress[]::new));
        }
        List<InternetAddress> bccAddresses = bcc.build();
        if (!bccAddresses.isEmpty()) {
            mimeMessage.setRecipients(Message.RecipientType.BCC, bccAddresses.toArray(InternetAddress[]::new));
        }

        MimeMessage wrappedMessage = MimeMessageWrapper.wrap(mimeMessage);

        List<Header> headerList = headers.build();
        for (Header header: headerList) {
            if (header.name.equals("Message-ID") || header.name.equals("Date")) {
                wrappedMessage.setHeader(header.name, header.value);
            } else {
                wrappedMessage.addHeader(header.name, header.value);
            }
        }
        wrappedMessage.saveChanges();

        return wrappedMessage;
    }

}
