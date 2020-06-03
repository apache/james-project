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

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.MailboxId;

import com.google.common.collect.ImmutableList;

class ReprocessingContext {
    private final AtomicInteger successfullyReprocessedMails;
    private final AtomicInteger failedReprocessingMails;
    private final ConcurrentLinkedDeque<ReIndexingExecutionFailures.ReIndexingFailure> failures;
    private final ConcurrentLinkedDeque<MailboxId> mailboxFailures;

    ReprocessingContext() {
        failedReprocessingMails = new AtomicInteger(0);
        successfullyReprocessedMails = new AtomicInteger(0);
        failures = new ConcurrentLinkedDeque<>();
        mailboxFailures = new ConcurrentLinkedDeque<>();
    }

    void recordFailureDetailsForMessage(MailboxId mailboxId, MessageUid uid) {
        failures.add(new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, uid));
        failedReprocessingMails.incrementAndGet();
    }

    void recordSuccess() {
        successfullyReprocessedMails.incrementAndGet();
    }

    void recordMailboxFailure(MailboxId mailboxId) {
        mailboxFailures.add(mailboxId);
    }

    int successfullyReprocessedMailCount() {
        return successfullyReprocessedMails.get();
    }

    int failedReprocessingMailCount() {
        return failedReprocessingMails.get();
    }

    ReIndexingExecutionFailures failures() {
        return new ReIndexingExecutionFailures(ImmutableList.copyOf(failures), ImmutableList.copyOf(mailboxFailures));
    }
}
