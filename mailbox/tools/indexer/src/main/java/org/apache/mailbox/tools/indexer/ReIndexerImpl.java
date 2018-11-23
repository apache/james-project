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

package org.apache.mailbox.tools.indexer;

import javax.inject.Inject;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.task.Task;

/**
 * Note about live re-indexation handling :
 *
 *  - Data races may arise... If you modify the stored value between the received event check and the index operation,
 *  you have an inconsistent behavior (for mailbox renames).
 *
 *  A mechanism for tracking mailbox renames had been implemented, and is taken into account when starting re-indexing a mailbox.
 *  Note that if a mailbox is renamed during its re-indexation process, it will not be taken into account. (We just reduce the inconsistency window).
 */
public class ReIndexerImpl implements ReIndexer {

    private final ReIndexerPerformer reIndexerPerformer;
    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mapperFactory;

    @Inject
    public ReIndexerImpl(ReIndexerPerformer reIndexerPerformer, MailboxManager mailboxManager, MailboxSessionMapperFactory mapperFactory) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.mailboxManager = mailboxManager;
        this.mapperFactory = mapperFactory;
    }

    @Override
    public Task reIndex(MailboxPath path) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession("ReIndexingImap");
        Mailbox mailbox = mapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(path);
        return new SingleMailboxReindexingTask(reIndexerPerformer, mailbox);
    }

    @Override
    public Task reIndex(MailboxId mailboxId) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession("ReIndexingImap");
        Mailbox mailbox = mapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);
        return new SingleMailboxReindexingTask(reIndexerPerformer, mailbox);
    }

    @Override
    public Task reIndex() {
        return new FullReindexingTask(reIndexerPerformer);
    }

    @Override
    public Task reIndex(User user) {
        return new UserReindexingTask(reIndexerPerformer, user);
    }

    @Override
    public Task reIndex(MailboxPath path, MessageUid uid) {
        return new SingleMessageReindexingTask(reIndexerPerformer, path, uid);
    }
}
