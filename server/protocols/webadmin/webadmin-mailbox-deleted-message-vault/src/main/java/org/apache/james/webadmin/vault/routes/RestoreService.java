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
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;
import static org.apache.james.webadmin.vault.routes.RestoreService.RestoreResult.RESTORE_FAILED;
import static org.apache.james.webadmin.vault.routes.RestoreService.RestoreResult.RESTORE_SUCCEED;

import java.io.InputStream;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.ByteSourceContent;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageContentNotFoundException;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.VaultConfiguration;
import org.apache.james.vault.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RestoreService {

    enum RestoreResult {
        RESTORE_SUCCEED,
        RESTORE_FAILED
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RestoreService.class);
    private static final Predicate<Throwable> CONTENT_NOT_FOUND_PREDICATE =
        DeletedMessageContentNotFoundException.class::isInstance;

    private final DeletedMessageVault deletedMessageVault;
    private final MailboxManager mailboxManager;
    private final VaultConfiguration vaultConfiguration;

    @Inject
    RestoreService(DeletedMessageVault deletedMessageVault, MailboxManager mailboxManager,
                   VaultConfiguration vaultConfiguration) {
        this.deletedMessageVault = deletedMessageVault;
        this.mailboxManager = mailboxManager;
        this.vaultConfiguration = vaultConfiguration;
    }

    public Flux<RestoreResult> restore(Username usernameToRestore, Query searchQuery) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(usernameToRestore);
        MessageManager restoreMessageManager = restoreMailboxManager(session);

        return Flux.from(deletedMessageVault.search(usernameToRestore, searchQuery))
            .flatMap(deletedMessage -> appendToMailbox(restoreMessageManager, deletedMessage, session), DEFAULT_CONCURRENCY)
            .doFinally(any -> mailboxManager.endProcessingRequest(session));
    }

    private Mono<RestoreResult> appendToMailbox(MessageManager restoreMailboxManager, DeletedMessage deletedMessage, MailboxSession session) {
        return Mono.usingWhen(
            messageContent(deletedMessage),
            inputStream -> Mono.usingWhen(
                Mono.fromCallable(() -> ByteSourceContent.of(inputStream)),
                content -> Mono.from(restoreMailboxManager.appendMessageReactive(AppendCommand.builder().build(content), session))
                    .map(any -> RESTORE_SUCCEED),
                content -> Mono.fromRunnable(Throwing.runnable(content::close))),
            stream -> Mono.fromRunnable(Throwing.runnable(stream::close)))
            .onErrorResume(throwable -> {
                LOGGER.error("append message {} to restore mailbox of user {} didn't success",
                    deletedMessage.getMessageId().serialize(), deletedMessage.getOwner().asString(), throwable);
                return Mono.just(RESTORE_FAILED);
            });
    }

    private Mono<InputStream> messageContent(DeletedMessage deletedMessage) {
        return Mono.from(deletedMessageVault.loadMimeMessage(deletedMessage.getOwner(), deletedMessage.getMessageId()))
            .onErrorResume(CONTENT_NOT_FOUND_PREDICATE, throwable -> {
                LOGGER.info(
                    "Error happened when loading mime message associated with id {} of user {} in the vault",
                    deletedMessage.getMessageId().serialize(),
                    deletedMessage.getOwner().asString(),
                    throwable);
                return Mono.empty();
            });
    }

    private MessageManager restoreMailboxManager(MailboxSession session) throws MailboxException {
        MailboxPath restoreMailbox = MailboxPath.forUser(session.getUser(), vaultConfiguration.getRestoreLocation());
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
