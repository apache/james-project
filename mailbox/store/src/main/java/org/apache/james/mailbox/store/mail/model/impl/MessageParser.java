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

package org.apache.james.mailbox.store.mail.model.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.james.mailbox.store.mail.model.Attachment;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.MessageWriter;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.stream.Field;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class MessageParser {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public List<Attachment> retrieveAttachments(InputStream fullContent) throws MimeException, IOException {
        Body body = new DefaultMessageBuilder()
                .parseMessage(fullContent)
                .getBody();
        try {
            if (body instanceof Multipart) {
                return listAttachments((Multipart)body);
            } else {
                return ImmutableList.of();
            }
        } finally {
            body.dispose();
        }
    }

    private List<Attachment> listAttachments(Multipart multipart) throws IOException {
        ImmutableList.Builder<Attachment> attachments = ImmutableList.builder();
        MessageWriter messageWriter = new DefaultMessageWriter();
        for (Entity entity : multipart.getBodyParts()) {
            if (isAttachment(entity)) {
                Optional<ContentTypeField> contentTypeField = contentTypeField(entity.getHeader().getField(CONTENT_TYPE));
                Optional<String> contentType = contentType(contentTypeField);
                Optional<String> name = name(contentTypeField);
                
                attachments.add(Attachment.builder()
                        .bytes(getBytes(messageWriter, entity.getBody()))
                        .type(contentType.or(DEFAULT_CONTENT_TYPE))
                        .name(name)
                        .build());
            }
        }
        return attachments.build();
    }

    private Optional<String> contentType(Optional<ContentTypeField> contentTypeField) {
        return contentTypeField.transform(new Function<ContentTypeField, Optional<String>>() {
            @Override
            public Optional<String> apply(ContentTypeField field) {
                return Optional.fromNullable(field.getMimeType());
            }
        }).or(Optional.<String> absent());
    }

    private Optional<String> name(Optional<ContentTypeField> contentTypeField) {
        return contentTypeField.transform(new Function<ContentTypeField, Optional<String>>() {
            @Override
            public Optional<String> apply(ContentTypeField field) {
                return Optional.fromNullable(field.getParameter("name"));
            }
        }).or(Optional.<String> absent());
    }

    private Optional<ContentTypeField> contentTypeField(Field contentType) {
        if (contentType == null || !(contentType instanceof ContentTypeField)) {
            return Optional.absent();
        }
        return Optional.of((ContentTypeField) contentType);
    }

    private boolean isAttachment(Entity part) {
        return ContentDispositionField.DISPOSITION_TYPE_ATTACHMENT.equalsIgnoreCase(part.getDispositionType());
    }

    private byte[] getBytes(MessageWriter messageWriter, Body body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        messageWriter.writeBody(body, out);
        return out.toByteArray();
    }
}
