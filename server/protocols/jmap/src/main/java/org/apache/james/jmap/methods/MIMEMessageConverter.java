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

package org.apache.james.jmap.methods;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.model.CreationMessageId;
import org.apache.james.mime4j.Charsets;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.FieldParser;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.field.UnstructuredField;
import org.apache.james.mime4j.field.UnstructuredFieldImpl;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.MessageBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.NameValuePair;
import org.apache.james.mime4j.stream.RawField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.net.MediaType;

public class MIMEMessageConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MIMEMessageConverter.class);

    private static final String PLAIN_TEXT_MEDIA_TYPE = MediaType.PLAIN_TEXT_UTF_8.withoutParameters().toString();
    private static final String HTML_MEDIA_TYPE = MediaType.HTML_UTF_8.withoutParameters().toString();
    private static final NameValuePair UTF_8_CHARSET = new NameValuePair("charset", Charsets.UTF_8.name());
    private static final String MIXED_SUB_TYPE = "mixed";

    private final BasicBodyFactory bodyFactory;

    public MIMEMessageConverter() {
        this.bodyFactory = new BasicBodyFactory();
    }

    public byte[] convert(ValueWithId.CreationMessageEntry creationMessageEntry) {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DefaultMessageWriter writer = new DefaultMessageWriter();
        try {
            writer.writeMessage(convertToMime(creationMessageEntry), buffer);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return buffer.toByteArray();
    }

    @VisibleForTesting Message convertToMime(ValueWithId.CreationMessageEntry creationMessageEntry) {
        if (creationMessageEntry == null || creationMessageEntry.getValue() == null) {
            throw new IllegalArgumentException("creationMessageEntry is either null or has null message");
        }

        MessageBuilder messageBuilder = MessageBuilder.create();
        if (mixedTextAndHtml(creationMessageEntry.getValue())) {
            messageBuilder.setBody(createMultipartBody(creationMessageEntry.getValue()));
        } else {
            messageBuilder.setBody(createTextBody(creationMessageEntry.getValue()));
        }
        buildMimeHeaders(messageBuilder, creationMessageEntry.getCreationId(), creationMessageEntry.getValue());
        return messageBuilder.build();
    }

    private void buildMimeHeaders(MessageBuilder messageBuilder, CreationMessageId creationId, CreationMessage newMessage) {
        Optional<Mailbox> fromAddress = newMessage.getFrom().filter(DraftEmailer::hasValidEmail).map(this::convertEmailToMimeHeader);
        fromAddress.ifPresent(messageBuilder::setFrom);
        fromAddress.ifPresent(messageBuilder::setSender);

        messageBuilder.setReplyTo(newMessage.getReplyTo().stream()
                .map(this::convertEmailToMimeHeader)
                .collect(Collectors.toList()));
        messageBuilder.setTo(newMessage.getTo().stream()
                .filter(DraftEmailer::hasValidEmail)
                .map(this::convertEmailToMimeHeader)
                .collect(Collectors.toList()));
        messageBuilder.setCc(newMessage.getCc().stream()
                .filter(DraftEmailer::hasValidEmail)
                .map(this::convertEmailToMimeHeader)
                .collect(Collectors.toList()));
        messageBuilder.setBcc(newMessage.getBcc().stream()
                .filter(DraftEmailer::hasValidEmail)
                .map(this::convertEmailToMimeHeader)
                .collect(Collectors.toList()));
        messageBuilder.setSubject(newMessage.getSubject());
        messageBuilder.setMessageId(creationId.getId());

        // note that date conversion probably lose milliseconds!
        messageBuilder.setDate(Date.from(newMessage.getDate().toInstant()), TimeZone.getTimeZone(newMessage.getDate().getZone()));
        newMessage.getInReplyToMessageId().ifPresent(addInReplyToHeader(messageBuilder::addField));
        if (!mixedTextAndHtml(newMessage)) {
            newMessage.getHtmlBody().ifPresent(x -> messageBuilder.setContentType(HTML_MEDIA_TYPE, UTF_8_CHARSET));
        }
    }

    private Consumer<String> addInReplyToHeader(Consumer<Field> headerAppender) {
        return msgId -> {
            FieldParser<UnstructuredField> parser = UnstructuredFieldImpl.PARSER;
            RawField rawField = new RawField("In-Reply-To", msgId);
            headerAppender.accept(parser.parse(rawField, DecodeMonitor.SILENT));
        };
    }

    private boolean mixedTextAndHtml(CreationMessage newMessage) {
        return newMessage.getTextBody().isPresent() && newMessage.getHtmlBody().isPresent();
    }

    private TextBody createTextBody(CreationMessage newMessage) {
        String body = newMessage.getHtmlBody()
                        .orElse(newMessage.getTextBody()
                                .orElse(""));
        return bodyFactory.textBody(body, Charsets.UTF_8);
    }

    private Multipart createMultipartBody(CreationMessage newMessage) {
        try {
            return MultipartBuilder.create(MIXED_SUB_TYPE)
                    .addBodyPart(BodyPartBuilder.create()
                            .use(bodyFactory)
                            .setBody(newMessage.getTextBody().get(), Charsets.UTF_8)
                            .setContentType(PLAIN_TEXT_MEDIA_TYPE, UTF_8_CHARSET)
                            .build())
                    .addBodyPart(BodyPartBuilder.create()
                            .use(bodyFactory)
                            .setBody(newMessage.getHtmlBody().get(), Charsets.UTF_8)
                            .setContentType(HTML_MEDIA_TYPE, UTF_8_CHARSET)
                            .build())
                    .build();
        } catch (IOException e) {
            LOGGER.error("Error while creating textBody \n"+ newMessage.getTextBody().get() +"\n or htmlBody \n" + newMessage.getHtmlBody().get(), e);
            throw Throwables.propagate(e);
        }
    }

    private Mailbox convertEmailToMimeHeader(DraftEmailer address) {
        if (!address.hasValidEmail()) {
            throw new IllegalArgumentException("address");
        }
        CreationMessage.EmailUserAndDomain emailUserAndDomain = address.getEmailUserAndDomain();
        return new Mailbox(address.getName().orElse(null), null, emailUserAndDomain.getUser().orElse(null), emailUserAndDomain.getDomain().orElse(null));
    }
}
