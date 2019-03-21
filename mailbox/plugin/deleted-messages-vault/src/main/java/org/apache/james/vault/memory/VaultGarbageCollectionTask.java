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

package org.apache.james.vault.memory;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;

public class VaultGarbageCollectionTask implements Task {
    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final ZonedDateTime beginningOfRetentionPeriod;
        private final long handledUserCount;
        private final long permanantlyDeletedMessages;
        private final long vaultSearchErrorCount;
        private final long deletionErrorCount;

        AdditionalInformation(ZonedDateTime beginningOfRetentionPeriod, long handledUserCount, long permanantlyDeletedMessages, long vaultSearchErrorCount, long deletionErrorCount) {
            this.beginningOfRetentionPeriod = beginningOfRetentionPeriod;
            this.handledUserCount = handledUserCount;
            this.permanantlyDeletedMessages = permanantlyDeletedMessages;
            this.vaultSearchErrorCount = vaultSearchErrorCount;
            this.deletionErrorCount = deletionErrorCount;
        }

        public  ZonedDateTime getBeginningOfRetentionPeriod() {
            return beginningOfRetentionPeriod;
        }

        public long getHandledUserCount() {
            return handledUserCount;
        }

        public long getPermanantlyDeletedMessages() {
            return permanantlyDeletedMessages;
        }

        public long getVaultSearchErrorCount() {
            return vaultSearchErrorCount;
        }

        public long getDeletionErrorCount() {
            return deletionErrorCount;
        }
    }

    private static final String TYPE = "deletedMessages/garbageCollection";

    private final DeleteByQueryExecutor deleteByQueryExecutor;
    private final DeleteByQueryExecutor.Notifiers notifiers;
    private final AtomicLong handledUserCount;
    private final AtomicLong permanantlyDeletedMessages;
    private final AtomicLong vaultSearchErrorCount;
    private final AtomicLong deletionErrorCount;
    private final ZonedDateTime beginningOfRetentionPeriod;

    public VaultGarbageCollectionTask(DeleteByQueryExecutor deleteByQueryExecutor, ZonedDateTime beginningOfRetentionPeriod) {
        this.deleteByQueryExecutor = deleteByQueryExecutor;
        this.beginningOfRetentionPeriod = beginningOfRetentionPeriod;

        this.handledUserCount = new AtomicLong(0);
        this.permanantlyDeletedMessages = new AtomicLong(0);
        this.vaultSearchErrorCount = new AtomicLong(0);
        this.deletionErrorCount = new AtomicLong(0);

        this.notifiers = new DeleteByQueryExecutor.Notifiers(
            handledUserCount::incrementAndGet,
            vaultSearchErrorCount::incrementAndGet,
            deletionErrorCount::incrementAndGet,
            permanantlyDeletedMessages::incrementAndGet);
    }

    @Override
    public Result run() {
        Query query = Query.of(CriterionFactory.deletionDate().beforeOrEquals(beginningOfRetentionPeriod));

        return deleteByQueryExecutor.deleteByQuery(query, notifiers);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(
            beginningOfRetentionPeriod,
            handledUserCount.get(),
            permanantlyDeletedMessages.get(),
            vaultSearchErrorCount.get(),
            deletionErrorCount.get()));
    }
}
