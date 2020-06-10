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

import java.time.Clock;

import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.webadmin.dto.DTOModuleInjections;
import org.apache.james.webadmin.service.ClearMailRepositoryTask;
import org.apache.james.webadmin.service.ClearMailRepositoryTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ClearMailRepositoryTaskDTO;
import org.apache.james.webadmin.service.ReprocessingAllMailsTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ReprocessingAllMailsTaskDTO;
import org.apache.james.webadmin.service.ReprocessingOneMailTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ReprocessingOneMailTaskDTO;
import org.apache.james.webadmin.service.ReprocessingService;
import org.apache.james.webadmin.service.WebAdminClearMailRepositoryTaskAdditionalInformationDTO;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;

public class MailRepositoryTaskSerializationModule extends AbstractModule {
    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> clearMailRepositoryTask(ClearMailRepositoryTask.Factory factory) {
        return ClearMailRepositoryTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> reprocessingAllMailsTask(ReprocessingService reprocessingService) {
        return ReprocessingAllMailsTaskDTO.module(reprocessingService);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> reprocessingOneMailsTask(ReprocessingService reprocessingService) {
        return ReprocessingOneMailTaskDTO.module(Clock.systemUTC(), reprocessingService);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> clearMailRepositoryAdditionalInformation() {
        return ClearMailRepositoryTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminClearMailRepositoryAdditionalInformation() {
        return WebAdminClearMailRepositoryTaskAdditionalInformationDTO.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> reprocessingAllMailsAdditionalInformation() {
        return ReprocessingAllMailsTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminReprocessingAllMailsAdditionalInformation() {
        return ReprocessingAllMailsTaskAdditionalInformationDTO.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> reprocessingOneMailAdditionalInformation() {
        return ReprocessingOneMailTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminReprocessingOneMailAdditionalInformation() {
        return ReprocessingOneMailTaskAdditionalInformationDTO.module();
    }
}
