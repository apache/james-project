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

package org.apache.james.rspamd.task;

import java.time.Clock;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.rspamd.client.RSpamDHttpClient;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.user.api.UsersRepository;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FeedHamToRSpamDTaskDTO implements TaskDTO {
    public static TaskDTOModule<FeedHamToRSpamDTask, FeedHamToRSpamDTaskDTO> module(MailboxManager mailboxManager,
                                                                                    UsersRepository usersRepository,
                                                                                    MessageIdManager messageIdManager,
                                                                                    MailboxSessionMapperFactory mapperFactory,
                                                                                    RSpamDHttpClient rSpamDHttpClient,
                                                                                    Clock clock) {
        return DTOModule.forDomainObject(FeedHamToRSpamDTask.class)
            .convertToDTO(FeedHamToRSpamDTaskDTO.class)
            .toDomainObjectConverter(dto -> new FeedHamToRSpamDTask(mailboxManager,
                usersRepository,
                messageIdManager,
                mapperFactory,
                rSpamDHttpClient,
                new FeedHamToRSpamDTask.RunningOptions(Optional.ofNullable(dto.getPeriodInSecond()),
                    dto.getMessagesPerSecond(),
                    dto.getSamplingProbability()),
                clock))
            .toDTOConverter((domain, type) -> new FeedHamToRSpamDTaskDTO(type,
                domain.getRunningOptions().getPeriodInSecond().orElse(null),
                domain.getRunningOptions().getMessagesPerSecond(),
                domain.getRunningOptions().getSamplingProbability()))
            .typeName(FeedHamToRSpamDTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }


    private final String type;
    private final Long periodInSecond;
    private final int messagesPerSecond;
    private final double samplingProbability;

    public FeedHamToRSpamDTaskDTO(@JsonProperty("type") String type,
                                  @JsonProperty("periodInSecond") Long periodInSecond,
                                  @JsonProperty("messagesPerSecond") int messagesPerSecond,
                                  @JsonProperty("samplingProbability") double samplingProbability) {
        this.type = type;
        this.periodInSecond = periodInSecond;
        this.messagesPerSecond = messagesPerSecond;
        this.samplingProbability = samplingProbability;
    }

    @Override
    public String getType() {
        return type;
    }

    public Long getPeriodInSecond() {
        return periodInSecond;
    }

    public int getMessagesPerSecond() {
        return messagesPerSecond;
    }

    public double getSamplingProbability() {
        return samplingProbability;
    }
}