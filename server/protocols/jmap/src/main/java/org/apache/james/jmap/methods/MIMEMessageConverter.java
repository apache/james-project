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

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.model.CreationMessageId;
import org.apache.james.mime4j.Charsets;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.FieldParser;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.field.UnstructuredField;
import org.apache.james.mime4j.field.UnstructuredFieldImpl;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.MessageBuilder;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.NameValuePair;
import org.apache.james.mime4j.stream.RawField;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;

public class MIMEMessageConverter {

    private final BasicBodyFactory bodyFactory;

    public MIMEMessageConverter() {
        this.bodyFactory = new BasicBodyFactory();
    }

    public byte[] convert(MessageWithId.CreationMessageEntry creationMessageEntry) {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DefaultMessageWriter writer = new DefaultMessageWriter();
        try {
            writer.writeMessage(convertToMime(creationMessageEntry), buffer);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return buffer.toByteArray();
    }

    @VisibleForTesting Message convertToMime(MessageWithId.CreationMessageEntry creationMessageEntry) {
        if (creationMessageEntry == null || creationMessageEntry.getMessage() == null) {
            throw new IllegalArgumentException("creationMessageEntry is either null or has null message");
        }

        MessageBuilder messageBuilder = MessageBuilder.create();
        messageBuilder.setBody(createTextBody(creationMessageEntry.getMessage()));
        buildMimeHeaders(messageBuilder, creationMessageEntry.getCreationId(), creationMessageEntry.getMessage());
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
        newMessage.getHtmlBody().ifPresent(x -> messageBuilder.setContentType("text/html", new NameValuePair("charset", "utf-8")));
    }

    private Consumer<String> addInReplyToHeader(Consumer<Field> headerAppender) {
        return msgId -> {
            FieldParser<UnstructuredField> parser = UnstructuredFieldImpl.PARSER;
            RawField rawField = new RawField("In-Reply-To", msgId);
            headerAppender.accept(parser.parse(rawField, DecodeMonitor.SILENT));
        };
    }

    private TextBody createTextBody(CreationMessage newMessage) {
        if (newMessage.getTextBody().isPresent() && newMessage.getHtmlBody().isPresent()) {
            throw new NotImplementedException("Converter can't handle yet htmlBody and textBody in the same message");
        }
        String body = newMessage.getHtmlBody()
                        .orElse(newMessage.getTextBody()
                                .orElse(""));
        return bodyFactory.textBody(body, Charsets.UTF_8);
    }

    private Mailbox convertEmailToMimeHeader(DraftEmailer address) {
        if (!address.hasValidEmail()) {
            throw new IllegalArgumentException("address");
        }
        CreationMessage.EmailUserAndDomain emailUserAndDomain = address.getEmailUserAndDomain();
        return new Mailbox(address.getName().orElse(null), null, emailUserAndDomain.getUser().orElse(null), emailUserAndDomain.getDomain().orElse(null));
    }
}
