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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.model.UpdateMessagePatch;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetMessagesUpdateProcessor<Id extends MailboxId> {

    private static final int LIMIT_BY_ONE = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(SetMessagesUpdateProcessor.class);

    private final UpdateMessagePatchConverter updatePatchConverter;
    private final MailboxMapperFactory<Id> mailboxMapperFactory;
    private final MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory;

    @Inject
    @VisibleForTesting SetMessagesUpdateProcessor(
            UpdateMessagePatchConverter updatePatchConverter,
            MailboxMapperFactory<Id> mailboxMapperFactory,
            MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory) {
        this.updatePatchConverter = updatePatchConverter;
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
    }

    public SetMessagesResponse processUpdates(SetMessagesRequest request,  MailboxSession mailboxSession) {
        SetMessagesResponse.Builder responseBuilder = SetMessagesResponse.builder();

        List<Map.Entry<MessageId, UpdateMessagePatch>> updatePatchesById = request.buildUpdatePatchs(updatePatchConverter)
                .entrySet().stream()
                .collect(Collectors.toList());
        updatePatchesById.stream()
                .filter(mwp -> ! mwp.getValue().isValid())
                .forEach(mwp -> handleInvalidRequest(responseBuilder, mwp.getKey(), mwp.getValue().getValidationErrors()));
        updatePatchesById.stream()
                .filter(mwp -> mwp.getValue().isValid())
                .forEach(e -> update(e.getKey(), e.getValue(), mailboxSession, responseBuilder));
        return responseBuilder.build();
    }

    private void update(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession,
                        SetMessagesResponse.Builder builder) {
        try {
            MessageMapper<Id> messageMapper = mailboxSessionMapperFactory.createMessageMapper(mailboxSession);
            Mailbox<Id> mailbox = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                    .findMailboxByPath(messageId.getMailboxPath(mailboxSession));
            Iterator<MailboxMessage<Id>> mailboxMessage = messageMapper.findInMailbox(
                    mailbox, MessageRange.one(messageId.getUid()), MessageMapper.FetchType.Metadata, LIMIT_BY_ONE);
            MailboxMessage<Id> messageWithUpdatedFlags = applyMessagePatch(messageId, mailboxMessage.next(), updateMessagePatch, builder);
            savePatchedMessage(mailbox, messageId, messageWithUpdatedFlags, messageMapper);
        } catch (NoSuchElementException e) {
            addMessageIdNotFoundToResponse(messageId, builder);
        } catch (MailboxException e) {
            handleMessageUpdateException(messageId, builder, e);
        }
    }

    private boolean savePatchedMessage(Mailbox<Id> mailbox, MessageId messageId,
                                       MailboxMessage<Id> message,
                                       MessageMapper<Id> messageMapper) throws MailboxException {
        return messageMapper.updateFlags(mailbox, new FlagsUpdateCalculator(message.createFlags(),
                        MessageManager.FlagsUpdateMode.REPLACE),
                MessageRange.one(messageId.getUid()))
                .hasNext()
                ;
    }

    private void addMessageIdNotFoundToResponse(MessageId messageId, SetMessagesResponse.Builder builder) {
        builder.notUpdated(ImmutableMap.of(messageId,
                SetError.builder()
                        .type("notFound")
                        .properties(ImmutableSet.of(MessageProperties.MessageProperty.id))
                        .description("message not found")
                        .build()));
    }

    private MailboxMessage<Id> applyMessagePatch(MessageId messageId, MailboxMessage<Id> message,
                                                 UpdateMessagePatch updatePatch, SetMessagesResponse.Builder builder) {
        Flags newStateFlags = updatePatch.applyToState(message.isSeen(), message.isAnswered(), message.isFlagged());
        message.setFlags(newStateFlags);
        builder.updated(ImmutableList.of(messageId));
        return message;
    }

    private void handleMessageUpdateException(MessageId messageId,
                                              SetMessagesResponse.Builder builder,
                                              MailboxException e) {
        LOGGER.error("An error occurred when updating a message", e);
        builder.notUpdated(ImmutableMap.of(messageId, SetError.builder()
                .type("anErrorOccurred")
                .description("An error occurred when updating a message")
                .build()));
    }

    private void handleInvalidRequest(SetMessagesResponse.Builder responseBuilder, MessageId messageId,
                                      List<ValidationResult> validationErrors) {
        LOGGER.error("Invalid update request for message #", messageId.toString());

        String formattedValidationErrorMessage = validationErrors.stream()
                .map(err -> err.getProperty() + ": " + err.getErrorMessage())
                .collect(Collectors.joining(", "));

        Set<MessageProperties.MessageProperty> properties = validationErrors.stream()
                .flatMap(err -> MessageProperties.MessageProperty.find(err.getProperty()))
                .collect(Collectors.toSet());

        responseBuilder.notUpdated(ImmutableMap.of(messageId, SetError.builder()
                .type("invalidProperties")
                .properties(properties)
                .description(formattedValidationErrorMessage)
                .build()));

    }
}
