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

package org.apache.james.webadmin.vault.routes;

import static org.apache.james.mailbox.MessageManager.AppendCommand;
import static org.apache.james.webadmin.vault.routes.RestoreService.RestoreResult.RESTORE_FAILED;
import static org.apache.james.webadmin.vault.routes.RestoreService.RestoreResult.RESTORE_SUCCEED;

import javax.inject.Inject;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RestoreService {

    enum RestoreResult {
        RESTORE_SUCCEED,
        RESTORE_FAILED
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RestoreService.class);

    static final String RESTORE_MAILBOX_NAME = "Restored-Messages";

    private final DeletedMessageVault deletedMessageVault;
    private final MailboxManager mailboxManager;

    @Inject
    RestoreService(DeletedMessageVault deletedMessageVault, MailboxManager mailboxManager) {
        this.deletedMessageVault = deletedMessageVault;
        this.mailboxManager = mailboxManager;
    }

    Flux<RestoreResult> restore(User userToRestore, Query searchQuery) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(userToRestore.asString());
        MessageManager restoreMessageManager = restoreMailboxManager(session);

        return Flux.from(deletedMessageVault.search(userToRestore, searchQuery))
            .flatMap(deletedMessage -> appendToMailbox(restoreMessageManager, deletedMessage, session));
    }

    private Mono<RestoreResult> appendToMailbox(MessageManager restoreMailboxManager, DeletedMessage deletedMessage, MailboxSession session) {
        return appendCommand(deletedMessage)
            .map(Throwing.<AppendCommand, ComposedMessageId>function(
                appendCommand -> restoreMailboxManager.appendMessage(appendCommand, session)).sneakyThrow())
            .map(any -> RESTORE_SUCCEED)
            .onErrorResume(throwable -> {
                LOGGER.error("append message {} to restore mailbox of user {} didn't success",
                    deletedMessage.getMessageId().serialize(), deletedMessage.getOwner().asString(), throwable);
                return Mono.just(RESTORE_FAILED);
            });
    }

    private Mono<AppendCommand> appendCommand(DeletedMessage deletedMessage) {
        return Mono.from(deletedMessageVault.loadMimeMessage(deletedMessage.getOwner(), deletedMessage.getMessageId()))
            .map(messageContentStream -> AppendCommand.builder()
                .build(messageContentStream));
    }

    private MessageManager restoreMailboxManager(MailboxSession session) throws MailboxException {
        MailboxPath restoreMailbox = MailboxPath.forUser(session.getUser().asString(), RESTORE_MAILBOX_NAME);
        try {
            return mailboxManager.getMailbox(restoreMailbox, session);
        } catch (MailboxNotFoundException e) {
            LOGGER.debug("mailbox {} doesn't exist, create a new one", restoreMailbox);
            return createRestoreMailbox(session, restoreMailbox);
        }
    }

    private MessageManager createRestoreMailbox(MailboxSession session, MailboxPath restoreMailbox) throws MailboxException {
        return mailboxManager.createMailbox(restoreMailbox, session)
            .map(Throwing.<MailboxId, MessageManager>function(mailboxId -> mailboxManager.getMailbox(mailboxId, session)).sneakyThrow())
            .orElseThrow(() -> new RuntimeException("createMailbox " + restoreMailbox.asString() + " returns an empty mailboxId"));
    }

}
