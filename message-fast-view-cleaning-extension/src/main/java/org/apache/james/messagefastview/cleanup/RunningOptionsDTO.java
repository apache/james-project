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

package org.apache.james.messagefastview.cleanup;

import java.util.Optional;

import org.apache.james.messagefastview.cleanup.MessageFastViewCleanupService.RunningOptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RunningOptionsDTO {
    public static RunningOptionsDTO asDTO(RunningOptions domainObject) {
        return new RunningOptionsDTO(Optional.of(domainObject.getMessageIdsPerSecond()));
    }

    private final Optional<Integer> messageIdsPerSecond;

    @JsonCreator
    public RunningOptionsDTO(
            @JsonProperty("messageIdsPerSecond") Optional<Integer> messageIdsPerSecond) {
        this.messageIdsPerSecond = messageIdsPerSecond;
    }

    public Optional<Integer> getMessageIdsPerSecond() {
        return messageIdsPerSecond;
    }

    public RunningOptions asDomainObject() {
        return RunningOptions.of(messageIdsPerSecond.orElse(RunningOptions.DEFAULT_MESSAGE_IDS_PER_SECOND));
    }
}
