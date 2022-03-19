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

package org.apache.james.transport.mailets.delivery;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;

import jakarta.mail.Flags;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendResult;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.server.core.MimeMessageUtil;
import org.apache.mailet.StorageDirective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MailboxAppenderImpl implements MailboxAppender {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxAppenderImpl.class);

    private final MailboxManager mailboxManager;

    public MailboxAppenderImpl(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    public Mono<ComposedMessageId> append(MimeMessage mail, Username user, StorageDirective storageDirective) throws MessagingException {
        Preconditions.checkArgument(storageDirective.getTargetFolders().isPresent(), "'targetFolders' field is needed");

        MailboxSession session = createMailboxSession(user);
        String urlPath = storageDirective.getTargetFolders().flatMap(collection -> collection.stream().findFirst()).get();
        String targetFolder = useSlashAsSeparator(urlPath, session);

        return append(mail, user, targetFolder, storageDirective.getFlags(), session)
            .map(AppendResult::getId)
            .flatMap(id -> copyToExtraMailboxes(storageDirective, session, targetFolder, id));
    }

    // Avoids using the MessageIdManager for JPA compatibility
    private Mono<ComposedMessageId> copyToExtraMailboxes(StorageDirective storageDirective, MailboxSession session, String targetFolder, ComposedMessageId id) {
        Collection<String> folders = storageDirective.getTargetFolders().get();

        if (folders.size() > 1) {
            return Flux.fromIterable(folders)
                .skip(1)
                .flatMap(Throwing.function(extraTargetFolder -> {
                    MailboxPath originMailboxPath = MailboxPath.forUser(session.getUser(), targetFolder);
                    MailboxPath destinationMailboxPath = MailboxPath.forUser(session.getUser(), useSlashAsSeparator(extraTargetFolder, session));

                    return copyToExtraMailbox(id, originMailboxPath, destinationMailboxPath, session);
                }))
                .then(Mono.fromRunnable(() -> LOGGER.info("{} copied to {} extra mailboxes", id.getMessageId(), folders)))
                .thenReturn(id);
        }
        return Mono.just(id);
    }

    private Mono<MessageRange> copyToExtraMailbox(ComposedMessageId id, MailboxPath originMailboxPath, MailboxPath destinationMailboxPath, MailboxSession session) {
        return Mono.from(mailboxManager.mailboxExists(destinationMailboxPath, session))
            .flatMap(exists -> {
                if (exists) {
                    return Mono.empty();
                }
                return Mono.from(mailboxManager.createMailboxReactive(destinationMailboxPath, session));
            }).then(Mono.from(mailboxManager.copyMessagesReactive(MessageRange.one(id.getUid()), originMailboxPath, destinationMailboxPath, session)));
    }

    private String useSlashAsSeparator(String urlPath, MailboxSession session) throws MessagingException {
        String destination = urlPath.replace('/', session.getPathDelimiter());
        if (Strings.isNullOrEmpty(destination)) {
            throw new MessagingException("Mail can not be delivered to empty folder");
        }
        if (destination.charAt(0) == session.getPathDelimiter()) {
            destination = destination.substring(1);
        }
        return destination;
    }

    private Mono<AppendResult> append(MimeMessage mail, Username user, String folder, Optional<Flags> flags, MailboxSession mailboxSession) {
        MailboxPath mailboxPath = MailboxPath.forUser(user, folder);
        return Mono.using(
            () -> {
                mailboxManager.startProcessingRequest(mailboxSession);
                return mailboxSession;
            },
            session -> appendMessageToMailbox(mail, session, mailboxPath, flags),
            this::closeProcessing)
            .onErrorMap(OverQuotaException.class, e -> new MessagingException("Could not append due to quota error", e))
            .onErrorMap(MailboxException.class, e -> new MessagingException("Unable to access mailbox.", e));
    }

    protected Mono<AppendResult> appendMessageToMailbox(MimeMessage mail, MailboxSession session, MailboxPath path, Optional<Flags> flags) {
        return createMailboxIfNotExist(session, path)
            .flatMap(mailbox -> Mono.from(mailbox.appendMessageReactive(appendCommand(flags).build(extractContent(mail)), session)));
    }

    private MessageManager.AppendCommand.Builder appendCommand(Optional<Flags> flags) {
        MessageManager.AppendCommand.Builder builder = MessageManager.AppendCommand.builder()
                    .recent()
            .delivery();
        return flags.map(builder::withFlags)
            .orElse(builder);
    }

    private Content extractContent(MimeMessage mail) {
        return new Content() {
            @Override
            public InputStream getInputStream() throws IOException {
                try {
                    return new MimeMessageInputStream(mail);
                } catch (MessagingException e) {
                    throw new IOException(e);
                }
            }

            @Override
            public long size() throws MailboxException {
                try {
                    return MimeMessageUtil.getMessageSize(mail);
                } catch (MessagingException e) {
                    throw new MailboxException("Cannot compute message size", e);
                }
            }
        };
    }

    private Mono<MessageManager> createMailboxIfNotExist(MailboxSession session, MailboxPath path) {
        return Mono.from(mailboxManager.getMailboxReactive(path, session))
            .onErrorResume(MailboxNotFoundException.class, e ->
                Mono.from(mailboxManager.createMailboxReactive(path, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, session))
                    .then(Mono.from(mailboxManager.getMailboxReactive(path, session)))
                    .onErrorResume(MailboxExistsException.class, e2 -> {
                        LOGGER.info("Mailbox {} have been created concurrently", path);
                        return Mono.from(mailboxManager.getMailboxReactive(path, session));
                    }));
    }

    public MailboxSession createMailboxSession(Username user) {
        return mailboxManager.createSystemSession(user);
    }

    private void closeProcessing(MailboxSession session) {
            mailboxManager.endProcessingRequest(session);
        }

}
