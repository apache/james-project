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
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.inject.Inject;

import jakarta.mail.internet.SharedInputStream;

import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.draft.methods.BlobManager;
import org.apache.james.jmap.draft.model.Attachment;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.Emailer;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.james.util.mime.MessageContentExtractor.MessageContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MessageFullViewFactory implements MessageViewFactory<MessageFullView> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageFullViewFactory.class);

    private final BlobManager blobManager;
    private final MessageContentExtractor messageContentExtractor;
    private final HtmlTextExtractor htmlTextExtractor;
    private final MessageIdManager messageIdManager;
    private final MessageFastViewProjection fastViewProjection;

    @Inject
    public MessageFullViewFactory(BlobManager blobManager, MessageContentExtractor messageContentExtractor,
                                  HtmlTextExtractor htmlTextExtractor, MessageIdManager messageIdManager,
                                  MessageFastViewProjection fastViewProjection) {
        this.blobManager = blobManager;
        this.messageContentExtractor = messageContentExtractor;
        this.htmlTextExtractor = htmlTextExtractor;
        this.messageIdManager = messageIdManager;
        this.fastViewProjection = fastViewProjection;
    }

    @Override
    public Flux<MessageFullView> fromMessageIds(List<MessageId> messageIds, MailboxSession mailboxSession) {
        Flux<MessageResult> messages = Flux.from(messageIdManager.getMessagesReactive(messageIds, FetchGroup.FULL_CONTENT, mailboxSession));
        return Helpers.toMessageViews(messages, this::fromMessageResults);
    }

    public Mono<MessageFullView> fromMetaDataWithContent(MetaDataWithContent message) {
        return Mono.fromCallable(() -> Helpers.retrieveMessage(message))
            .flatMap(Throwing.function(mimeMessage -> fromMetaDataWithContent(message, mimeMessage)));
    }

    private Mono<MessageFullView> fromMetaDataWithContent(MetaDataWithContent message, Message mimeMessage) throws IOException {
        MessageContent messageContent = messageContentExtractor.extract(mimeMessage);
        Optional<String> htmlBody = messageContent.getHtmlBody();
        Optional<String> textBody = computeTextBodyIfNeeded(messageContent);

        return retrieveProjection(messageContent, message.getMessageId(),
                () -> MessageFullView.hasAttachment(getAttachments(message.getAttachments())))
            .map(messageProjection -> instanciateMessageFullView(message, mimeMessage, htmlBody, textBody, messageProjection));
    }

    private MessageFullView instanciateMessageFullView(MetaDataWithContent message, Message mimeMessage, Optional<String> htmlBody, Optional<String> textBody, MessageFastViewPrecomputedProperties messageProjection) {
        return MessageFullView.builder()
                .id(message.getMessageId())
                .blobId(BlobId.of(message.getMessageId()))
                .threadId(message.getMessageId().serialize())
                .mailboxIds(message.getMailboxIds())
                .inReplyToMessageId(Helpers.getHeaderValue(mimeMessage, "in-reply-to"))
                .keywords(message.getKeywords())
                .subject(Strings.nullToEmpty(mimeMessage.getSubject()).trim())
                .headers(Helpers.toHeaderMap(mimeMessage.getHeader()))
                .from(Emailer.firstFromMailboxList(mimeMessage.getFrom()))
                .to(Emailer.fromAddressList(mimeMessage.getTo()))
                .cc(Emailer.fromAddressList(mimeMessage.getCc()))
                .bcc(Emailer.fromAddressList(mimeMessage.getBcc()))
                .replyTo(Emailer.fromAddressList(mimeMessage.getReplyTo()))
                .size(message.getSize())
                .date(getDateFromHeaderOrInternalDateOtherwise(mimeMessage, message))
                .textBody(textBody)
                .htmlBody(htmlBody)
                .preview(messageProjection.getPreview())
                .attachments(getAttachments(message.getAttachments()))
                .build();
    }

    private Mono<MessageFastViewPrecomputedProperties> retrieveProjection(MessageContent messageContent,
                                                                    MessageId messageId, Supplier<Boolean> hasAttachments) {
        return Mono.from(fastViewProjection.retrieve(messageId))
            .onErrorResume(throwable -> fallBackToCompute(messageContent, hasAttachments, throwable))
            .switchIfEmpty(computeThenStoreAsync(messageContent, messageId, hasAttachments));
    }

    private Mono<MessageFastViewPrecomputedProperties> fallBackToCompute(MessageContent messageContent,
                                                                         Supplier<Boolean> hasAttachments,
                                                                         Throwable throwable) {
        LOGGER.error("Cannot retrieve the computed preview from MessageFastViewProjection", throwable);
        return computeProjection(messageContent, hasAttachments);
    }

    private Mono<MessageFastViewPrecomputedProperties> computeThenStoreAsync(MessageContent messageContent,
                                                                             MessageId messageId,
                                                                             Supplier<Boolean> hasAttachments) {
        return computeProjection(messageContent, hasAttachments)
            .doOnNext(projection -> Mono.from(fastViewProjection.store(messageId, projection))
                .doOnError(throwable -> LOGGER.error("Cannot store the projection to MessageFastViewProjection", throwable))
                .subscribeOn(Schedulers.parallel())
                .subscribe());
    }

    private Mono<MessageFastViewPrecomputedProperties> computeProjection(MessageContent messageContent, Supplier<Boolean> hasAttachments) {
        return Mono.fromCallable(() -> mainTextContent(messageContent))
            .handle(ReactorUtils.publishIfPresent())
            .map(Preview::compute)
            .defaultIfEmpty(Preview.EMPTY)
            .map(extractedPreview -> MessageFastViewPrecomputedProperties.builder()
                .preview(extractedPreview)
                .hasAttachment(hasAttachments.get())
                .build());
    }

    private Instant getDateFromHeaderOrInternalDateOtherwise(Message mimeMessage, MessageFullViewFactory.MetaDataWithContent message) {
        return Optional.ofNullable(mimeMessage.getDate())
            .map(Date::toInstant)
            .orElse(message.getInternalDate());
    }

    Mono<MessageFullView> fromMessageResults(Collection<MessageResult> messageResults) {
        try {
            return fromMetaDataWithContent(toMetaDataWithContent(messageResults));
        } catch (MailboxException e) {
            return Mono.error(e);
        }
    }

    private MetaDataWithContent toMetaDataWithContent(Collection<MessageResult> messageResults) throws MailboxException {
        Helpers.assertOneMessageId(messageResults);

        MessageResult firstMessageResult = messageResults.iterator().next();
        Set<MailboxId> mailboxIds = Helpers.getMailboxIds(messageResults);
        Keywords keywords = Helpers.getKeywords(messageResults);

        return MetaDataWithContent.builderFromMessageResult(firstMessageResult)
            .messageId(firstMessageResult.getMessageId())
            .mailboxIds(mailboxIds)
            .keywords(keywords)
            .build();
    }

    private Optional<String> computeTextBodyIfNeeded(MessageContent messageContent) {
        return messageContent.getTextBody()
            .or(() -> messageContent.extractMainTextContent(htmlTextExtractor));
    }

    private Optional<String> mainTextContent(MessageContent messageContent) {
        return messageContent.getHtmlBody()
            .map(htmlTextExtractor::toPlainText)
            .filter(Predicate.not(Strings::isNullOrEmpty))
            .or(messageContent::getTextBody);
    }
    
    private List<Attachment> getAttachments(List<MessageAttachmentMetadata> attachments) {
        return attachments.stream()
                .map(this::fromMailboxAttachment)
                .collect(ImmutableList.toImmutableList());
    }

    private Attachment fromMailboxAttachment(MessageAttachmentMetadata attachment) {
        return Attachment.builder()
                    .blobId(BlobId.of(attachment.getAttachmentId()))
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
            private Keywords keywords;
            private Long size;
            private Instant internalDate;
            private InputStream content;
            private SharedInputStream sharedContent;
            private List<MessageAttachmentMetadata> attachments;
            private Set<MailboxId> mailboxIds = Sets.newHashSet();
            private MessageId messageId;
            private Optional<Message> message;

            public Builder() {
                this.message = Optional.empty();
            }

            public Builder uid(MessageUid uid) {
                this.uid = uid;
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
            
            public Builder attachments(List<MessageAttachmentMetadata> attachments) {
                this.attachments = attachments;
                return this;
            }
            
            public Builder mailboxId(MailboxId mailboxId) {
                this.mailboxIds.add(mailboxId);
                return this;
            }

            public Builder message(Message message) {
                this.message = Optional.of(message);
                return this;
            }

            public Builder mailboxIds(Set<MailboxId> mailboxIds) {
                this.mailboxIds.addAll(mailboxIds);
                return this;
            }
            
            public Builder messageId(MessageId messageId) {
                this.messageId = messageId;
                return this;
            }
            
            public MetaDataWithContent build() {
                Preconditions.checkArgument(uid != null);
                Preconditions.checkArgument(keywords != null);
                Preconditions.checkArgument(size != null);
                Preconditions.checkArgument(internalDate != null);
                Preconditions.checkArgument(content != null ^ sharedContent != null);
                Preconditions.checkArgument(attachments != null);
                Preconditions.checkArgument(mailboxIds != null);
                Preconditions.checkArgument(messageId != null);

                return new MetaDataWithContent(uid, keywords, size, internalDate, content, sharedContent, attachments, mailboxIds, messageId, message);
            }
        }

        private final MessageUid uid;
        private final Keywords keywords;
        private final long size;
        private final Instant internalDate;
        private final InputStream content;
        private final SharedInputStream sharedContent;
        private final List<MessageAttachmentMetadata> attachments;
        private final Set<MailboxId> mailboxIds;
        private final MessageId messageId;
        private final Optional<Message> message;

        private MetaDataWithContent(MessageUid uid,
                                    Keywords keywords,
                                    long size,
                                    Instant internalDate,
                                    InputStream content,
                                    SharedInputStream sharedContent,
                                    List<MessageAttachmentMetadata> attachments,
                                    Set<MailboxId> mailboxIds,
                                    MessageId messageId, Optional<Message> message) {
            this.uid = uid;
            this.keywords = keywords;
            this.size = size;
            this.internalDate = internalDate;
            this.content = content;
            this.sharedContent = sharedContent;
            this.attachments = attachments;
            this.mailboxIds = mailboxIds;
            this.messageId = messageId;
            this.message = message;
        }

        public Optional<Message> getMessage() {
            return message;
        }

        public MessageUid getUid() {
            return uid;
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

        public List<MessageAttachmentMetadata> getAttachments() {
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