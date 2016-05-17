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

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.jmap.exceptions.MailboxRoleNotFoundException;
import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessageId;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.MessageProperties.MessageProperty;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.send.MailFactory;
import org.apache.james.jmap.send.MailMetadata;
import org.apache.james.jmap.send.MailSpool;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class SetMessagesCreationProcessor implements SetMessagesProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMessagesCreationProcessor.class);

    private final MailboxMapperFactory mailboxMapperFactory;
    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final MIMEMessageConverter mimeMessageConverter;
    private final MailSpool mailSpool;
    private final MailFactory mailFactory;

    @Inject
    @VisibleForTesting
    SetMessagesCreationProcessor(MailboxMapperFactory mailboxMapperFactory,
                                 MailboxManager mailboxManager,
                                 MailboxSessionMapperFactory mailboxSessionMapperFactory,
                                 MIMEMessageConverter mimeMessageConverter,
                                 MailSpool mailSpool,
                                 MailFactory mailFactory) {
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.mailboxManager = mailboxManager;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.mimeMessageConverter = mimeMessageConverter;
        this.mailSpool = mailSpool;
        this.mailFactory = mailFactory;
    }

    @Override
    public SetMessagesResponse process(SetMessagesRequest request, MailboxSession mailboxSession) {
        Mailbox outbox;
        try {
            outbox = getOutbox(mailboxSession).orElseThrow(() -> new MailboxRoleNotFoundException(Role.OUTBOX));
        } catch (MailboxException | MailboxRoleNotFoundException e) {
            LOGGER.error("Unable to find a mailbox with role 'outbox'!");
            throw Throwables.propagate(e);
        }

        List<String> allowedSenders = ImmutableList.of(mailboxSession.getUser().getUserName());

        // handle errors
        Predicate<CreationMessage> validMessagesTester = creationMessage -> creationMessage.isValid() && isAllowedFromAddress(creationMessage, allowedSenders);
        Predicate<CreationMessage> invalidMessagesTester = validMessagesTester.negate();
        Function<CreationMessage, List<ValidationResult>> toValidationResults = creationMessage -> ImmutableList.<ValidationResult>builder()
            .addAll(creationMessage.validate())
            .addAll(validationResultForIncorrectAddress(creationMessage, allowedSenders))
            .build();

        SetMessagesResponse.Builder responseBuilder = SetMessagesResponse.builder()
                .notCreated(handleCreationErrors(invalidMessagesTester, toValidationResults, request));

        return request.getCreate().entrySet().stream()
                .filter(e -> validMessagesTester.test(e.getValue()))
                .map(e -> new MessageWithId.CreationMessageEntry(e.getKey(), e.getValue()))
                .map(nuMsg -> createMessageInOutboxAndSend(nuMsg, mailboxSession, outbox, buildMessageIdFunc(mailboxSession, outbox)))
                .map(msg -> SetMessagesResponse.builder().created(ImmutableMap.of(msg.getCreationId(), msg.getMessage())).build())
                .reduce(responseBuilder, SetMessagesResponse.Builder::accumulator, SetMessagesResponse.Builder::combiner)
                .build();
    }

    private boolean isAllowedFromAddress(CreationMessage creationMessage, List<String> allowedFromMailAddresses) {
        return creationMessage.getFrom()
            .map(draftEmailer -> draftEmailer.getEmail()
                .map(allowedFromMailAddresses::contains)
                .orElse(false))
            .orElse(false);
    }

    private List<ValidationResult> validationResultForIncorrectAddress(CreationMessage creationMessage, List<String> allowedSenders) {
        return creationMessage.getFrom()
            .map(draftEmailer -> draftEmailer
                .getEmail()
                .map(mail -> validationResultForIncorrectAddress(allowedSenders, mail))
                .orElse(Lists.newArrayList()))
            .orElse(Lists.newArrayList());
    }

    private List<ValidationResult> validationResultForIncorrectAddress(List<String> allowedSenders, String mail) {
        if (!allowedSenders.contains(mail)) {
            return Lists.newArrayList(ValidationResult.builder()
                .message("Invalid 'from' field. Must be one of " + allowedSenders)
                .property(MessageProperty.from.asFieldName())
                .build());
        }
        return Lists.newArrayList();
    }

    private Map<CreationMessageId, SetError> handleCreationErrors(Predicate<CreationMessage> invalidMessagesTester,
                                                                  Function<CreationMessage, List<ValidationResult>> toValidationResults,
                                                                  SetMessagesRequest request) {
        return request.getCreate().entrySet().stream()
                .filter(e -> invalidMessagesTester.test(e.getValue()))
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), buildSetErrorFromValidationResult(toValidationResults.apply(e.getValue()))))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    private SetError buildSetErrorFromValidationResult(List<ValidationResult> validationErrors) {
        String formattedValidationErrorMessage = validationErrors.stream()
                .map(err -> err.getProperty() + ": " + err.getErrorMessage())
                .collect(Collectors.joining("\\n"));
        Splitter propertiesSplitter = Splitter.on(',').trimResults().omitEmptyStrings();
        Set<MessageProperties.MessageProperty> properties = validationErrors.stream()
                .flatMap(err -> propertiesSplitter.splitToList(err.getProperty()).stream())
                .flatMap(MessageProperty::find)
                .collect(Collectors.toSet());
        return SetError.builder()
                .type("invalidProperties")
                .properties(properties)
                .description(formattedValidationErrorMessage)
                .build();
    }

    @VisibleForTesting
    protected MessageWithId<Message> createMessageInOutboxAndSend(MessageWithId.CreationMessageEntry createdEntry,
                                                           MailboxSession session,
                                                           Mailbox outbox, Function<Long, MessageId> buildMessageIdFromUid) {
        try {
            MessageMapper messageMapper = mailboxSessionMapperFactory.createMessageMapper(session);
            MailboxMessage newMailboxMessage = buildMailboxMessage(createdEntry, outbox);
            messageMapper.add(outbox, newMailboxMessage);
            Message jmapMessage = Message.fromMailboxMessage(newMailboxMessage, buildMessageIdFromUid);
            sendMessage(newMailboxMessage, jmapMessage, session);
            return new MessageWithId<>(createdEntry.getCreationId(), jmapMessage);
        } catch (MailboxException | MessagingException | IOException e) {
            throw Throwables.propagate(e);
        } catch (MailboxRoleNotFoundException e) {
            LOGGER.error("Could not find mailbox '%s' while trying to save message.", e.getRole().serialize());
            throw Throwables.propagate(e);
        }
    }

    private Function<Long, MessageId> buildMessageIdFunc(MailboxSession session, Mailbox outbox) {
        MailboxPath outboxPath = new MailboxPath(session.getPersonalSpace(), session.getUser().getUserName(), outbox.getName());
        return uid -> new MessageId(session.getUser(), outboxPath, uid);
    }

    private MailboxMessage buildMailboxMessage(MessageWithId.CreationMessageEntry createdEntry, Mailbox outbox) {
        byte[] messageContent = mimeMessageConverter.convert(createdEntry);
        SharedInputStream content = new SharedByteArrayInputStream(messageContent);
        long size = messageContent.length;
        int bodyStartOctet = 0;

        Flags flags = getMessageFlags(createdEntry.getMessage());
        PropertyBuilder propertyBuilder = buildPropertyBuilder();
        MailboxId mailboxId = outbox.getMailboxId();
        Date internalDate = Date.from(createdEntry.getMessage().getDate().toInstant());

        return new SimpleMailboxMessage(internalDate, size,
                bodyStartOctet, content, flags, propertyBuilder, mailboxId);
    }

    @VisibleForTesting
    protected Optional<Mailbox> getOutbox(MailboxSession session) throws MailboxException {
        return mailboxManager.search(MailboxQuery.builder(session)
                .privateUserMailboxes().build(), session).stream()
            .map(MailboxMetaData::getPath)
            .filter(this::hasRoleOutbox)
            .map(loadMailbox(session))
            .findFirst();
    }

    private boolean hasRoleOutbox(MailboxPath mailBoxPath) {
        return Role.from(mailBoxPath.getName())
                .map(Role.OUTBOX::equals)
                .orElse(false);
    }

    private ThrowingFunction<MailboxPath, Mailbox> loadMailbox(MailboxSession session) {
        return path -> mailboxMapperFactory.getMailboxMapper(session).findMailboxByPath(path);
    }

    private PropertyBuilder buildPropertyBuilder() {
        return new PropertyBuilder();
    }

    private Flags getMessageFlags(CreationMessage message) {
        Flags result = new Flags();
        if (!message.isIsUnread()) {
            result.add(Flags.Flag.SEEN);
        }
        if (message.isIsFlagged()) {
            result.add(Flags.Flag.FLAGGED);
        }
        if (message.isIsAnswered() || message.getInReplyToMessageId().isPresent()) {
            result.add(Flags.Flag.ANSWERED);
        }
        if (message.isIsDraft()) {
            result.add(Flags.Flag.DRAFT);
        }
        return result;
    }

    private void sendMessage(MailboxMessage mailboxMessage, Message jmapMessage, MailboxSession session) throws MessagingException, IOException {
        Mail mail = mailFactory.build(mailboxMessage, jmapMessage);
        MailMetadata metadata = new MailMetadata(jmapMessage.getId(), session.getUser().getUserName());
        mailSpool.send(mail, metadata);
    }
}
