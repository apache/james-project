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
package org.apache.james.jmap.draft.model.message.view;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
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
import javax.mail.internet.SharedInputStream;

import org.apache.james.jmap.draft.model.Attachment;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.Emailer;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.MessagePreviewGenerator;
import org.apache.james.jmap.draft.utils.HtmlTextExtractor;
import org.apache.james.jmap.draft.utils.KeywordsCombiner;
import org.apache.james.mailbox.BlobManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.james.util.mime.MessageContentExtractor.MessageContent;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

public class MessageViewFactory {
    public static final String JMAP_MULTIVALUED_FIELD_DELIMITER = "\n";

    private static final KeywordsCombiner ACCUMULATOR = new KeywordsCombiner();

    private final BlobManager blobManager;
    private final MessagePreviewGenerator messagePreview;
    private final MessageContentExtractor messageContentExtractor;
    private final HtmlTextExtractor htmlTextExtractor;
    private final Keywords.KeywordsFactory keywordsFactory;

    @Inject
    public MessageViewFactory(BlobManager blobManager, MessagePreviewGenerator messagePreview, MessageContentExtractor messageContentExtractor,
                              HtmlTextExtractor htmlTextExtractor) {
        this.blobManager = blobManager;
        this.messagePreview = messagePreview;
        this.messageContentExtractor = messageContentExtractor;
        this.htmlTextExtractor = htmlTextExtractor;
        this.keywordsFactory = Keywords.lenientFactory();
    }

    public MessageFullView fromMessageResults(Collection<MessageResult> messageResults) throws MailboxException {
        return fromMetaDataWithContent(toMetaDataWithContent(messageResults));
    }

    public MessageFullView fromMetaDataWithContent(MetaDataWithContent message) throws MailboxException {
        org.apache.james.mime4j.dom.Message mimeMessage = parse(message);
        MessageContent messageContent = extractContent(mimeMessage);
        Optional<String> htmlBody = messageContent.getHtmlBody();
        Optional<String> mainTextContent = mainTextContent(messageContent);
        Optional<String> textBody = computeTextBodyIfNeeded(messageContent, mainTextContent);
        String preview = messagePreview.compute(mainTextContent);
        return MessageFullView.builder()
                .id(message.getMessageId())
                .blobId(BlobId.of(blobManager.toBlobId(message.getMessageId())))
                .threadId(message.getMessageId().serialize())
                .mailboxIds(message.getMailboxIds())
                .inReplyToMessageId(getHeader(mimeMessage, "in-reply-to"))
                .keywords(message.getKeywords())
                .subject(Strings.nullToEmpty(mimeMessage.getSubject()).trim())
                .headers(toMap(mimeMessage.getHeader().getFields()))
                .from(firstFromMailboxList(mimeMessage.getFrom()))
                .to(fromAddressList(mimeMessage.getTo()))
                .cc(fromAddressList(mimeMessage.getCc()))
                .bcc(fromAddressList(mimeMessage.getBcc()))
                .replyTo(fromAddressList(mimeMessage.getReplyTo()))
                .size(message.getSize())
                .date(getDateFromHeaderOrInternalDateOtherwise(mimeMessage, message))
                .textBody(textBody)
                .htmlBody(htmlBody)
                .preview(preview)
                .attachments(getAttachments(message.getAttachments()))
                .build();
    }

    private MetaDataWithContent toMetaDataWithContent(Collection<MessageResult> messageResults) throws MailboxException {
        Preconditions.checkArgument(!messageResults.isEmpty(), "MessageResults cannot be empty");
        Preconditions.checkArgument(hasOnlyOneMessageId(messageResults), "MessageResults need to share the same messageId");

        MessageResult firstMessageResult = messageResults.iterator().next();
        List<MailboxId> mailboxIds = messageResults.stream()
            .map(MessageResult::getMailboxId)
            .distinct()
            .collect(Guavate.toImmutableList());

        Keywords keywords = messageResults.stream()
            .map(MessageResult::getFlags)
            .map(keywordsFactory::fromFlags)
            .reduce(ACCUMULATOR)
            .get();

        return MetaDataWithContent.builderFromMessageResult(firstMessageResult)
            .messageId(firstMessageResult.getMessageId())
            .mailboxIds(mailboxIds)
            .keywords(keywords)
            .build();
    }

    private boolean hasOnlyOneMessageId(Collection<MessageResult> messageResults) {
        return messageResults
            .stream()
            .map(MessageResult::getMessageId)
            .distinct()
            .count() == 1;
    }

    private Instant getDateFromHeaderOrInternalDateOtherwise(org.apache.james.mime4j.dom.Message mimeMessage, MetaDataWithContent message) {
        return Optional.ofNullable(mimeMessage.getDate())
            .map(Date::toInstant)
            .orElse(message.getInternalDate());
    }

    private Optional<String> computeTextBodyIfNeeded(MessageContent messageContent, Optional<String> mainTextContent) {
        return messageContent.getTextBody()
            .map(Optional::of)
            .orElse(mainTextContent);
    }

    private Optional<String> mainTextContent(MessageContent messageContent) {
        return messageContent.getHtmlBody()
            .map(htmlTextExtractor::toPlainText)
            .filter(s -> !Strings.isNullOrEmpty(s))
            .map(Optional::of)
            .orElse(messageContent.getTextBody());
    }

    private org.apache.james.mime4j.dom.Message parse(MetaDataWithContent message) throws MailboxException {
        try {
            return org.apache.james.mime4j.dom.Message.Builder
                    .of()
                    .use(MimeConfig.PERMISSIVE)
                    .parse(message.getContent())
                    .build();
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message: " + e.getMessage(), e);
        }
    }

    private MessageContent extractContent(org.apache.james.mime4j.dom.Message mimeMessage) throws MailboxException {
        try {
            return messageContentExtractor.extract(mimeMessage);
        } catch (IOException e) {
            throw new MailboxException("Unable to extract content: " + e.getMessage(), e);
        }
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
            .allowInvalid()
            .build();
    }

    private String getNameOrAddress(Mailbox mailbox) {
        if (mailbox.getName() != null) {
            return mailbox.getName();
        }
        return mailbox.getAddress();
    }

    private ImmutableMap<String, String> toMap(List<Field> fields) {
        Function<Entry<String, Collection<Field>>, String> bodyConcatenator = fieldListEntry -> fieldListEntry.getValue()
                .stream()
                .map(Field::getBody)
                .map(MimeUtil::unscrambleHeaderValue)
                .collect(Collectors.toList())
                .stream()
                .collect(Collectors.joining(JMAP_MULTIVALUED_FIELD_DELIMITER));
        return Multimaps.index(fields, Field::getName)
                .asMap()
                .entrySet()
                .stream()
                .collect(Guavate.toImmutableMap(Map.Entry::getKey, bodyConcatenator));
    }
    
    private String getHeader(org.apache.james.mime4j.dom.Message message, String header) {
        Field field = message.getHeader().getField(header);
        if (field == null) {
            return null;
        }
        return field.getBody();
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
                    .name(attachment.getName())
                    .cid(attachment.getCid().map(Cid::getValue))
                    .isInline(attachment.isInline())
                    .build();
    }

    public static class MetaDataWithContent {
        public static Builder builder() {
            return new Builder();
        }
        
        public static Builder builderFromMessageResult(MessageResult messageResult) throws MailboxException {
            Builder builder = builder()
                .uid(messageResult.getUid())
                .modSeq(messageResult.getModSeq())
                .size(messageResult.getSize())
                .internalDate(messageResult.getInternalDate().toInstant())
                .attachments(messageResult.getLoadedAttachments())
                .mailboxId(messageResult.getMailboxId());
            try {
                return builder.content(messageResult.getFullContent().getInputStream());
            } catch (IOException e) {
                throw new MailboxException("Can't get message full content: " + e.getMessage(), e);
            }
        }
        
        public static class Builder {
            private MessageUid uid;
            private Long modSeq;
            private Keywords keywords;
            private Long size;
            private Instant internalDate;
            private InputStream content;
            private SharedInputStream sharedContent;
            private List<MessageAttachment> attachments;
            private Set<MailboxId> mailboxIds = Sets.newHashSet();
            private MessageId messageId;

            public Builder uid(MessageUid uid) {
                this.uid = uid;
                return this;
            }
            
            public Builder modSeq(long modSeq) {
                this.modSeq = modSeq;
                return this;
            }
            
            public Builder keywords(Keywords keywords) {
                this.keywords = keywords;
                return this;
            }

            public Builder size(long size) {
                this.size = size;
                return this;
            }
            
            public Builder internalDate(Instant internalDate) {
                this.internalDate = internalDate;
                return this;
            }
            
            public Builder content(InputStream content) {
                this.content = content;
                return this;
            }
            
            public Builder sharedContent(SharedInputStream sharedContent) {
                this.sharedContent = sharedContent;
                return this;
            }
            
            public Builder attachments(List<MessageAttachment> attachments) {
                this.attachments = attachments;
                return this;
            }
            
            public Builder mailboxId(MailboxId mailboxId) {
                this.mailboxIds.add(mailboxId);
                return this;
            }

            public Builder mailboxIds(List<MailboxId> mailboxIds) {
                this.mailboxIds.addAll(mailboxIds);
                return this;
            }
            
            public Builder messageId(MessageId messageId) {
                this.messageId = messageId;
                return this;
            }
            
            public MetaDataWithContent build() {
                Preconditions.checkArgument(uid != null);
                if (modSeq == null) {
                    modSeq = -1L;
                }
                Preconditions.checkArgument(keywords != null);
                Preconditions.checkArgument(size != null);
                Preconditions.checkArgument(internalDate != null);
                Preconditions.checkArgument(content != null ^ sharedContent != null);
                Preconditions.checkArgument(attachments != null);
                Preconditions.checkArgument(mailboxIds != null);
                Preconditions.checkArgument(messageId != null);
                return new MetaDataWithContent(uid, modSeq, keywords, size, internalDate, content, sharedContent, attachments, mailboxIds, messageId);
            }
        }

        private final MessageUid uid;
        private final long modSeq;
        private final Keywords keywords;
        private final long size;
        private final Instant internalDate;
        private final InputStream content;
        private final SharedInputStream sharedContent;
        private final List<MessageAttachment> attachments;
        private final Set<MailboxId> mailboxIds;
        private final MessageId messageId;

        private MetaDataWithContent(MessageUid uid,
                                    long modSeq,
                                    Keywords keywords,
                                    long size,
                                    Instant internalDate,
                                    InputStream content,
                                    SharedInputStream sharedContent,
                                    List<MessageAttachment> attachments,
                                    Set<MailboxId> mailboxIds,
                                    MessageId messageId) {
            this.uid = uid;
            this.modSeq = modSeq;
            this.keywords = keywords;
            this.size = size;
            this.internalDate = internalDate;
            this.content = content;
            this.sharedContent = sharedContent;
            this.attachments = attachments;
            this.mailboxIds = mailboxIds;
            this.messageId = messageId;
        }

        public MessageUid getUid() {
            return uid;
        }

        public long getModSeq() {
            return modSeq;
        }

        public Keywords getKeywords() {
            return keywords;
        }

        public long getSize() {
            return size;
        }

        public Instant getInternalDate() {
            return internalDate;
        }

        public InputStream getContent() {
            if (sharedContent != null) {
                long begin = 0;
                long allContent = -1;
                return sharedContent.newStream(begin, allContent);
            }
            return content;
        }

        public List<MessageAttachment> getAttachments() {
            return attachments;
        }

        public Set<MailboxId> getMailboxIds() {
            return mailboxIds;
        }

        public MessageId getMessageId() {
            return messageId;
        }

    }
}