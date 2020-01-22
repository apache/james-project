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

package org.apache.james.jmap.draft.methods;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.james.jmap.draft.model.CreationMessage;
import org.apache.james.jmap.draft.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.draft.model.message.view.MessageViewFactory;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.codec.EncoderUtil.Usage;
import org.apache.james.mime4j.dom.FieldParser;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.dom.field.FieldName;
import org.apache.james.mime4j.dom.field.UnstructuredField;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.field.UnstructuredFieldImpl;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.stream.NameValuePair;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.mime4j.util.MimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;

public class MIMEMessageConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MIMEMessageConverter.class);

    private static final String PLAIN_TEXT_MEDIA_TYPE = MediaType.PLAIN_TEXT_UTF_8.withoutParameters().toString();
    private static final String HTML_MEDIA_TYPE = MediaType.HTML_UTF_8.withoutParameters().toString();
    private static final NameValuePair UTF_8_CHARSET = new NameValuePair("charset", StandardCharsets.UTF_8.name());
    private static final String ALTERNATIVE_SUB_TYPE = "alternative";
    private static final String MIXED_SUB_TYPE = "mixed";
    private static final String RELATED_SUB_TYPE = "related";
    private static final String FIELD_PARAMETERS_SEPARATOR = ";";
    private static final String QUOTED_PRINTABLE = "quoted-printable";
    private static final String BASE64 = "base64";
    private static final String IN_REPLY_TO_HEADER = "In-Reply-To";
    private static final List<String> COMPUTED_HEADERS = ImmutableList.of(
            FieldName.FROM,
            FieldName.SENDER,
            FieldName.REPLY_TO,
            FieldName.TO,
            FieldName.CC,
            FieldName.BCC,
            FieldName.SUBJECT,
            FieldName.MESSAGE_ID,
            FieldName.DATE,
            FieldName.CONTENT_TYPE,
            FieldName.MIME_VERSION,
            FieldName.CONTENT_TRANSFER_ENCODING);
    private static final List<String> LOWERCASED_COMPUTED_HEADERS = COMPUTED_HEADERS.stream()
            .map(s -> s.toLowerCase(Locale.ENGLISH))
            .collect(Guavate.toImmutableList());

    private final BasicBodyFactory bodyFactory;
    private final AttachmentContentLoader attachmentContentLoader;

    @Inject
    public MIMEMessageConverter(AttachmentContentLoader attachmentContentLoader) {
        this.attachmentContentLoader = attachmentContentLoader;
        this.bodyFactory = new BasicBodyFactory();
    }

    public byte[] convert(ValueWithId.CreationMessageEntry creationMessageEntry, ImmutableList<MessageAttachment> messageAttachments, MailboxSession session) {
        return asBytes(convertToMime(creationMessageEntry, messageAttachments, session));
    }

    public byte[] asBytes(Message message) {
        try {
            return DefaultMessageWriter.asBytes(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting Message convertToMime(ValueWithId.CreationMessageEntry creationMessageEntry, ImmutableList<MessageAttachment> messageAttachments, MailboxSession session) {
        if (creationMessageEntry == null || creationMessageEntry.getValue() == null) {
            throw new IllegalArgumentException("creationMessageEntry is either null or has null message");
        }

        Message.Builder messageBuilder = Message.Builder.of();
        if (isMultipart(creationMessageEntry.getValue(), messageAttachments)) {
            messageBuilder.setBody(createMultipart(creationMessageEntry.getValue(), messageAttachments, session));
        } else {
            messageBuilder.setBody(createTextBody(creationMessageEntry.getValue()))
                .setContentTransferEncoding(QUOTED_PRINTABLE);
        }
        buildMimeHeaders(messageBuilder, creationMessageEntry.getValue(), messageAttachments);
        return messageBuilder.build();
    }

    private void buildMimeHeaders(Message.Builder messageBuilder, CreationMessage newMessage, ImmutableList<MessageAttachment> messageAttachments) {
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
        messageBuilder.setMessageId(generateUniqueMessageId(fromAddress));

        // note that date conversion probably lose milliseconds!
        messageBuilder.setDate(Date.from(newMessage.getDate().toInstant()), TimeZone.getTimeZone(newMessage.getDate().getZone()));
        newMessage.getInReplyToMessageId()
            .ifPresent(id -> addHeader(messageBuilder, IN_REPLY_TO_HEADER, id));
        if (!isMultipart(newMessage, messageAttachments)) {
            newMessage.getHtmlBody().ifPresent(x -> messageBuilder.setContentType(HTML_MEDIA_TYPE, UTF_8_CHARSET));
        }
        newMessage.getHeaders().entrySet().stream()
            .filter(header -> ! header.getKey().trim().isEmpty())
            .filter(header -> ! LOWERCASED_COMPUTED_HEADERS.contains(header.getKey().toLowerCase(Locale.ENGLISH)))
            .forEach(header -> addMultivaluedHeader(messageBuilder, header.getKey(), header.getValue()));
    }

    private String generateUniqueMessageId(Optional<Mailbox> fromAddress) {
        String noDomain = null;
        return MimeUtil.createUniqueMessageId(fromAddress
            .map(Mailbox::getDomain)
            .orElse(noDomain));
    }

    private void addMultivaluedHeader(Message.Builder messageBuilder, String fieldName, String multipleValues) {
        Splitter.on(MessageViewFactory.JMAP_MULTIVALUED_FIELD_DELIMITER).split(multipleValues)
            .forEach(value -> addHeader(messageBuilder, fieldName, value));
    }

    private void addHeader(Message.Builder messageBuilder, String fieldName, String value) {
        FieldParser<UnstructuredField> parser = UnstructuredFieldImpl.PARSER;
        RawField rawField = new RawField(fieldName, value);
        messageBuilder.addField(parser.parse(rawField, DecodeMonitor.SILENT));
    }

    private boolean isMultipart(CreationMessage newMessage, ImmutableList<MessageAttachment> messageAttachments) {
        return (newMessage.getTextBody().isPresent() && newMessage.getHtmlBody().isPresent())
                || hasAttachment(messageAttachments);
    }

    private boolean hasAttachment(ImmutableList<MessageAttachment> messageAttachments) {
        return !messageAttachments.isEmpty();
    }

    private TextBody createTextBody(CreationMessage newMessage) {
        String body = newMessage.getHtmlBody()
                        .orElse(newMessage.getTextBody()
                                .orElse(""));
        return bodyFactory.textBody(body, StandardCharsets.UTF_8);
    }

    private Multipart createMultipart(CreationMessage newMessage, ImmutableList<MessageAttachment> messageAttachments, MailboxSession session) {
        try {
            if (hasAttachment(messageAttachments)) {
                return createMultipartWithAttachments(newMessage, messageAttachments, session);
            } else {
                return createMultipartAlternativeBody(newMessage);
            }
        } catch (IOException e) {
            LOGGER.error("Error while creating textBody \n{}\n or htmlBody \n{}", newMessage.getTextBody().get(), newMessage.getHtmlBody().get(), e);
            throw new RuntimeException(e);
        }
    }

    private Multipart createMultipartWithAttachments(CreationMessage newMessage, ImmutableList<MessageAttachment> messageAttachments, MailboxSession session) throws IOException {
        MultipartBuilder mixedMultipartBuilder = MultipartBuilder.create(MIXED_SUB_TYPE);
        List<MessageAttachment> inlineAttachments = messageAttachments.stream()
            .filter(MessageAttachment::isInline)
            .collect(Guavate.toImmutableList());
        List<MessageAttachment> besideAttachments = messageAttachments.stream()
            .filter(attachment -> !attachment.isInline())
            .collect(Guavate.toImmutableList());

        if (inlineAttachments.size() > 0) {
            mixedMultipartBuilder.addBodyPart(relatedInnerMessage(newMessage, inlineAttachments, session));
        } else {
            addBody(newMessage, mixedMultipartBuilder);
        }

        addAttachments(besideAttachments, mixedMultipartBuilder, session);

        return mixedMultipartBuilder.build();
    }

    private Message relatedInnerMessage(CreationMessage newMessage, List<MessageAttachment> inlines, MailboxSession session) throws IOException {
        MultipartBuilder relatedMultipart = MultipartBuilder.create(RELATED_SUB_TYPE);
        addBody(newMessage, relatedMultipart);

        return Message.Builder.of()
            .setBody(addAttachments(inlines, relatedMultipart, session)
                .build())
            .build();
    }

    private MultipartBuilder addAttachments(List<MessageAttachment> messageAttachments,
                                            MultipartBuilder multipartBuilder, MailboxSession session) {
        messageAttachments.forEach(addAttachment(multipartBuilder, session));

        return multipartBuilder;
    }
    
    private void addBody(CreationMessage newMessage, MultipartBuilder builder) throws IOException {
        if (newMessage.getHtmlBody().isPresent() && newMessage.getTextBody().isPresent()) {
            Multipart body = createMultipartAlternativeBody(newMessage);
            builder.addBodyPart(BodyPartBuilder.create().setBody(body));
        } else {
            addText(builder, newMessage.getTextBody());
            addHtml(builder, newMessage.getHtmlBody());
        }
    }

    private Multipart createMultipartAlternativeBody(CreationMessage newMessage) throws IOException {
        MultipartBuilder bodyBuilder = MultipartBuilder.create(ALTERNATIVE_SUB_TYPE);
        addText(bodyBuilder, newMessage.getTextBody());
        addHtml(bodyBuilder, newMessage.getHtmlBody());
        return bodyBuilder.build();
    }

    private void addText(MultipartBuilder builder, Optional<String> textBody) throws IOException {
        if (textBody.isPresent()) {
            builder.addBodyPart(BodyPartBuilder.create()
                .use(bodyFactory)
                .setBody(textBody.get(), StandardCharsets.UTF_8)
                .setContentType(PLAIN_TEXT_MEDIA_TYPE, UTF_8_CHARSET)
                .setContentTransferEncoding(QUOTED_PRINTABLE));
        }
    }

    private void addHtml(MultipartBuilder builder, Optional<String> htmlBody) throws IOException {
        if (htmlBody.isPresent()) {
            builder.addBodyPart(BodyPartBuilder.create()
                .use(bodyFactory)
                .setBody(htmlBody.get(), StandardCharsets.UTF_8)
                .setContentType(HTML_MEDIA_TYPE, UTF_8_CHARSET)
                .setContentTransferEncoding(QUOTED_PRINTABLE));
        }
    }

    private Consumer<MessageAttachment> addAttachment(MultipartBuilder builder, MailboxSession session) {
        return att -> { 
            try {
                builder.addBodyPart(attachmentBodyPart(att, session));
            } catch (IOException | AttachmentNotFoundException e) {
                LOGGER.error("Error while creating attachment", e);
                throw new RuntimeException(e);
            }
        };
    }

    private BodyPart attachmentBodyPart(MessageAttachment att, MailboxSession session) throws IOException, AttachmentNotFoundException {
        try (InputStream attachmentStream = attachmentContentLoader.load(att.getAttachment(), session)) {
            BodyPartBuilder builder = BodyPartBuilder.create()
                .use(bodyFactory)
                .setBody(new BasicBodyFactory().binaryBody(ByteStreams.toByteArray(attachmentStream)))
                .setField(contentTypeField(att))
                .setField(contentDispositionField(att.isInline()))
                .setContentTransferEncoding(BASE64);
            contentId(builder, att);
            return builder.build();
        }
    }

    private void contentId(BodyPartBuilder builder, MessageAttachment att) {
        if (att.getCid().isPresent()) {
            builder.setField(new RawField("Content-ID", att.getCid().get().getValue()));
        }
    }

    private ContentTypeField contentTypeField(MessageAttachment att) {
        Builder<String, String> parameters = ImmutableMap.builder();
        if (att.getName().isPresent()) {
            parameters.put("name", encode(att.getName().get()));
        }
        String type = att.getAttachment().getType();
        if (type.contains(FIELD_PARAMETERS_SEPARATOR)) {
            return Fields.contentType(contentTypeWithoutParameters(type), parameters.build());
        }
        return Fields.contentType(type, parameters.build());
    }

    private String encode(String name) {
        return EncoderUtil.encodeEncodedWord(name, Usage.TEXT_TOKEN);
    }

    private String contentTypeWithoutParameters(String type) {
        return Splitter.on(FIELD_PARAMETERS_SEPARATOR).splitToList(type).get(0);
    }

    private ContentDispositionField contentDispositionField(boolean isInline) {
        if (isInline) {
            return Fields.contentDisposition("inline");
        }
        return Fields.contentDisposition("attachment");
    }

    private Mailbox convertEmailToMimeHeader(DraftEmailer address) {
        if (!address.hasValidEmail()) {
            throw new IllegalArgumentException("address");
        }
        CreationMessage.EmailUserAndDomain emailUserAndDomain = address.getEmailUserAndDomain();
        return new Mailbox(address.getName().orElse(null), null, emailUserAndDomain.getUser().orElse(null), emailUserAndDomain.getDomain().orElse(null));
    }
}
