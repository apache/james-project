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

import java.util.Optional;

import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.RunningOptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RunningOptionsDTO {
    public static RunningOptionsDTO asDTO(RunningOptions domainObject) {
        return new RunningOptionsDTO(Optional.of(domainObject.getUsersPerSecond()), domainObject.getQuotaComponent().map(QuotaComponent::getValue));
    }

    private final Optional<Integer> usersPerSecond;
    private final Optional<String> quotaComponent;

    @JsonCreator
    public RunningOptionsDTO(
            @JsonProperty("usersPerSecond") Optional<Integer> usersPerSecond,
            @JsonProperty("quotaComponent") Optional<String> quotaComponent) {
        this.usersPerSecond = usersPerSecond;
        this.quotaComponent = quotaComponent;
    }

    public Optional<Integer> getUsersPerSecond() {
        return usersPerSecond;
    }

    public RunningOptions asDomainObject() {
        return RunningOptions.of(usersPerSecond.orElse(RunningOptions.DEFAULT_USERS_PER_SECOND), quotaComponent.map(QuotaComponent::of));
    }
}
