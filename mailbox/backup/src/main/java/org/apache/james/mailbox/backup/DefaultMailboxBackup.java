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
package org.apache.james.mailbox.backup;

import static org.apache.james.mailbox.MailboxManager.MailboxSearchFetchType.Minimal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.streams.Iterators;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class DefaultMailboxBackup implements MailboxBackup {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMailboxBackup.class);

    @VisibleForTesting
    static class MailAccountContent {
        private final MailboxWithAnnotations mailboxWithAnnotations;
        private final Stream<MessageResult> messages;

        MailAccountContent(MailboxWithAnnotations mailboxWithAnnotations, Stream<MessageResult> messages) {
            this.mailboxWithAnnotations = mailboxWithAnnotations;
            this.messages = messages;
        }

        public MailboxWithAnnotations getMailboxWithAnnotations() {
            return mailboxWithAnnotations;
        }

        public Stream<MessageResult> getMessages() {
            return messages;
        }
    }

    private final MailboxManager mailboxManager;
    private final ArchiveService archiveService;
    private final MailArchiveRestorer archiveRestorer;

    @Inject
    public DefaultMailboxBackup(MailboxManager mailboxManager, ArchiveService archiveService, MailArchiveRestorer archiveRestorer) {
        this.mailboxManager = mailboxManager;
        this.archiveService = archiveService;
        this.archiveRestorer = archiveRestorer;
    }

    @Override
    public void backupAccount(Username username, OutputStream destination) throws IOException, MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(username);
        List<MailAccountContent> accountContents = getAccountContentForUser(session);
        List<MailboxWithAnnotations> mailboxes = accountContents.stream()
            .map(MailAccountContent::getMailboxWithAnnotations)
            .collect(ImmutableList.toImmutableList());

        Stream<MessageResult> messages = allMessagesForUser(accountContents);
        archive(mailboxes, messages, destination);
        mailboxManager.endProcessingRequest(session);
    }

    private boolean isAccountNonEmpty(Username username) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(username);
        try {
            return getAccountContentForUser(session)
                .stream()
                .findFirst()
                .isPresent();
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

    @Override
    public Publisher<BackupStatus> restore(Username username, InputStream source) {
        try {
            if (isAccountNonEmpty(username)) {
                return Mono.just(BackupStatus.NON_EMPTY_RECEIVER_ACCOUNT);
            }
        } catch (Exception e) {
            LOGGER.error("Error during account restoration for user : " + username.asString(), e);
            return Mono.just(BackupStatus.FAILED);
        }

        return Mono.fromRunnable(Throwing.runnable(() -> archiveRestorer.restore(username, source)).sneakyThrow())
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .doOnError(e -> LOGGER.error("Error during account restoration for user : " + username.asString(), e))
            .doOnTerminate(Throwing.runnable(source::close).sneakyThrow())
            .thenReturn(BackupStatus.DONE)
            .onErrorReturn(BackupStatus.FAILED);
    }

    private Stream<MailAccountContent> getMailboxWithAnnotationsFromPath(MailboxSession session, MailboxPath path) {
        try {
            MessageManager messageManager = mailboxManager.getMailbox(path, session);
            Mailbox mailbox = messageManager.getMailboxEntity();
            List<MailboxAnnotation> annotations = mailboxManager.getAllAnnotations(path, session);
            MailboxWithAnnotations mailboxWithAnnotations = new MailboxWithAnnotations(mailbox, annotations);
            Stream<MessageResult> messages = Iterators.toStream(messageManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session));
            return Stream.of(new MailAccountContent(mailboxWithAnnotations, messages));
        } catch (MailboxException e) {
            LOGGER.error("Error while fetching Mailbox during backup", e);
            return Stream.empty();
        }
    }

    @VisibleForTesting
    List<MailAccountContent> getAccountContentForUser(MailboxSession session) throws MailboxException {
        MailboxQuery queryUser = MailboxQuery.builder()
            .privateNamespace()
            .user(session.getUser())
            .build();
        Stream<MailboxPath> paths = mailboxManager.search(queryUser, Minimal, session)
            .toStream()
            .map(MailboxMetaData::getPath);
        List<MailAccountContent> mailboxes = paths
            .flatMap(path -> getMailboxWithAnnotationsFromPath(session, path))
            .collect(ImmutableList.toImmutableList());

        return mailboxes;
    }

    private void archive(List<MailboxWithAnnotations> mailboxes, Stream<MessageResult> messages, OutputStream destination) throws IOException {
        archiveService.archive(mailboxes, messages, destination);
    }

    private Stream<MessageResult> allMessagesForUser(List<MailAccountContent> mailboxes) {
        return mailboxes.stream().flatMap(MailAccountContent::getMessages);
    }

}
