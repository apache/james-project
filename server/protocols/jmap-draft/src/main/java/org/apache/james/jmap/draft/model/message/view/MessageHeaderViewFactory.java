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
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.mailbox.BlobManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.MimeConfig;

import com.google.common.base.Strings;

public class MessageHeaderViewFactory implements MessageViewFactory<MessageHeaderView> {
    private final BlobManager blobManager;

    @Inject
    MessageHeaderViewFactory(BlobManager blobManager) {
        this.blobManager = blobManager;
    }

    @Override
    public MessageHeaderView fromMessageResults(Collection<MessageResult> messageResults) throws MailboxException {
        assertOneMessageId(messageResults);

        MessageResult firstMessageResult = messageResults.iterator().next();
        List<MailboxId> mailboxIds = getMailboxIds(messageResults);

        Message mimeMessage = parse(firstMessageResult);

        return MessageHeaderView.messageHeaderBuilder()
            .id(firstMessageResult.getMessageId())
            .mailboxIds(mailboxIds)
            .blobId(BlobId.of(blobManager.toBlobId(firstMessageResult.getMessageId())))
            .threadId(firstMessageResult.getMessageId().serialize())
            .keywords(getKeywords(messageResults))
            .size(firstMessageResult.getSize())
            .inReplyToMessageId(getHeader(mimeMessage, "in-reply-to"))
            .subject(Strings.nullToEmpty(mimeMessage.getSubject()).trim())
            .headers(toMap(mimeMessage.getHeader().getFields()))
            .from(firstFromMailboxList(mimeMessage.getFrom()))
            .to(fromAddressList(mimeMessage.getTo()))
            .cc(fromAddressList(mimeMessage.getCc()))
            .bcc(fromAddressList(mimeMessage.getBcc()))
            .replyTo(fromAddressList(mimeMessage.getReplyTo()))
            .date(getDateFromHeaderOrInternalDateOtherwise(mimeMessage, firstMessageResult))
            .build();
    }

    private Message parse(MessageResult message) throws MailboxException {
        try {
            return Message.Builder
                .of()
                .use(MimeConfig.PERMISSIVE)
                .parse(message.getFullContent().getInputStream())
                .build();
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message: " + e.getMessage(), e);
        }
    }

    private Instant getDateFromHeaderOrInternalDateOtherwise(Message mimeMessage, MessageResult message) {
        return Optional.ofNullable(mimeMessage.getDate())
            .map(Date::toInstant)
            .orElse(message.getInternalDate().toInstant());
    }
}
