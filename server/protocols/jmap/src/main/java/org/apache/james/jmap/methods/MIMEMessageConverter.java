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
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.Emailer;
import org.apache.james.mime4j.Charsets;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.FieldParser;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageBuilder;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.field.UnstructuredField;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.field.UnstructuredFieldImpl;
import org.apache.james.mime4j.io.InputStreams;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyFactory;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.RawField;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;

public class MIMEMessageConverter {

    private final MessageBuilder messageBuilder;
    private final BodyFactory bodyFactory;

    public MIMEMessageConverter() {
        this.messageBuilder = new DefaultMessageBuilder();
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

        Message message = messageBuilder.newMessage();
        message.setBody(createTextBody(creationMessageEntry.getMessage()));
        message.setHeader(buildMimeHeaders(creationMessageEntry.getCreationId(), creationMessageEntry.getMessage()));
        return message;
    }

    private Header buildMimeHeaders(String creationId, CreationMessage newMessage) {
        Header messageHeaders = new HeaderImpl();

        // add From: and Sender: headers
        newMessage.getFrom().map(this::convertEmailToMimeHeader)
                .map(Fields::from)
                .ifPresent(f -> messageHeaders.addField(f));
        newMessage.getFrom().map(this::convertEmailToMimeHeader)
                .map(Fields::sender)
                .ifPresent(f -> messageHeaders.addField(f));

        // add Reply-To:
        messageHeaders.addField(Fields.replyTo(newMessage.getReplyTo().stream()
                .map(rt -> convertEmailToMimeHeader(rt))
                .collect(Collectors.toList())));
        // add To: headers
        messageHeaders.addField(Fields.to(newMessage.getTo().stream()
                .map(this::convertEmailToMimeHeader)
                .collect(Collectors.toList())));
        // add Cc: headers
        messageHeaders.addField(Fields.cc(newMessage.getCc().stream()
                .map(this::convertEmailToMimeHeader)
                .collect(Collectors.toList())));
        // add Bcc: headers
        messageHeaders.addField(Fields.bcc(newMessage.getBcc().stream()
                .map(this::convertEmailToMimeHeader)
                .collect(Collectors.toList())));
        // add Subject: header
        messageHeaders.addField(Fields.subject(newMessage.getSubject()));
        // set creation Id as MessageId: header
        messageHeaders.addField(Fields.messageId(creationId));

        // date(String fieldName, Date date, TimeZone zone)
        // note that date conversion probably lose milliseconds !
        messageHeaders.addField(Fields.date("Date",
                Date.from(newMessage.getDate().toInstant()), TimeZone.getTimeZone(newMessage.getDate().getZone())
        ));
        newMessage.getInReplyToMessageId().ifPresent(addInReplyToHeader(messageHeaders::addField));
        return messageHeaders;
    }

    private Consumer<String> addInReplyToHeader(Consumer<Field> headerAppender) {
        return msgId -> {
            FieldParser<UnstructuredField> parser = UnstructuredFieldImpl.PARSER;
            RawField rawField = new RawField("In-Reply-To", msgId);
            headerAppender.accept(parser.parse(rawField, DecodeMonitor.SILENT));
        };
    }

    private TextBody createTextBody(CreationMessage newMessage) {
        try {
            return bodyFactory.textBody(
                    InputStreams.create(newMessage.getTextBody().orElse(""), Charsets.UTF_8),
                    Charsets.UTF_8.name());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private Mailbox convertEmailToMimeHeader(Emailer address) {
        String[] splitAddress = address.getEmail().split("@", 2);
        return new Mailbox(address.getName(), null, splitAddress[0], splitAddress[1]);
    }
}
