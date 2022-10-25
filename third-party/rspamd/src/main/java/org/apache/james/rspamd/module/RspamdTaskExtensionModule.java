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

package org.apache.james.rspamd.module;

import java.time.Clock;
import java.util.Set;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.rspamd.client.RspamdClientConfiguration;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.rspamd.task.FeedHamToRspamdTask;
import org.apache.james.rspamd.task.FeedHamToRspamdTaskAdditionalInformationDTO;
import org.apache.james.rspamd.task.FeedHamToRspamdTaskDTO;
import org.apache.james.rspamd.task.FeedSpamToRspamdTask;
import org.apache.james.rspamd.task.FeedSpamToRspamdTaskAdditionalInformationDTO;
import org.apache.james.rspamd.task.FeedSpamToRspamdTaskDTO;
import org.apache.james.server.task.json.TaskExtensionModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.user.api.UsersRepository;

import com.google.inject.Inject;

public class RspamdTaskExtensionModule implements TaskExtensionModule {

    private final TaskDTOModule<FeedSpamToRspamdTask, FeedSpamToRspamdTaskDTO> feedSpamTaskDTOModule;
    private final TaskDTOModule<FeedHamToRspamdTask, FeedHamToRspamdTaskDTO> feedHamTaskDTOModule;

    @Inject
    public RspamdTaskExtensionModule(MailboxManager mailboxManager,
                                     UsersRepository usersRepository,
                                     MessageIdManager messageIdManager,
                                     MailboxSessionMapperFactory mapperFactory,
                                     RspamdHttpClient rspamdHttpClient,
                                     Clock clock,
                                     RspamdClientConfiguration rspamdConfiguration) {
        this.feedSpamTaskDTOModule = FeedSpamToRspamdTaskDTO.module(mailboxManager, usersRepository, messageIdManager, mapperFactory, rspamdHttpClient, clock, rspamdConfiguration);
        this.feedHamTaskDTOModule = FeedHamToRspamdTaskDTO.module(mailboxManager, usersRepository, messageIdManager, mapperFactory, rspamdHttpClient, clock, rspamdConfiguration);
    }

    @Override
    public Set<TaskDTOModule<? extends Task, ? extends TaskDTO>> taskDTOModules() {
        return Set.of(feedSpamTaskDTOModule, feedHamTaskDTOModule);
    }

    @Override
    public Set<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>> taskAdditionalInformationDTOModules() {
        return Set.of(FeedSpamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE,
            FeedHamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE);
    }
}
