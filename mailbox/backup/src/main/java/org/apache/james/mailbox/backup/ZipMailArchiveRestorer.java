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
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendResult;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteSourceContent;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

public class ZipMailArchiveRestorer implements MailArchiveRestorer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipMailArchiveRestorer.class);

    private final MailboxManager mailboxManager;
    private final MailArchivesLoader archiveLoader;

    @Inject
    public ZipMailArchiveRestorer(MailboxManager mailboxManager, MailArchivesLoader archiveLoader) {
        this.mailboxManager = mailboxManager;
        this.archiveLoader = archiveLoader;
    }

    public void restore(Username username, InputStream source) throws MailboxException, IOException {
        MailboxSession session = mailboxManager.createSystemSession(username);
        restoreEntries(source, session);
        mailboxManager.endProcessingRequest(session);
    }

    private void restoreEntries(InputStream source, MailboxSession session) throws IOException, MailboxException {
        try (MailArchiveIterator archiveIterator = archiveLoader.load(source)) {
            Map<SerializedMailboxId, MessageManager> restoredMailboxes = new HashMap<>();
            while (archiveIterator.hasNext()) {
                MailArchiveEntry entry = archiveIterator.next();
                switch (entry.getType()) {
                    case MAILBOX:
                        MailboxWithAnnotationsArchiveEntry mailboxEntry = (MailboxWithAnnotationsArchiveEntry) entry;
                        restoreMailboxEntry(session, mailboxEntry)
                            .ifPresent(pair -> restoredMailboxes.put(pair.getKey(), pair.getValue()));
                        break;
                    case MESSAGE:
                        MessageArchiveEntry messageEntry = (MessageArchiveEntry) entry;
                        restoreMessage(session, messageEntry, restoredMailboxes);
                        break;
                    case UNKNOWN:
                        String entryName = ((UnknownArchiveEntry) entry).entryName();
                        LOGGER.warn("unknown entry found in zip :" + entryName);
                        break;
                }
            }
        }
    }

    private void restoreMessage(MailboxSession session, MessageArchiveEntry messageEntry, Map<SerializedMailboxId, MessageManager> mailboxes) {
        try {
            MessageManager messageManager = mailboxes.get(messageEntry.mailboxId());
            if (messageManager == null) {
                LOGGER.warn("Mailbox {} not found for message {}", messageEntry.mailboxId(), messageEntry.messageId());
                return;
            }
            ByteSourceContent content = ByteSourceContent.of(messageEntry.content());
            MessageManager.AppendCommand command = MessageManager.AppendCommand.builder()
                .withInternalDate(messageEntry.internalDate())
                .withFlags(messageEntry.flags())
                .build(content);
            AppendResult result = messageManager.appendMessage(command, session);
            if (!result.getSize().equals(messageEntry.size())) {
                LOGGER.warn("Size {} for message {} different from zip entry one {}", result.getSize(), messageEntry.messageId(), messageEntry.size());
            }
        } catch (Exception e) {
            LOGGER.error("Error restoring message {} to mailbox {}", messageEntry.messageId(), messageEntry.mailboxId(), e);
        }
    }

    private Optional<ImmutablePair<SerializedMailboxId, MessageManager>> restoreMailboxEntry(MailboxSession session,
                                                                                             MailboxWithAnnotationsArchiveEntry mailboxWithAnnotationsArchiveEntry) throws MailboxException {
        MailboxPath mailboxPath = MailboxPath.forUser(session.getUser(), mailboxWithAnnotationsArchiveEntry.getMailboxName());
        Optional<MailboxId> newMailboxId = mailboxManager.createMailbox(mailboxPath, session);
        mailboxManager.updateAnnotations(mailboxPath, session, mailboxWithAnnotationsArchiveEntry.getAnnotations());
        return newMailboxId.map(Throwing.<MailboxId, ImmutablePair<SerializedMailboxId, MessageManager>>function(newId ->
            ImmutablePair.of(mailboxWithAnnotationsArchiveEntry.getMailboxId(), mailboxManager.getMailbox(newId, session))).sneakyThrow());
    }
}
