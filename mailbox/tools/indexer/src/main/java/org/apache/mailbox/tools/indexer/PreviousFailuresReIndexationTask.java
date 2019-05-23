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

import org.apache.james.mailbox.indexer.IndexingDetailInformation;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PreviousFailuresReIndexationTask implements Task {
    public static final String PREVIOUS_FAILURES_INDEXING = "ReIndexPreviousFailures";

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation, IndexingDetailInformation {
        private final ReprocessingContext reprocessingContext;

        AdditionalInformation(ReprocessingContext reprocessingContext) {
            this.reprocessingContext = reprocessingContext;
        }

        public int getSuccessfullyReprocessMailCount() {
            return reprocessingContext.successfullyReprocessedMailCount();
        }

        public int getFailedReprocessedMailCount() {
            return reprocessingContext.failedReprocessingMailCount();
        }
        @JsonIgnore
        public ReIndexingExecutionFailures failures() {
            return reprocessingContext.failures();
        }

        @JsonProperty("failures")
        public SerializableReIndexingExecutionFailures failuresAsJson() {
            return SerializableReIndexingExecutionFailures.from(failures());
        }
    }

    private final ReIndexerPerformer reIndexerPerformer;
    private final AdditionalInformation additionalInformation;
    private final ReprocessingContext reprocessingContext;
    private final ReIndexingExecutionFailures previousFailures;

    public PreviousFailuresReIndexationTask(ReIndexerPerformer reIndexerPerformer, ReIndexingExecutionFailures previousFailures) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.previousFailures = previousFailures;
        this.reprocessingContext = new ReprocessingContext();
        this.additionalInformation = new AdditionalInformation(reprocessingContext);
    }

    @Override
    public Result run() {
        return reIndexerPerformer.reIndex(reprocessingContext, previousFailures);
    }

    @Override
    public String type() {
        return PREVIOUS_FAILURES_INDEXING;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }
}
