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
import org.apache.james.mailbox.store.mail.model.MessageAttachment;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.MessageWriter;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.dom.field.ContentIdField;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.dom.field.ParsedField;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.stream.Field;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class MessageParser {

    private static final String TEXT_MEDIA_TYPE = "text";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_ID = "Content-ID";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final List<String> ATTACHMENT_CONTENT_DISPOSITIONS = ImmutableList.of(
            ContentDispositionField.DISPOSITION_TYPE_ATTACHMENT.toLowerCase(),
            ContentDispositionField.DISPOSITION_TYPE_INLINE.toLowerCase());

    public List<MessageAttachment> retrieveAttachments(InputStream fullContent) throws MimeException, IOException {
        Body body = new DefaultMessageBuilder()
                .parseMessage(fullContent)
                .getBody();
        try {
            if (body instanceof Multipart) {
                Multipart multipartBody = (Multipart)body;
                return listAttachments(multipartBody, Context.fromSubType(multipartBody.getSubType()));
            } else {
                return ImmutableList.of();
            }
        } finally {
            body.dispose();
        }
    }

    private List<MessageAttachment> listAttachments(Multipart multipart, Context context) throws IOException {
        ImmutableList.Builder<MessageAttachment> attachments = ImmutableList.builder();
        MessageWriter messageWriter = new DefaultMessageWriter();
        for (Entity entity : multipart.getBodyParts()) {
            if (isMultipart(entity)) {
                attachments.addAll(listAttachments((Multipart) entity.getBody(), Context.fromEntity(entity)));
            } else {
                if (isAttachment(entity, context)) {
                    attachments.add(retrieveAttachment(messageWriter, entity));
                }
            }
        }
        return attachments.build();
    }

    private MessageAttachment retrieveAttachment(MessageWriter messageWriter, Entity entity) throws IOException {
        Optional<ContentTypeField> contentTypeField = getContentTypeField(entity);
        Optional<String> contentType = contentType(contentTypeField);
        Optional<String> name = name(contentTypeField);
        Optional<Cid> cid = cid(castField(entity.getHeader().getField(CONTENT_ID), ContentIdField.class));
        boolean isInline = isInline(castField(entity.getHeader().getField(CONTENT_DISPOSITION), ContentDispositionField.class));

        return MessageAttachment.builder()
                .attachment(Attachment.builder()
                    .bytes(getBytes(messageWriter, entity.getBody()))
                    .type(contentType.or(DEFAULT_CONTENT_TYPE))
                    .build())
                .name(name.orNull())
                .cid(cid.orNull())
                .isInline(isInline)
                .build();
    }

    private Optional<ContentTypeField> getContentTypeField(Entity entity) {
        return castField(entity.getHeader().getField(CONTENT_TYPE), ContentTypeField.class);
    }

    @SuppressWarnings("unchecked")
    private <U extends ParsedField> Optional<U> castField(Field field, Class<U> clazz) {
        if (field == null || !clazz.isInstance(field)) {
            return Optional.absent();
        }
        return Optional.of((U) field);
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

    private Optional<Cid> cid(Optional<ContentIdField> contentIdField) {
        return contentIdField.transform(new Function<ContentIdField, Optional<Cid>>() {
            @Override
            public Optional<Cid> apply(ContentIdField field) {
                return Optional.fromNullable(field.getId())
                        .transform(new Function<String, Cid>() {

                            @Override
                            public Cid apply(String cid) {
                                return Cid.from(cid);
                            }
                        });
            }
        }).or(Optional.<Cid> absent());
    }

    private boolean isMultipart(Entity entity) {
        return entity.isMultipart() && entity.getBody() instanceof Multipart;
    }

    private boolean isInline(Optional<ContentDispositionField> contentDispositionField) {
        return contentDispositionField.transform(new Function<ContentDispositionField, Boolean>() {
            @Override
            public Boolean apply(ContentDispositionField field) {
                return field.isInline();
            }
        }).or(false);
    }

    private boolean isAttachment(Entity part, Context context) {
        if (context == Context.BODY && isTextPart(part)) {
            return false;
        }
        return Optional.fromNullable(part.getDispositionType())
                .transform(new Function<String, Boolean>() {

                    @Override
                    public Boolean apply(String dispositionType) {
                        return ATTACHMENT_CONTENT_DISPOSITIONS.contains(dispositionType.toLowerCase());
                    }
                }).isPresent();
    }

    private boolean isTextPart(Entity part) {
        Optional<ContentTypeField> contentTypeField = getContentTypeField(part);
        if (contentTypeField.isPresent()) {
            String mediaType = contentTypeField.get().getMediaType();
            if (mediaType != null && mediaType.equals(TEXT_MEDIA_TYPE)) {
                return true;
            }
        }
        return false;
    }

    private byte[] getBytes(MessageWriter messageWriter, Body body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        messageWriter.writeBody(body, out);
        return out.toByteArray();
    }

    private static enum Context {
        BODY,
        OTHER;

        private static final String ALTERNATIVE_SUB_TYPE = "alternative";
        private static final String MULTIPART_ALTERNATIVE = "multipart/" + ALTERNATIVE_SUB_TYPE;

        public static Context fromEntity(Entity entity) {
            if (isMultipartAlternative(entity)) {
                return BODY;
            }
            return OTHER;
        }

        public static Context fromSubType(String subPart) {
            if (isAlternative(subPart)) {
                return BODY;
            }
            return OTHER;
        }

        private static boolean isMultipartAlternative(Entity entity) {
            return entity.getMimeType().equalsIgnoreCase(MULTIPART_ALTERNATIVE);
        }

        private static boolean isAlternative(String subPart) {
            return subPart.equalsIgnoreCase(ALTERNATIVE_SUB_TYPE);
        }

    }
}
