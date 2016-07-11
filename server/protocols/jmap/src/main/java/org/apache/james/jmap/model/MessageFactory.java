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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.james.jmap.model.message.EMailer;
import org.apache.james.jmap.model.message.IndexableMessage;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MessageAttachment;
import org.apache.james.mailbox.store.mail.model.impl.Cid;
import org.apache.james.util.streams.ImmutableCollectors;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

public class MessageFactory {

    public static final String NO_SUBJECT = "(No subject)";
    public static final String MULTIVALUED_HEADERS_SEPARATOR = ", ";
    public static final ZoneId UTC_ZONE_ID = ZoneId.of("Z");

    private final MessagePreviewGenerator messagePreview;

    @Inject
    public MessageFactory(MessagePreviewGenerator messagePreview) {
        this.messagePreview = messagePreview;
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

    private String getPreview(IndexableMessage im) {
        Optional<String> bodyHtml = im.getBodyHtml();
        if (bodyHtml.isPresent()) {
            return messagePreview.forHTMLBody(bodyHtml);
        }
        return messagePreview.forTextBody(im.getBodyText());
    }

    private String getSubject(IndexableMessage im) {
        return Optional.ofNullable(
                    Strings.emptyToNull(
                        im.getSubjects()
                            .stream()
                            .collect(Collectors.joining(MULTIVALUED_HEADERS_SEPARATOR))))
                .orElse(NO_SUBJECT);
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
                    .collect(ImmutableCollectors.toImmutableList());
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
                .collect(ImmutableCollectors.toImmutableMap(Map.Entry::getKey, x -> joinOnComma(x.getValue())));
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

    private String getTextBody(IndexableMessage im) {
        return im.getBodyText().map(Strings::emptyToNull).orElse(null);
    }

    private String getHtmlBody(IndexableMessage im) {
        return im.getBodyHtml().map(Strings::emptyToNull).orElse(null);
    }

    private List<Attachment> getAttachments(List<MessageAttachment> attachments) {
        return attachments.stream()
                .map(this::fromMailboxAttachment)
                .collect(ImmutableCollectors.toImmutableList());
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