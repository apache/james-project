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

package org.apache.james.jmap.model.message.view;

import java.util.Collection;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.jmap.methods.BlobManager;
import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.model.Emailer;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Message;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MessageHeaderViewFactory implements MessageViewFactory<MessageHeaderView> {
    private final BlobManager blobManager;
    private final MessageIdManager messageIdManager;

    @Inject
    @VisibleForTesting
    public MessageHeaderViewFactory(BlobManager blobManager, MessageIdManager messageIdManager) {
        this.blobManager = blobManager;
        this.messageIdManager = messageIdManager;
    }

    @Override
    public Flux<MessageHeaderView> fromMessageIds(List<MessageId> messageIds, MailboxSession mailboxSession) {
        Flux<MessageResult> messages = Flux.from(messageIdManager.getMessagesReactive(messageIds, FetchGroup.HEADERS, mailboxSession));
        return Helpers.toMessageViews(messages, this::fromMessageResults);
    }

    private Mono<MessageHeaderView> fromMessageResults(Collection<MessageResult> messageResults) {
        Helpers.assertOneMessageId(messageResults);


        return Mono.fromCallable(() -> messageResults.iterator().next())
            .flatMap(Throwing.function(firstMessageResult -> {
                Collection<MailboxId> mailboxIds = Helpers.getMailboxIds(messageResults);
                Message mimeMessage = Helpers.parse(firstMessageResult.getFullContent().getInputStream());
                return instanciateHeaderView(messageResults, firstMessageResult, mailboxIds, mimeMessage);
            }));
    }

    private Mono<MessageHeaderView> instanciateHeaderView(Collection<MessageResult> messageResults, MessageResult firstMessageResult,
                                                          Collection<MailboxId> mailboxIds, Message mimeMessage) {
        return Mono.just(MessageHeaderView.messageHeaderBuilder()
            .id(firstMessageResult.getMessageId())
            .mailboxIds(mailboxIds)
            .blobId(BlobId.of(firstMessageResult.getMessageId()))
            .threadId(firstMessageResult.getMessageId().serialize())
            .keywords(Helpers.getKeywords(messageResults))
            .size(firstMessageResult.getSize())
            .inReplyToMessageId(Helpers.getHeaderValue(mimeMessage, "in-reply-to"))
            .subject(Strings.nullToEmpty(mimeMessage.getSubject()).trim())
            .headers(Helpers.toHeaderMap(mimeMessage.getHeader()))
            .from(Emailer.firstFromMailboxList(mimeMessage.getFrom()))
            .to(Emailer.fromAddressList(mimeMessage.getTo()))
            .cc(Emailer.fromAddressList(mimeMessage.getCc()))
            .bcc(Emailer.fromAddressList(mimeMessage.getBcc()))
            .replyTo(Emailer.fromAddressList(mimeMessage.getReplyTo()))
            .date(Helpers.getDateFromHeaderOrInternalDateOtherwise(mimeMessage, firstMessageResult))
            .build());
    }
}
