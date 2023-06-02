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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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

    private void restoreEntries(InputStream source, MailboxSession session) throws IOException {
        try (MailArchiveIterator archiveIterator = archiveLoader.load(source)) {
            List<MailboxWithAnnotationsArchiveEntry> mailboxes = readMailboxes(archiveIterator);
            restoreMailboxes(session, mailboxes);
        }
    }

    private Map<SerializedMailboxId, MessageManager> restoreMailboxes(MailboxSession session, List<MailboxWithAnnotationsArchiveEntry> mailboxes) {
        return mailboxes.stream()
            .flatMap(Throwing.<MailboxWithAnnotationsArchiveEntry, Stream<ImmutablePair<SerializedMailboxId, MessageManager>>>function(
                mailboxEntry -> restoreMailboxEntry(session, mailboxEntry).stream()).sneakyThrow())
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<MailboxWithAnnotationsArchiveEntry> readMailboxes(MailArchiveIterator iterator) {
        ImmutableList.Builder<MailboxWithAnnotationsArchiveEntry> mailboxes = ImmutableList.builder();
        while (iterator.hasNext()) {
            MailArchiveEntry entry = iterator.next();
            switch (entry.getType()) {
                case MAILBOX:
                    mailboxes.add((MailboxWithAnnotationsArchiveEntry) entry);
                    break;
                case MESSAGE:
                    //Ignore for know, TODO: implementation
                    break;
                case UNKNOWN:
                    String entryName = ((UnknownArchiveEntry) entry).getEntryName();
                    LOGGER.warn("unknown entry found in zip :" + entryName);
                    break;
            }
        }
        return mailboxes.build();
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
