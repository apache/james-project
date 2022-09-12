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
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.user.api.UsersRepository;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FeedSpamToRspamdTaskDTO implements TaskDTO {
    public static TaskDTOModule<FeedSpamToRspamdTask, FeedSpamToRspamdTaskDTO> module(MailboxManager mailboxManager,
                                                                                      UsersRepository usersRepository,
                                                                                      MessageIdManager messageIdManager,
                                                                                      MailboxSessionMapperFactory mapperFactory,
                                                                                      RspamdHttpClient rspamdHttpClient,
                                                                                      Clock clock) {
        return DTOModule.forDomainObject(FeedSpamToRspamdTask.class)
            .convertToDTO(FeedSpamToRspamdTaskDTO.class)
            .toDomainObjectConverter(dto -> new FeedSpamToRspamdTask(mailboxManager,
                usersRepository,
                messageIdManager,
                mapperFactory,
                rspamdHttpClient,
                new RunningOptions(Optional.ofNullable(dto.getPeriodInSecond()),
                    dto.getMessagesPerSecond(),
                    dto.getSamplingProbability(),
                    dto.getClassifiedAsSpam()),
                clock))
            .toDTOConverter((domain, type) -> new FeedSpamToRspamdTaskDTO(
                type,
                domain.getRunningOptions().getPeriodInSecond().orElse(null),
                domain.getRunningOptions().getMessagesPerSecond(),
                domain.getRunningOptions().getSamplingProbability(),
                domain.getRunningOptions().getClassifiedAsSpam()))
            .typeName(FeedSpamToRspamdTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final Long periodInSecond;
    private final int messagesPerSecond;
    private final double samplingProbability;
    private final Optional<Boolean> classifiedAsSpam;


    public FeedSpamToRspamdTaskDTO(@JsonProperty("type") String type,
                                   @JsonProperty("periodInSecond") Long periodInSecond,
                                   @JsonProperty("messagesPerSecond") int messagesPerSecond,
                                   @JsonProperty("samplingProbability") double samplingProbability,
                                   @JsonProperty("classifiedAsSpam") Optional<Boolean> classifiedAsSpam) {
        this.type = type;
        this.periodInSecond = periodInSecond;
        this.messagesPerSecond = messagesPerSecond;
        this.samplingProbability = samplingProbability;
        this.classifiedAsSpam = classifiedAsSpam;
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

    public Optional<Boolean> getClassifiedAsSpam() {
        return classifiedAsSpam;
    }
}