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

import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RunningOptionsDTO {
    public static RunningOptionsDTO toDTO(RunningOptions runningOptions) {
        return new RunningOptionsDTO(
            Optional.of(runningOptions.getMessagesPerSecond()),
            Optional.of(runningOptions.getMode()));
    }

    private final Optional<Integer> messagesPerSecond;
    private final Optional<RunningOptions.Mode> mode;

    @JsonCreator
    public RunningOptionsDTO(@JsonProperty("messagesPerSecond") Optional<Integer> messagesPerSecond,
                             @JsonProperty("mode") Optional<RunningOptions.Mode> mode) {
        this.messagesPerSecond = messagesPerSecond;
        this.mode = mode;
    }

    public Optional<Integer> getMessagesPerSecond() {
        return messagesPerSecond;
    }

    public Optional<RunningOptions.Mode> getMode() {
        return mode;
    }

    public RunningOptions toDomainObject() {
        return RunningOptions.builder()
            .messagesPerSeconds(messagesPerSecond)
            .mode(mode)
            .build();
    }
}

