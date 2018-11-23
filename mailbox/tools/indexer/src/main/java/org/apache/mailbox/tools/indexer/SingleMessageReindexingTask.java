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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleMessageReindexingTask implements Task {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleMessageReindexingTask.class);

    public static final String MESSAGE_RE_INDEXING = "messageReIndexing";

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final Mailbox mailbox;
        private final MessageUid uid;

        AdditionalInformation(Mailbox mailbox, MessageUid uid) {
            this.mailbox = mailbox;
            this.uid = uid;
        }

        public String getMailboxPath() {
            return mailbox.generateAssociatedPath().asString();
        }

        public String getMailboxId() {
            return mailbox.getMailboxId().serialize();
        }

        public long getUid() {
            return uid.asLong();
        }
    }

    private final ReIndexerPerformer reIndexerPerformer;
    private final Mailbox mailbox;
    private final MessageUid uid;
    private final AdditionalInformation additionalInformation;

    @Inject
    public SingleMessageReindexingTask(ReIndexerPerformer reIndexerPerformer, Mailbox mailbox, MessageUid uid) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.mailbox = mailbox;
        this.uid = uid;
        this.additionalInformation = new AdditionalInformation(mailbox, uid);
    }

    @Override
    public Result run() {
        try {
            return reIndexerPerformer.handleMessageReIndexing(mailbox, uid);
        } catch (MailboxException e) {
            LOGGER.warn("Error encounteres while reindexing {} {} : {}", mailbox.getMailboxId(), mailbox.generateAssociatedPath(), uid, e);
            return Result.PARTIAL;
        }
    }

    @Override
    public String type() {
        return MESSAGE_RE_INDEXING;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }
}
