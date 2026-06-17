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

import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxInconsistenciesService.RunningOptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SolveMailboxInconsistenciesRunningOptionsDTO {
    public static SolveMailboxInconsistenciesRunningOptionsDTO asDTO(RunningOptions domainObject) {
        return new SolveMailboxInconsistenciesRunningOptionsDTO(
            Optional.of(domainObject.getMaxIterations()),
            Optional.of(domainObject.isAutoMerge()));
    }

    private final Optional<Integer> maxIterations;
    private final Optional<Boolean> autoMerge;

    @JsonCreator
    public SolveMailboxInconsistenciesRunningOptionsDTO(@JsonProperty("maxIterations") Optional<Integer> maxIterations,
                                                        @JsonProperty("autoMerge") Optional<Boolean> autoMerge) {
        this.maxIterations = maxIterations;
        this.autoMerge = autoMerge;
    }

    public Optional<Integer> getMaxIterations() {
        return maxIterations;
    }

    public Optional<Boolean> getAutoMerge() {
        return autoMerge;
    }

    public RunningOptions asDomainObject() {
        return new RunningOptions(
            maxIterations.orElse(RunningOptions.DEFAULT_MAX_ITERATIONS),
            autoMerge.orElse(RunningOptions.DEFAULT_AUTO_MERGE));
    }
}
