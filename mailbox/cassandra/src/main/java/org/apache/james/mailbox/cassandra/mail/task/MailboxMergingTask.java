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

package org.apache.james.mailbox.cassandra.mail.task;

import java.util.Optional;

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;

public class MailboxMergingTask implements Task {
    public static final String MAILBOX_MERGING = "mailboxMerging";

    public static class Details implements TaskExecutionDetails.AdditionalInformation {
        private final CassandraId oldMailboxId;
        private final CassandraId newMailboxId;

        public Details(CassandraId oldId, CassandraId newId) {
            this.oldMailboxId = oldId;
            this.newMailboxId = newId;
        }

        public String getOldMailboxId() {
            return oldMailboxId.serialize();
        }

        public String getNewMailboxId() {
            return newMailboxId.serialize();
        }
    }

    private final MailboxMergingTaskRunner taskRunner;
    private final CassandraId oldMailboxId;
    private final CassandraId newMailboxId;

    public MailboxMergingTask(MailboxMergingTaskRunner taskRunner, CassandraId oldMailboxId, CassandraId newMailboxId) {
        this.taskRunner = taskRunner;
        this.oldMailboxId = oldMailboxId;
        this.newMailboxId = newMailboxId;
    }

    @Override
    public Result run() {
        return taskRunner.run(oldMailboxId, newMailboxId);
    }

    @Override
    public String type() {
        return MAILBOX_MERGING;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new Details(oldMailboxId, newMailboxId));
    }
}
