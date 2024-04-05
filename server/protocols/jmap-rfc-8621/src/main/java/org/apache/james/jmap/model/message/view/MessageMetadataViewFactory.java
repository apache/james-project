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
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MessageMetadataViewFactory implements MessageViewFactory<MessageMetadataView> {

    private final BlobManager blobManager;
    private final MessageIdManager messageIdManager;

    @Inject
    @VisibleForTesting
    public MessageMetadataViewFactory(BlobManager blobManager, MessageIdManager messageIdManager) {
        this.blobManager = blobManager;
        this.messageIdManager = messageIdManager;
    }

    @Override
    public Flux<MessageMetadataView> fromMessageIds(List<MessageId> messageIds, MailboxSession session) {
        Flux<MessageResult> messages = Flux.from(messageIdManager.getMessagesReactive(messageIds, FetchGroup.MINIMAL, session));
        return Helpers.toMessageViews(messages, this::fromMessageResults);
    }

    @VisibleForTesting
    public Mono<MessageMetadataView> fromMessageResults(Collection<MessageResult> messageResults) {
        Helpers.assertOneMessageId(messageResults);

        MessageResult firstMessageResult = messageResults.iterator().next();
        Collection<MailboxId> mailboxIds = Helpers.getMailboxIds(messageResults);

        return Mono.just(MessageMetadataView.messageMetadataBuilder()
            .id(firstMessageResult.getMessageId())
            .mailboxIds(mailboxIds)
            .blobId(BlobId.of(firstMessageResult.getMessageId()))
            .threadId(firstMessageResult.getMessageId().serialize())
            .keywords(Helpers.getKeywords(messageResults))
            .size(firstMessageResult.getSize())
            .build());
    }

}
