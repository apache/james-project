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

package org.apache.james.webadmin.data.jmap;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.RuleDTO;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.filtering.Version;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.webadmin.validation.MailboxName;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class RunRulesOnMailboxTaskDTO implements TaskDTO {
    private final String type;
    private final String username;
    private final String mailboxName;
    private final ImmutableList<RuleDTO> rules;

    public RunRulesOnMailboxTaskDTO(@JsonProperty("type") String type,
                                    @JsonProperty("username") String username,
                                    @JsonProperty("mailboxName") String mailboxName,
                                    @JsonProperty("rules") ImmutableList<RuleDTO> rules) {
        this.type = type;
        this.username = username;
        this.mailboxName = mailboxName;
        this.rules = rules;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public String getMailboxName() {
        return mailboxName;
    }

    public ImmutableList<RuleDTO> getRules() {
        return rules;
    }

    public static TaskDTOModule<RunRulesOnMailboxTask, RunRulesOnMailboxTaskDTO> module(RunRulesOnMailboxService runRulesOnMailboxService) {
        return DTOModule
            .forDomainObject(RunRulesOnMailboxTask.class)
            .convertToDTO(RunRulesOnMailboxTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(runRulesOnMailboxService))
            .toDTOConverter(RunRulesOnMailboxTaskDTO::toDTO)
            .typeName(RunRulesOnMailboxTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public RunRulesOnMailboxTask fromDTO(RunRulesOnMailboxService runRulesOnMailboxService) {
        return new RunRulesOnMailboxTask(Username.of(username), new MailboxName(mailboxName), new Rules(RuleDTO.toRules(rules), Version.INITIAL), runRulesOnMailboxService);
    }

    public static RunRulesOnMailboxTaskDTO toDTO(RunRulesOnMailboxTask domainObject, String typeName) {
        return new RunRulesOnMailboxTaskDTO(typeName, domainObject.getUsername().asString(), domainObject.getMailboxName().asString(), RuleDTO.from(domainObject.getRules().getRules()));
    }
}
