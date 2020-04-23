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

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService.Options;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecomputeMailboxCountersTaskDTO implements TaskDTO {
    private static RecomputeMailboxCountersTaskDTO toDTO(RecomputeMailboxCountersTask domainObject, String typeName) {
        return new RecomputeMailboxCountersTaskDTO(typeName, Optional.of(domainObject.getOptions().isMessageDenormalizationTrusted()));
    }

    public static TaskDTOModule<RecomputeMailboxCountersTask, RecomputeMailboxCountersTaskDTO> module(RecomputeMailboxCountersService service) {
        return DTOModule
            .forDomainObject(RecomputeMailboxCountersTask.class)
            .convertToDTO(RecomputeMailboxCountersTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDomainObject(service))
            .toDTOConverter(RecomputeMailboxCountersTaskDTO::toDTO)
            .typeName(RecomputeMailboxCountersTask.RECOMPUTE_MAILBOX_COUNTERS.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final Optional<Boolean> trustMessageDenormalization;

    public RecomputeMailboxCountersTaskDTO(@JsonProperty("type") String type,
                                           @JsonProperty("trustMessageDenormalization") Optional<Boolean> trustMessageDenormalization) {
        this.type = type;
        this.trustMessageDenormalization = trustMessageDenormalization;
    }

    private RecomputeMailboxCountersTask toDomainObject(RecomputeMailboxCountersService service) {
        Options options = trustMessageDenormalization.map(Options::of).orElse(Options.recheckMessageDenormalization());
        return new RecomputeMailboxCountersTask(service, options);
    }

    @Override
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("trustMessageDenormalization")
    public Optional<Boolean> trustMessageDenormalization() {
        return trustMessageDenormalization;
    }
}
