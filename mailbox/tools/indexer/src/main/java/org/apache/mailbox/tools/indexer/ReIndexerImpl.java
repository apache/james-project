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

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxReactorUtils;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
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
    public Task reIndex(MailboxPath path, RunningOptions runningOptions) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(path.getUser());

        MailboxId mailboxId = mailboxManager.getMailbox(path, mailboxSession).getId();

        return new SingleMailboxReindexingTask(reIndexerPerformer, mailboxId, runningOptions);
    }

    @Override
    public Task reIndex(MailboxId mailboxId, RunningOptions runningOptions) throws MailboxException {
        validateIdExists(mailboxId);

        return new SingleMailboxReindexingTask(reIndexerPerformer, mailboxId, runningOptions);
    }

    @Override
    public Task reIndex(RunningOptions runningOptions) {
        return new FullReindexingTask(reIndexerPerformer, runningOptions);
    }

    @Override
    public Task reIndex(Username username, RunningOptions runningOptions) {
        return new UserReindexingTask(reIndexerPerformer, username, runningOptions);
    }

    @Override
    public Task reIndex(MailboxPath path, MessageUid uid) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(path.getUser());

        MailboxId mailboxId = mailboxManager.getMailbox(path, mailboxSession).getId();

        return new SingleMessageReindexingTask(reIndexerPerformer, mailboxId, uid);
    }

    @Override
    public Task reIndex(MailboxId mailboxId, MessageUid uid) throws MailboxException {
        validateIdExists(mailboxId);

        return new SingleMessageReindexingTask(reIndexerPerformer, mailboxId, uid);
    }

    @Override
    public Task reIndex(ReIndexingExecutionFailures previousFailures, RunningOptions runningOptions) {
        return new ErrorRecoveryIndexationTask(reIndexerPerformer, previousFailures, runningOptions);
    }

    private void validateIdExists(MailboxId mailboxId) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(Username.of("ReIndexingImap"));
        MailboxReactorUtils.block(mapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId));
        mailboxManager.endProcessingRequest(mailboxSession);
    }
}
