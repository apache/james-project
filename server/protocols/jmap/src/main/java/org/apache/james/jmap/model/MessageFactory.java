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
package org.apache.james.jmap.model;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.jmap.model.MessageContentExtractor.MessageContent;
import org.apache.james.jmap.model.message.EMailer;
import org.apache.james.jmap.model.message.IndexableMessage;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.message.MessageBuilder;
import org.apache.james.mime4j.stream.Field;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class MessageFactory {

    public static final String MULTIVALUED_HEADERS_SEPARATOR = ", ";
    public static final ZoneId UTC_ZONE_ID = ZoneId.of("Z");

    private final MessagePreviewGenerator messagePreview;
    private final MessageContentExtractor messageContentExtractor;

    @Inject
    public MessageFactory(MessagePreviewGenerator messagePreview, MessageContentExtractor messageContentExtractor) {
        this.messagePreview = messagePreview;
        this.messageContentExtractor = messageContentExtractor;
    }

    public Message fromMessageResult(MessageResult messageResult,
            List<MessageAttachment> attachments,
            MailboxId mailboxId,
            Function<Long, MessageId> uidToMessageId) throws MailboxException {
        MessageId messageId = uidToMessageId.apply(messageResult.getUid());

        MessageBuilder parsedMessageResult;
        MessageContent messageContent;
        try {
            parsedMessageResult = MessageBuilder.read(messageResult.getFullContent().getInputStream());
            messageContent = messageContentExtractor.extract(parsedMessageResult.build());
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message: " + e.getMessage(), e);
        }

        return Message.builder()
                .id(messageId)
                .blobId(BlobId.of(String.valueOf(messageResult.getUid())))
                .threadId(messageId.serialize())
                .mailboxIds(ImmutableList.of(mailboxId.serialize()))
                .inReplyToMessageId(getHeader(parsedMessageResult, "in-reply-to"))
                .isUnread(! messageResult.getFlags().contains(Flags.Flag.SEEN))
                .isFlagged(messageResult.getFlags().contains(Flags.Flag.FLAGGED))
                .isAnswered(messageResult.getFlags().contains(Flags.Flag.ANSWERED))
                .isDraft(messageResult.getFlags().contains(Flags.Flag.DRAFT))
                .subject(Strings.nullToEmpty(parsedMessageResult.getSubject()))
                .headers(toMap(parsedMessageResult.getFields()))
                .from(firstFromMailboxList(parsedMessageResult.getFrom()))
                .to(fromAddressList(parsedMessageResult.getTo()))
                .cc(fromAddressList(parsedMessageResult.getCc()))
                .bcc(fromAddressList(parsedMessageResult.getBcc()))
                .replyTo(fromAddressList(parsedMessageResult.getReplyTo()))
                .size(parsedMessageResult.getSize())
                .date(toZonedDateTime(messageResult.getInternalDate()))
                .textBody(messageContent.getTextBody().orElse(null))
                .htmlBody(messageContent.getHtmlBody().orElse(null))
                .preview(getPreview(messageContent))
                .attachments(getAttachments(attachments))
                .build();
    }

    public Message fromMailboxMessage(MailboxMessage mailboxMessage,
            List<MessageAttachment> attachments,
            Function<Long, MessageId> uidToMessageId) {

        IndexableMessage im = IndexableMessage.from(mailboxMessage, new DefaultTextExtractor(), UTC_ZONE_ID);
        MessageId messageId = uidToMessageId.apply(im.getId());
        return Message.builder()
                .id(messageId)
                .blobId(BlobId.of(String.valueOf(im.getId())))
                .threadId(messageId.serialize())
                .mailboxIds(ImmutableList.of(im.getMailboxId()))
                .inReplyToMessageId(getHeaderAsSingleValue(im, "in-reply-to"))
                .isUnread(im.isUnRead())
                .isFlagged(im.isFlagged())
                .isAnswered(im.isAnswered())
                .isDraft(im.isDraft())
                .subject(getSubject(im))
                .headers(toMap(im.getHeaders()))
                .from(firstElasticSearchEmailers(im.getFrom()))
                .to(fromElasticSearchEmailers(im.getTo()))
                .cc(fromElasticSearchEmailers(im.getCc()))
                .bcc(fromElasticSearchEmailers(im.getBcc()))
                .replyTo(fromElasticSearchEmailers(im.getReplyTo()))
                .size(im.getSize())
                .date(getInternalDate(mailboxMessage, im))
                .preview(getPreview(im))
                .textBody(getTextBody(im))
                .htmlBody(getHtmlBody(im))
                .attachments(getAttachments(attachments))
                .build();
    }

    private String getPreview(MessageContent messageContent) {
        if (messageContent.getHtmlBody().isPresent()) {
            return messagePreview.forHTMLBody(messageContent.getHtmlBody());
        }
        return messagePreview.forTextBody(messageContent.getTextBody());
    }

    private String getPreview(IndexableMessage im) {
        Optional<String> bodyHtml = im.getBodyHtml();
        if (bodyHtml.isPresent()) {
            return messagePreview.forHTMLBody(bodyHtml);
        }
        return messagePreview.forTextBody(im.getBodyText());
    }

    private String getSubject(IndexableMessage im) {
        return im.getSubjects()
                    .stream()
                    .map(String::trim)
                    .collect(Collectors.joining(MULTIVALUED_HEADERS_SEPARATOR));
    }
    
    private Emailer firstFromMailboxList(MailboxList list) {
        if (list == null) {
            return null;
        }
        return list.stream()
                .map(this::fromMailbox)
                .findFirst()
                .orElse(null);
    }
    
    private ImmutableList<Emailer> fromAddressList(AddressList list) {
        if (list == null) {
            return ImmutableList.of();
        }
        return list.flatten()
            .stream()
            .map(this::fromMailbox)
            .collect(Guavate.toImmutableList());
    }
    
    private Emailer fromMailbox(Mailbox mailbox) {
        return Emailer.builder()
                    .name(getNameOrAddress(mailbox))
                    .email(mailbox.getAddress())
                    .build();
    }

    private String getNameOrAddress(Mailbox mailbox) {
        if (mailbox.getName() != null) {
            return mailbox.getName();
        }
        return mailbox.getAddress();
    }

    private Emailer firstElasticSearchEmailers(Set<EMailer> emailers) {
        return emailers.stream()
                    .findFirst()
                    .map(this::fromElasticSearchEmailer)
                    .orElse(null);
    }
    
    private ImmutableList<Emailer> fromElasticSearchEmailers(Set<EMailer> emailers) {
        return emailers.stream()
                    .map(this::fromElasticSearchEmailer)
                    .collect(Guavate.toImmutableList());
    }
    
    private Emailer fromElasticSearchEmailer(EMailer emailer) {
        return Emailer.builder()
                    .name(emailer.getName())
                    .email(emailer.getAddress())
                    .build();
    }
    
    private ImmutableMap<String, String> toMap(Multimap<String, String> multimap) {
        return multimap
                .asMap()
                .entrySet()
                .stream()
                .collect(Guavate.toImmutableMap(Map.Entry::getKey, x -> joinOnComma(x.getValue())));
    }
    
    private ImmutableMap<String, String> toMap(List<Field> fields) {
        Function<Entry<String, Collection<Field>>, String> bodyConcatenator = fieldListEntry -> fieldListEntry.getValue()
                .stream()
                .map(Field::getBody)
                .collect(Collectors.toList())
                .stream()
                .collect(Collectors.joining(","));
        return Multimaps.index(fields, Field::getName)
                .asMap()
                .entrySet()
                .stream()
                .collect(Guavate.toImmutableMap(Map.Entry::getKey, bodyConcatenator));
    }
    
    private String getHeader(MessageBuilder message, String header) {
        Field field = message.getField(header);
        if (field == null) {
            return null;
        }
        return field.getBody();
    }
    
    private String getHeaderAsSingleValue(IndexableMessage im, String header) {
        return Strings.emptyToNull(joinOnComma(im.getHeaders().get(header)));
    }
    
    private String joinOnComma(Iterable<String> iterable) {
        return String.join(MULTIVALUED_HEADERS_SEPARATOR, iterable);
    }
    
    private ZonedDateTime getInternalDate(MailboxMessage mailboxMessage, IndexableMessage im) {
        return ZonedDateTime.ofInstant(mailboxMessage.getInternalDate().toInstant(), UTC_ZONE_ID);
    }

    private ZonedDateTime toZonedDateTime(Date date) {
        return ZonedDateTime.ofInstant(date.toInstant(), UTC_ZONE_ID);
    }

    private String getTextBody(IndexableMessage im) {
        return im.getBodyText().map(Strings::emptyToNull).orElse(null);
    }

    private String getHtmlBody(IndexableMessage im) {
        return im.getBodyHtml().map(Strings::emptyToNull).orElse(null);
    }

    private List<Attachment> getAttachments(List<MessageAttachment> attachments) {
        return attachments.stream()
                .map(this::fromMailboxAttachment)
                .collect(Guavate.toImmutableList());
    }

    private Attachment fromMailboxAttachment(MessageAttachment attachment) {
        return Attachment.builder()
                    .blobId(BlobId.of(attachment.getAttachmentId().getId()))
                    .type(attachment.getAttachment().getType())
                    .size(attachment.getAttachment().getSize())
                    .name(attachment.getName().orNull())
                    .cid(attachment.getCid().transform(Cid::getValue).orNull())
                    .isInline(attachment.isInline())
                    .build();
    }
}