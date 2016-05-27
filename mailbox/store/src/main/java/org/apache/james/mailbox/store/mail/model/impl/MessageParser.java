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
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.stream.Field;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class MessageParser {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String HEADER_SEPARATOR = ";";

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
                Optional<String> contentType = contentType(entity.getHeader().getField(CONTENT_TYPE));
                attachments.add(createAttachment(messageWriter, entity.getBody(), contentType.or(DEFAULT_CONTENT_TYPE)));
            }
        }
        return attachments.build();
    }

    private Optional<String> contentType(Field contentType) {
        if (contentType == null) {
            return Optional.absent();
        }
        String body = contentType.getBody();
        if (Strings.isNullOrEmpty(body)) {
            return Optional.absent();
        }
        if (body.contains(HEADER_SEPARATOR)) {
            return Optional.of(Iterables.get(Splitter.on(HEADER_SEPARATOR).split(body), 0));
        }
        return Optional.of(body);
    }

    private boolean isAttachment(Entity part) {
        return ContentDispositionField.DISPOSITION_TYPE_ATTACHMENT.equalsIgnoreCase(part.getDispositionType());
    }

    private Attachment createAttachment(MessageWriter messageWriter, Body body, String contentType) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        messageWriter.writeBody(body, out);
        byte[] bytes = out.toByteArray();
        return Attachment.from(bytes, contentType);
    }
}
