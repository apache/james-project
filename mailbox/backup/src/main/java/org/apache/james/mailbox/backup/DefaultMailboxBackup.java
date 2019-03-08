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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.BlobId;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxAnnotation;
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

public class DefaultMailboxBackup implements MailboxBackup {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMailboxBackup.class);

    protected class MailAccountContent {
        final MailboxWithAnnotations mailboxWithAnnotations;
        final Stream<MessageResult> messages;

        MailAccountContent(MailboxWithAnnotations mailboxWithAnnotations, Stream<MessageResult> messages) {
            this.mailboxWithAnnotations = mailboxWithAnnotations;
            this.messages = messages;
        }
    }

    public DefaultMailboxBackup(MailboxManager mailboxManager, ArchiveService archiveService, BlobStore store) {
        this.mailboxManager = mailboxManager;
        this.archiveService = archiveService;
        this.store = store;
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

    private List<MailAccountContent> getAllMailboxesForUser(MailboxSession session) throws MailboxException {
        MailboxQuery queryUser = MailboxQuery.builder().username(session.getUser().asString()).build();
        Stream<MailboxPath> paths = mailboxManager.search(queryUser, session).stream().map(mailboxMetaData -> mailboxMetaData.getPath());
        List<MailAccountContent> mailboxes = paths
            .flatMap(getMailboxWithAnnotationsFromPath(session).andThen(OptionalUtils::toStream))
            .collect(Collectors.toList());

        return mailboxes;
    }

    private Publisher<BlobId> saveToStore(List<MailboxWithAnnotations> mailboxes, Stream<MessageResult> messages) throws IOException {
        File tmp = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            archiveService.archive(mailboxes, messages, out);
            try (InputStream in = new BufferedInputStream(new FileInputStream(tmp))) {
                return store.save(in).map(b -> BlobId.fromString(b.asString()));
            }
        } finally {
            tmp.delete();
        }
    }

    private Stream<MessageResult> allMessagesForUser(List<MailAccountContent> mailboxes) {
        return mailboxes.stream().flatMap(messages -> messages.messages);
    }

    @Override
    public Publisher<BlobId> backupAccount(User user) throws IOException, MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(user.asString());
        List<MailAccountContent> mailboxesWithMessages = getAllMailboxesForUser(session);
        List<MailboxWithAnnotations> mailboxes = mailboxesWithMessages.stream().map(m -> m.mailboxWithAnnotations).collect(Collectors.toList());
        Stream<MessageResult> messages = allMessagesForUser(mailboxesWithMessages);
        return saveToStore(mailboxes, messages);
    }
}
