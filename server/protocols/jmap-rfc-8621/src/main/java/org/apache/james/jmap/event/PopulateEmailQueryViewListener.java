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

package org.apache.james.jmap.event;

import static jakarta.mail.Flags.Flag.DELETED;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener.ReactiveGroupEventListener;
import org.apache.james.events.Group;
import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.field.DateTimeField;
import org.apache.james.mime4j.field.DateTimeFieldLenientImpl;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.FunctionalUtils;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PopulateEmailQueryViewListener implements ReactiveGroupEventListener {
    public static class PopulateEmailQueryViewListenerGroup extends Group {

    }

    static final Group GROUP = new PopulateEmailQueryViewListenerGroup();
    private static final int CONCURRENCY = 5;

    private final MessageIdManager messageIdManager;
    private final EmailQueryView view;
    private final SessionProvider sessionProvider;

    @Inject
    public PopulateEmailQueryViewListener(MessageIdManager messageIdManager, EmailQueryView view, SessionProvider sessionProvider) {
        this.messageIdManager = messageIdManager;
        this.view = view;
        this.sessionProvider = sessionProvider;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof Added
            || event instanceof FlagsUpdated
            || event instanceof Expunged
            || event instanceof MailboxDeletion;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof Added) {
            return handleAdded((Added) event);
        }
        if (event instanceof Expunged) {
            return handleExpunged((Expunged) event);
        }
        if (event instanceof FlagsUpdated) {
            return handleFlagsUpdated((FlagsUpdated) event);
        }
        if (event instanceof MailboxDeletion) {
            return handleMailboxDeletion((MailboxDeletion) event);
        }
        return Mono.empty();
    }

    private Publisher<Void> handleMailboxDeletion(MailboxDeletion mailboxDeletion) {
        return view.delete(mailboxDeletion.getMailboxId());
    }

    private Publisher<Void> handleExpunged(Expunged expunged) {
        return Flux.fromStream(expunged.getUids().stream()
            .map(uid -> expunged.getMetaData(uid).getMessageId()))
            .concatMap(messageId -> view.delete(expunged.getMailboxId(), messageId))
            .then();
    }


    private Publisher<Void> handleFlagsUpdated(FlagsUpdated flagsUpdated) {
        MailboxSession session = sessionProvider.createSystemSession(flagsUpdated.getUsername());

        Mono<Void> removeMessagesMarkedAsDeleted = Flux.fromIterable(flagsUpdated.getUpdatedFlags())
            .filter(updatedFlags -> updatedFlags.isModifiedToSet(DELETED))
            .map(UpdatedFlags::getMessageId)
            .handle(publishIfPresent())
            .concatMap(messageId -> view.delete(flagsUpdated.getMailboxId(), messageId))
            .then();

        Mono<Void> addMessagesNoLongerMarkedAsDeleted = Flux.fromIterable(flagsUpdated.getUpdatedFlags())
            .filter(updatedFlags -> updatedFlags.isModifiedToUnset(DELETED))
            .map(UpdatedFlags::getMessageId)
            .handle(publishIfPresent())
            .concatMap(messageId ->
                Flux.from(messageIdManager.getMessagesReactive(ImmutableList.of(messageId), FetchGroup.HEADERS, session))
                    .next())
            .concatMap(message -> handleAdded(flagsUpdated.getMailboxId(), message))
            .then();

        return removeMessagesMarkedAsDeleted
            .then(addMessagesNoLongerMarkedAsDeleted);
    }

    private Mono<Void> handleAdded(Added added) {
        MailboxSession session = sessionProvider.createSystemSession(added.getUsername());
        return Flux.fromStream(added.getUids().stream()
            .map(added::getMetaData))
            .flatMap(messageMetaData -> handleAdded(added, messageMetaData, session), CONCURRENCY)
            .then();
    }

    private Mono<Void> handleAdded(Added added, MessageMetaData messageMetaData, MailboxSession session) {
        MessageId messageId = messageMetaData.getMessageId();
        MailboxId mailboxId = added.getMailboxId();

        Mono<Void> doHandleAdded = Flux.from(messageIdManager.getMessagesReactive(ImmutableList.of(messageId), FetchGroup.HEADERS, session))
            .next()
            .filter(message -> !message.getFlags().contains(DELETED))
            .flatMap(messageResult -> handleAdded(added.getMailboxId(), messageResult));
        if (Role.from(added.getMailboxPath().getName()).equals(Optional.of(Role.OUTBOX))) {
            return checkMessageStillInOriginMailbox(messageId, session, mailboxId)
                .filter(FunctionalUtils.identityPredicate())
                .flatMap(stillInOriginMailbox -> doHandleAdded);
        }
        return doHandleAdded;
    }

    private Mono<Boolean> checkMessageStillInOriginMailbox(MessageId messageId, MailboxSession session, MailboxId targetMailboxId) {
        return Flux.from(messageIdManager.messageMetadata(messageId, session))
            .filter(composedMessageIdWithMetaData -> composedMessageIdWithMetaData.getComposedMessageId().getMailboxId().equals(targetMailboxId))
            .hasElements();
    }

    public Mono<Void> handleAdded(MailboxId mailboxId, MessageResult messageResult) {
        ZonedDateTime receivedAt = ZonedDateTime.ofInstant(messageResult.getInternalDate().toInstant(), ZoneOffset.UTC);

        return Mono.fromCallable(() -> parseMessage(messageResult))
            .map(header -> date(header).orElse(messageResult.getInternalDate()))
            .map(date -> ZonedDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC))
            .flatMap(sentAt -> view.save(mailboxId, sentAt, receivedAt, messageResult.getMessageId()))
            .then();
    }

    private Header parseMessage(MessageResult messageResult) throws IOException, MailboxException {
        DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
        defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        return defaultMessageBuilder.parseHeader(messageResult.getFullContent().getInputStream());
    }

    private Optional<Date> date(Header header) {
        return Optional.ofNullable(header.getField("Date"))
            .map(field -> DateTimeFieldLenientImpl.PARSER.parse(field, DecodeMonitor.SILENT))
            .map(DateTimeField::getDate);
    }
}
