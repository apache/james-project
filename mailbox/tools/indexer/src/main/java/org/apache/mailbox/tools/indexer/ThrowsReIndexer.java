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

import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Task;

public class ThrowsReIndexer implements ReIndexer {
    @Override
    public Task reIndex(MailboxPath path, RunningOptions runningOptions) throws MailboxException {
        throw new MailboxException("Not implemented");
    }

    @Override
    public Task reIndex(MailboxId mailboxId, RunningOptions runningOptions) throws MailboxException {
        throw new MailboxException("Not implemented");
    }

    @Override
    public Task reIndex(RunningOptions runningOptions) throws MailboxException {
        throw new MailboxException("Not implemented");
    }

    @Override
    public Task reIndex(Username username, RunningOptions runningOptions) throws MailboxException {
        throw new MailboxException("Not implemented");
    }

    @Override
    public Task reIndex(MailboxPath path, MessageUid uid) throws MailboxException {
        throw new MailboxException("Not implemented");
    }

    @Override
    public Task reIndex(MailboxId mailboxId, MessageUid uid) throws MailboxException {
        throw new MailboxException("Not implemented");
    }

    @Override
    public Task reIndex(ReIndexingExecutionFailures previousFailures, RunningOptions runningOptions) throws MailboxException {
        throw new MailboxException("Not implemented");
    }
}
