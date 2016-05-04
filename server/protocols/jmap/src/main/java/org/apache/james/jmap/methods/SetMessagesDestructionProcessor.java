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

package org.apache.james.jmap.methods;

import java.util.Iterator;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.jmap.exceptions.MessageNotFoundException;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;

public class SetMessagesDestructionProcessor implements SetMessagesProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMessagesCreationProcessor.class);
    private static final int LIMIT_BY_ONE = 1;

    private final MailboxMapperFactory mailboxMapperFactory;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;

    @Inject
    @VisibleForTesting
    SetMessagesDestructionProcessor(MailboxMapperFactory mailboxMapperFactory,
                                           MailboxSessionMapperFactory mailboxSessionMapperFactory) {
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
    }

    @Override
    public SetMessagesResponse process(SetMessagesRequest request, MailboxSession mailboxSession) {
        MessageMapper messageMapper;
        try {
            messageMapper = mailboxSessionMapperFactory.createMessageMapper(mailboxSession);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
        return request.getDestroy().stream()
                .map(delete(messageMapper, mailboxSession))
                .reduce(SetMessagesResponse.builder(),  SetMessagesResponse.Builder::accumulator, SetMessagesResponse.Builder::combiner)
                .build();
    }

    private Function<? super MessageId, SetMessagesResponse> delete(MessageMapper messageMapper, MailboxSession mailboxSession) {
        return (messageId) -> {
            try {
                Mailbox mailbox = mailboxMapperFactory
                        .getMailboxMapper(mailboxSession)
                        .findMailboxByPath(messageId.getMailboxPath());

                MailboxMessage mailboxMessage = getMailboxMessage(messageMapper, messageId, mailbox);

                messageMapper.delete(mailbox, mailboxMessage);
                return SetMessagesResponse.builder().destroyed(messageId).build();
            } catch (MessageNotFoundException e) {
                return SetMessagesResponse.builder().notDestroyed(messageId,
                        SetError.builder()
                                .type("notFound")
                                .description("The message " + messageId.serialize() + " can't be found")
                                .build())
                        .build();
            } catch (MailboxException e) {
                LOGGER.error("An error occurred when deleting a message", e);
                return SetMessagesResponse.builder().notDestroyed(messageId,
                        SetError.builder()
                                .type("anErrorOccurred")
                                .description("An error occurred while deleting message " + messageId.serialize())
                                .build())
                        .build();
            }
        };
    }

    private MailboxMessage getMailboxMessage(MessageMapper messageMapper, MessageId messageId, Mailbox mailbox)
            throws MailboxException, MessageNotFoundException {

        Iterator<MailboxMessage> mailboxMessage = messageMapper.findInMailbox(mailbox, MessageRange.one(messageId.getUid()), MessageMapper.FetchType.Metadata, LIMIT_BY_ONE);
        if (!mailboxMessage.hasNext()) {
            throw new MessageNotFoundException();
        }
        return mailboxMessage.next();
    }
}
