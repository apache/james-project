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
package org.apache.james.modules.server;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasTaskAdditionalInformationDTO;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasTaskDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.webadmin.dto.DTOModuleInjections;
import org.apache.james.webadmin.service.ClearMailboxContentTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ClearMailboxContentTaskDTO;
import org.apache.james.webadmin.service.CreateMissingParentsTask;
import org.apache.james.webadmin.service.CreateMissingParentsTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.EventDeadLettersRedeliverAllTaskDTO;
import org.apache.james.webadmin.service.EventDeadLettersRedeliverGroupTaskDTO;
import org.apache.james.webadmin.service.EventDeadLettersRedeliverOneTaskDTO;
import org.apache.james.webadmin.service.EventDeadLettersRedeliverService;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.SubscribeAllTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.SubscribeAllTaskDTO;
import org.apache.james.webadmin.service.UserMailboxesService;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;

public class WebadminMailboxTaskSerializationModule extends AbstractModule {
    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> eventDeadLettersRedeliverAllTask(EventDeadLettersRedeliverService service) {
        return EventDeadLettersRedeliverAllTaskDTO.module(service);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> eventDeadLettersRedeliverGroupTask(EventDeadLettersRedeliverService service) {
        return EventDeadLettersRedeliverGroupTaskDTO.module(service);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> eventDeadLettersRedeliverOneTask(EventDeadLettersRedeliverService service) {
        return EventDeadLettersRedeliverOneTaskDTO.module(service);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> subscribeAllTask(MailboxManager mailboxManager, SubscriptionManager subscriptionManager) {
        return SubscribeAllTaskDTO.module(mailboxManager, subscriptionManager);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> recomputeCurrentQuotasTask(RecomputeCurrentQuotasService service) {
        return RecomputeCurrentQuotasTaskDTO.module(service);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> createMissingParentsTask(MailboxManager mailboxManager) {
        return CreateMissingParentsTask.module(mailboxManager);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> clearMailboxContentTask(UserMailboxesService userMailboxesService) {
        return ClearMailboxContentTaskDTO.module(userMailboxesService);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> eventDeadLettersRedeliveryAdditionalInformationForAll() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForAll.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> subscribeAllTaskDTO() {
        return SubscribeAllTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminEventDeadLettersRedeliveryAdditionalInformationForAll() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForAll.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> eventDeadLettersRedeliveryAdditionalInformationForGroup() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminEventDeadLettersRedeliveryAdditionalInformationForGroup() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> eventDeadLettersRedeliveryAdditionalInformationForOne() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForOne.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminEventDeadLettersRedeliveryAdditionalInformationForOne() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForOne.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> recomputeCurrentQuotasAdditionalInformation() {
        return RecomputeCurrentQuotasTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminRecomputeCurrentQuotasAdditionalInformation() {
        return RecomputeCurrentQuotasTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> subscribeAllTaskWebAdminDTO() {
        return SubscribeAllTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> createMissingParentsAdditionalInformation() {
        return CreateMissingParentsTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminCreateMissingParentsAdditionalInformation() {
        return CreateMissingParentsTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> clearMailboxContentAdditionalInformation() {
        return ClearMailboxContentTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminClearMailboxContentAdditionalInformation() {
        return ClearMailboxContentTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

}
