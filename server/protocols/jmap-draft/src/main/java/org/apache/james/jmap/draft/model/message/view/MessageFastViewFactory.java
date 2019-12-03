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
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.Emailer;
import org.apache.james.mailbox.BlobManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MessageFastViewFactory implements MessageViewFactory<MessageFastView> {

    private static class FromMessageResultAndPreview implements Helpers.FromMessageResult<MessageFastView> {

        private final BlobManager blobManager;
        private final Map<MessageId, MessageFastViewPrecomputedProperties> fastProjections;

        private FromMessageResultAndPreview(BlobManager blobManager,
                                            Map<MessageId, MessageFastViewPrecomputedProperties> fastProjections) {
            this.blobManager = blobManager;
            this.fastProjections = fastProjections;
        }

        @Override
        public MessageFastView fromMessageResults(Collection<MessageResult> messageResults) throws MailboxException, IOException {
            Helpers.assertOneMessageId(messageResults);
            MessageResult firstMessageResult = messageResults.iterator().next();
            Preconditions.checkArgument(fastProjections.containsKey(firstMessageResult.getMessageId()),
                "FromMessageResultAndPreview usage requires a precomputed preview");
            MessageFastViewPrecomputedProperties messageProjection = fastProjections.get(firstMessageResult.getMessageId());
            List<MailboxId> mailboxIds = Helpers.getMailboxIds(messageResults);

            Message mimeMessage = Helpers.parse(firstMessageResult.getFullContent().getInputStream());

            return MessageFastView.builder()
                .id(firstMessageResult.getMessageId())
                .mailboxIds(mailboxIds)
                .blobId(BlobId.of(blobManager.toBlobId(firstMessageResult.getMessageId())))
                .threadId(firstMessageResult.getMessageId().serialize())
                .keywords(Helpers.getKeywords(messageResults))
                .size(firstMessageResult.getSize())
                .inReplyToMessageId(Helpers.getHeaderValue(mimeMessage, "in-reply-to"))
                .subject(Strings.nullToEmpty(mimeMessage.getSubject()).trim())
                .headers(Helpers.toHeaderMap(mimeMessage.getHeader().getFields()))
                .from(Emailer.firstFromMailboxList(mimeMessage.getFrom()))
                .to(Emailer.fromAddressList(mimeMessage.getTo()))
                .cc(Emailer.fromAddressList(mimeMessage.getCc()))
                .bcc(Emailer.fromAddressList(mimeMessage.getBcc()))
                .replyTo(Emailer.fromAddressList(mimeMessage.getReplyTo()))
                .date(Helpers.getDateFromHeaderOrInternalDateOtherwise(mimeMessage, firstMessageResult))
                .preview(messageProjection.getPreview())
                .hasAttachment(messageProjection.hasAttachment())
                .build();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageFastViewFactory.class);

    private final BlobManager blobManager;
    private final MessageIdManager messageIdManager;
    private final MessageFastViewProjection fastViewProjection;
    private final MessageFullViewFactory messageFullViewFactory;

    @Inject
    @VisibleForTesting
    MessageFastViewFactory(BlobManager blobManager, MessageIdManager messageIdManager, MessageFastViewProjection fastViewProjection,
                           MessageFullViewFactory messageFullViewFactory) {
        this.blobManager = blobManager;
        this.messageIdManager = messageIdManager;
        this.fastViewProjection = fastViewProjection;
        this.messageFullViewFactory = messageFullViewFactory;
    }

    @Override
    public List<MessageFastView> fromMessageIds(List<MessageId> messageIds, MailboxSession mailboxSession) {
        return Mono.from(fastViewProjection.retrieve(messageIds))
            .flatMapMany(fastProjections -> gatherMessageViews(messageIds, mailboxSession, fastProjections))
            .collectList()
            .subscribeOn(Schedulers.boundedElastic())
            .block();
    }

    private Flux<MessageFastView> gatherMessageViews(List<MessageId> messageIds, MailboxSession mailboxSession,
                                                     Map<MessageId, MessageFastViewPrecomputedProperties> fastProjections) {
        return Flux.merge(
                fetch(ImmutableList.copyOf(fastProjections.keySet()), FetchGroup.HEADERS, mailboxSession)
                    .map(messageResults -> Helpers.toMessageViews(messageResults, new FromMessageResultAndPreview(blobManager, fastProjections))),
                fetch(withoutPreviews(messageIds, fastProjections), FetchGroup.FULL_CONTENT, mailboxSession)
                    .map(messageResults -> Helpers.toMessageViews(messageResults, messageFullViewFactory::fromMessageResults)))
            .flatMap(Flux::fromIterable);
    }

    private List<MessageId> withoutPreviews(List<MessageId> messageIds, Map<MessageId, MessageFastViewPrecomputedProperties> fastProjections) {
        return ImmutableList.copyOf(Sets.difference(
            ImmutableSet.copyOf(messageIds),
            fastProjections.keySet()));
    }

    private Mono<List<MessageResult>> fetch(List<MessageId> messageIds, FetchGroup fetchGroup, MailboxSession mailboxSession) {
        return Mono.fromCallable(() -> messageIdManager.getMessages(messageIds, fetchGroup, mailboxSession))
            .onErrorResume(MailboxException.class, ex -> {
                LOGGER.error("cannot read messages {}", messageIds, ex);
                return Mono.just(ImmutableList.of());
            });
    }
}
