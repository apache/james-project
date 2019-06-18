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
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.dom.field.ContentIdField;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.dom.field.ParsedField;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.util.MimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class MessageParser {

    private static final String TEXT_MEDIA_TYPE = "text";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_ID = "Content-ID";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final List<String> ATTACHMENT_CONTENT_DISPOSITIONS = ImmutableList.of(
            ContentDispositionField.DISPOSITION_TYPE_ATTACHMENT.toLowerCase(Locale.US),
            ContentDispositionField.DISPOSITION_TYPE_INLINE.toLowerCase(Locale.US));
    private static final String TEXT_CALENDAR = "text/calendar";
    private static final ImmutableList<String> ATTACHMENT_CONTENT_TYPES = ImmutableList.of(
        "application/pgp-signature",
        "message/disposition-notification",
        TEXT_CALENDAR);
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageParser.class);

    private final Cid.CidParser cidParser;

    public MessageParser() {
        cidParser = Cid.parser()
            .relaxed()
            .unwrap();
    }

    public List<MessageAttachment> retrieveAttachments(InputStream fullContent) throws MimeException, IOException {
        DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
        defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
        Message message = defaultMessageBuilder.parseMessage(fullContent);
        Body body = message.getBody();
        try {
            if (isAttachment(message, Context.BODY)) {
                return ImmutableList.of(retrieveAttachment(message));
            }

            if (body instanceof Multipart) {
                Multipart multipartBody = (Multipart) body;
                return listAttachments(multipartBody, Context.fromSubType(multipartBody.getSubType()))
                    .collect(Guavate.toImmutableList());
            } else {
                return ImmutableList.of();
            }
        } finally {
            body.dispose();
        }
    }

    private Stream<MessageAttachment> listAttachments(Multipart multipart, Context context) {
        return multipart.getBodyParts()
            .stream()
            .flatMap(entity -> listAttachments(entity, context));
    }

    private Stream<MessageAttachment> listAttachments(Entity entity, Context context) {
        if (isMultipart(entity)) {
            return listAttachments((Multipart) entity.getBody(), Context.fromEntity(entity));
        }
        if (isAttachment(entity, context)) {
            try {
                return Stream.of(retrieveAttachment(entity));
            } catch (IllegalStateException e) {
                LOGGER.warn("The attachment is not well-formed", e);
            } catch (IOException e) {
                LOGGER.warn("There is an error when retrieving attachment", e);
            }
        }
        return Stream.empty();
    }

    private MessageAttachment retrieveAttachment(Entity entity) throws IOException {
        Optional<ContentTypeField> contentTypeField = getContentTypeField(entity);
        Optional<ContentDispositionField> contentDispositionField = getContentDispositionField(entity);
        Optional<String> contentType = contentType(contentTypeField);
        Optional<String> name = name(contentTypeField, contentDispositionField);
        Optional<Cid> cid = cid(readHeader(entity, CONTENT_ID, ContentIdField.class));
        boolean isInline = isInline(readHeader(entity, CONTENT_DISPOSITION, ContentDispositionField.class)) && cid.isPresent();

        return MessageAttachment.builder()
                .attachment(Attachment.builder()
                    .bytes(getBytes(entity.getBody()))
                    .type(contentType.orElse(DEFAULT_CONTENT_TYPE))
                    .build())
                .name(name.orElse(null))
                .cid(cid.orElse(null))
                .isInline(isInline)
                .build();
    }

    private <T extends ParsedField> Optional<T> readHeader(Entity entity, String headerName, Class<T> clazz) {
        return castField(entity.getHeader().getField(headerName), clazz);
    }

    private Optional<ContentTypeField> getContentTypeField(Entity entity) {
        return castField(entity.getHeader().getField(CONTENT_TYPE), ContentTypeField.class);
    }

    private Optional<ContentDispositionField> getContentDispositionField(Entity entity) {
        return castField(entity.getHeader().getField(CONTENT_DISPOSITION), ContentDispositionField.class);
    }

    @SuppressWarnings("unchecked")
    private <U extends ParsedField> Optional<U> castField(Field field, Class<U> clazz) {
        if (field == null || !clazz.isInstance(field)) {
            return Optional.empty();
        }
        return Optional.of((U) field);
    }

    private Optional<String> contentType(Optional<ContentTypeField> contentTypeField) {
        return contentTypeField.map(ContentTypeField::getMimeType);
    }

    private Optional<String> name(Optional<ContentTypeField> contentTypeField, Optional<ContentDispositionField> contentDispositionField) {
        return contentTypeField
            .map(field -> Optional.ofNullable(field.getParameter("name")))
            .filter(Optional::isPresent)
            .orElseGet(() -> contentDispositionField.map(ContentDispositionField::getFilename))
            .map(MimeUtil::unscrambleHeaderValue);
    }

    private Optional<Cid> cid(Optional<ContentIdField> contentIdField) {
        return contentIdField.map(ContentIdField::getId)
            .flatMap(cidParser::parse);
    }

    private boolean isMultipart(Entity entity) {
        return entity.isMultipart() && entity.getBody() instanceof Multipart;
    }

    private boolean isInline(Optional<ContentDispositionField> contentDispositionField) {
        return contentDispositionField.map(ContentDispositionField::isInline)
            .orElse(false);
    }

    private boolean isAttachment(Entity part, Context context) {
        if (context == Context.BODY && isTextPart(part)) {
            return false;
        }
        return attachmentDispositionCriterion(part) || attachmentContentTypeCriterion(part) || hadCID(part);
    }

    private boolean isTextPart(Entity part) {
        return getContentTypeField(part)
            .filter(header -> !ATTACHMENT_CONTENT_TYPES.contains(header.getMimeType()))
            .map(ContentTypeField::getMediaType)
            .map(TEXT_MEDIA_TYPE::equals)
            .orElse(false);
    }

    private boolean attachmentContentTypeCriterion(Entity part) {
        return getContentTypeField(part)
            .map(ContentTypeField::getMimeType)
            .map(dispositionType -> dispositionType.toLowerCase(Locale.US))
            .map(ATTACHMENT_CONTENT_TYPES::contains)
            .orElse(false);
    }

    private boolean attachmentDispositionCriterion(Entity part) {
        return getContentDispositionField(part)
            .map(ContentDispositionField::getDispositionType)
            .map(dispositionType -> dispositionType.toLowerCase(Locale.US))
            .map(ATTACHMENT_CONTENT_DISPOSITIONS::contains)
            .orElse(false);
    }

    private boolean hadCID(Entity part) {
        return readHeader(part, CONTENT_ID, ContentIdField.class).isPresent();
    }

    private byte[] getBytes(Body body) throws IOException {
        DefaultMessageWriter messageWriter = new DefaultMessageWriter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        messageWriter.writeBody(body, out);
        return out.toByteArray();
    }

    private enum Context {
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
