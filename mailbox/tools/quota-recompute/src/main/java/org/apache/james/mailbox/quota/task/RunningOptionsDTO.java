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

package org.apache.james.mailbox.quota.task;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.RunningOptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class RunningOptionsDTO {
    public static RunningOptionsDTO asDTO(RunningOptions domainObject) {
        return new RunningOptionsDTO(Optional.of(domainObject.getUsersPerSecond()),
            domainObject.getQuotaComponents().stream().map(QuotaComponent::getValue).collect(ImmutableList.toImmutableList()));
    }

    private final Optional<Integer> usersPerSecond;
    private final List<String> quotaComponents;

    @JsonCreator
    public RunningOptionsDTO(
            @JsonProperty("usersPerSecond") Optional<Integer> usersPerSecond,
            @JsonProperty("quotaComponent") List<String> quotaComponents) {
        this.usersPerSecond = usersPerSecond;
        this.quotaComponents = quotaComponents;
    }

    public Optional<Integer> getUsersPerSecond() {
        return usersPerSecond;
    }

    public RunningOptions asDomainObject() {
        return RunningOptions.of(usersPerSecond.orElse(RunningOptions.DEFAULT_USERS_PER_SECOND),
            quotaComponents.stream().map(QuotaComponent::of).collect(ImmutableList.toImmutableList()));
    }
}
