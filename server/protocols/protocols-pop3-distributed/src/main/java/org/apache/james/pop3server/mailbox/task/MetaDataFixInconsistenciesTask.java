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

package org.apache.james.pop3server.mailbox.task;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesService.Context;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesService.RunningOptions;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.collect.ImmutableList;

public class MetaDataFixInconsistenciesTask implements Task {

    public static final TaskType TASK_TYPE = TaskType.of("Pop3MetaDataFixInconsistenciesTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        public static AdditionalInformation from(Context context,
                                                  RunningOptions runningOptions) {
            Context.Snapshot snapshot = context.snapshot();
            return new AdditionalInformation(
                Clock.systemUTC().instant(),
                runningOptions,
                snapshot.getProcessedImapUidEntries(),
                snapshot.getProcessedPop3MetaDataStoreEntries(),
                snapshot.getStalePOP3Entries(),
                snapshot.getMissingPOP3Entries(),
                snapshot.getFixedInconsistencies(),
                snapshot.getErrors());
        }

        private final Instant timestamp;
        private final RunningOptions runningOptions;
        private final long processedImapUidEntries;
        private final long processedPop3MetaDataStoreEntries;
        private final long stalePOP3Entries;
        private final long missingPOP3Entries;
        private final ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies;
        private final ImmutableList<MessageInconsistenciesEntry> errors;

        public AdditionalInformation(Instant timestamp,
                                     RunningOptions runningOptions,
                                     long processedImapUidEntries,
                                     long processedPop3MetaDataStoreEntries,
                                     long stalePOP3Entries,
                                     long missingPOP3Entries,
                                     ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies,
                                     ImmutableList<MessageInconsistenciesEntry> errors) {
            this.timestamp = timestamp;
            this.runningOptions = runningOptions;
            this.processedImapUidEntries = processedImapUidEntries;
            this.processedPop3MetaDataStoreEntries = processedPop3MetaDataStoreEntries;
            this.stalePOP3Entries = stalePOP3Entries;
            this.missingPOP3Entries = missingPOP3Entries;
            this.fixedInconsistencies = fixedInconsistencies;
            this.errors = errors;
        }

        public RunningOptions getRunningOptions() {
            return runningOptions;
        }

        public long getProcessedImapUidEntries() {
            return processedImapUidEntries;
        }

        public long getProcessedPop3MetaDataStoreEntries() {
            return processedPop3MetaDataStoreEntries;
        }

        public long getStalePOP3Entries() {
            return stalePOP3Entries;
        }

        public long getMissingPOP3Entries() {
            return missingPOP3Entries;
        }

        public ImmutableList<MessageInconsistenciesEntry> getFixedInconsistencies() {
            return fixedInconsistencies;
        }

        public ImmutableList<MessageInconsistenciesEntry> getErrors() {
            return errors;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    private final MetaDataFixInconsistenciesService metaDataFixInconsistenciesService;
    private final Context context;
    private final RunningOptions runningOptions;

    @Inject
    public MetaDataFixInconsistenciesTask(MetaDataFixInconsistenciesService metaDataFixInconsistenciesService,
                                          RunningOptions runningOptions) {
        this.metaDataFixInconsistenciesService = metaDataFixInconsistenciesService;
        this.context = new Context();
        this.runningOptions = runningOptions;
    }

    @Override
    public Result run() throws InterruptedException {
        return metaDataFixInconsistenciesService.fixInconsistencies(context, runningOptions)
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(context, runningOptions));
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }
}
