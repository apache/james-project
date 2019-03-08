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

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.util.OptionalUtils;
import org.apache.james.util.streams.Iterators;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class DefaultMailboxBackup implements MailboxBackup {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMailboxBackup.class);

    protected class MailAccountContent {
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

    public DefaultMailboxBackup(MailboxManager mailboxManager, ArchiveService archiveService, BlobStore store) {
        this.mailboxManager = mailboxManager;
        this.archiveService = archiveService;
        this.store = store;
    }

    @Override
    public Publisher<BlobId> backupAccount(User user) throws IOException, MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(user.asString());
        List<MailAccountContent> accountContents = getAccountContentForUser(session);
        List<MailboxWithAnnotations> mailboxes = accountContents.stream()
            .map(MailAccountContent::getMailboxWithAnnotations)
            .collect(Guavate.toImmutableList());

        Stream<MessageResult> messages = allMessagesForUser(accountContents);
        return saveToStore(mailboxes, messages);
    }

    private final MailboxManager mailboxManager;
    private final ArchiveService archiveService;
    private final BlobStore store;

    private Function<MailboxPath, Optional<MailAccountContent>> getMailboxWithAnnotationsFromPath(MailboxSession session) {
        return path -> {
            try {
                StoreMessageManager messageManager = (StoreMessageManager) mailboxManager.getMailbox(path, session);
                Mailbox mailbox = messageManager.getMailboxEntity();
                List<MailboxAnnotation> annotations = mailboxManager.getAllAnnotations(path, session);
                MailboxWithAnnotations mailboxWithAnnotations = new MailboxWithAnnotations(mailbox, annotations);
                Stream<MessageResult> messages = Iterators.toStream(messageManager.getMessages(MessageRange.all(), FetchGroupImpl.FULL_CONTENT, session));
                return Optional.of(new MailAccountContent(mailboxWithAnnotations, messages));
            } catch (MailboxException e) {
                LOGGER.error("Error while fetching Mailbox during backup", e);
                return Optional.empty();
            }
        };
    }

    private List<MailAccountContent> getAccountContentForUser(MailboxSession session) throws MailboxException {
        MailboxQuery queryUser = MailboxQuery.builder().username(session.getUser().asString()).build();
        Stream<MailboxPath> paths = mailboxManager.search(queryUser, session).stream()
            .map(MailboxMetaData::getPath);
        List<MailAccountContent> mailboxes = paths
            .flatMap(getMailboxWithAnnotationsFromPath(session).andThen(OptionalUtils::toStream))
            .collect(Guavate.toImmutableList());

        return mailboxes;
    }

    private Publisher<BlobId> saveToStore(List<MailboxWithAnnotations> mailboxes, Stream<MessageResult> messages) throws IOException {
        PipedOutputStream outputStream = new PipedOutputStream();
        PipedInputStream inputStream = new PipedInputStream();
        inputStream.connect(outputStream);

        Mono.fromRunnable(Throwing.runnable(() -> archiveService.archive(mailboxes, messages, outputStream)).sneakyThrow())
            .doOnSuccessOrError(Throwing.biConsumer((result, throwable) -> {
                if (throwable != null) {
                    LOGGER.error("An error happened when archiving  messages", throwable);
                }
                outputStream.flush();
                outputStream.close();
            }))
            .subscribeOn(Schedulers.elastic())
            .then().subscribe();

        return store.save(inputStream)
            .doOnSuccessOrError(Throwing.biConsumer((blobId, e) -> inputStream.close()));
    }

    private Stream<MessageResult> allMessagesForUser(List<MailAccountContent> mailboxes) {
        return mailboxes.stream().flatMap(MailAccountContent::getMessages);
    }

}
