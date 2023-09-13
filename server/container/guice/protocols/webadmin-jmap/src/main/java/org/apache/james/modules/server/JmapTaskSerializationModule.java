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

import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.mailbox.quota.task.RecomputeJMAPUploadCurrentQuotasService;
import org.apache.james.mailbox.quota.task.RecomputeSingleComponentCurrentQuotasService;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.data.jmap.EmailQueryViewPopulator;
import org.apache.james.webadmin.data.jmap.MessageFastViewProjectionCorrector;
import org.apache.james.webadmin.data.jmap.PopulateEmailQueryViewTask;
import org.apache.james.webadmin.data.jmap.PopulateEmailQueryViewTaskAdditionalInformationDTO;
import org.apache.james.webadmin.data.jmap.PopulateFilteringProjectionTask;
import org.apache.james.webadmin.data.jmap.PopulateFilteringProjectionTaskAdditionalInformationDTO;
import org.apache.james.webadmin.data.jmap.RecomputeAllFastViewProjectionItemsTask;
import org.apache.james.webadmin.data.jmap.RecomputeAllFastViewTaskAdditionalInformationDTO;
import org.apache.james.webadmin.data.jmap.RecomputeUserFastViewProjectionItemsTask;
import org.apache.james.webadmin.data.jmap.RecomputeUserFastViewTaskAdditionalInformationDTO;
import org.apache.james.webadmin.dto.DTOModuleInjections;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;

public class JmapTaskSerializationModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), RecomputeSingleComponentCurrentQuotasService.class)
            .addBinding()
            .to(RecomputeJMAPUploadCurrentQuotasService.class);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> recomputeAllJmapPreviewsTask(MessageFastViewProjectionCorrector corrector) {
        return RecomputeAllFastViewProjectionItemsTask.module(corrector);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> populateEmailQueryViewTask(EmailQueryViewPopulator populator) {
        return PopulateEmailQueryViewTask.module(populator);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> populateFilteringProjectionTask(EventSourcingFilteringManagement.NoReadProjection noReadProjection,
                                                                                            EventSourcingFilteringManagement.ReadProjection readProjection,
                                                                                            UsersRepository usersRepository) {
        return PopulateFilteringProjectionTask.module(noReadProjection, readProjection, usersRepository);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> recomputeUserJmapPreviewsTask(MessageFastViewProjectionCorrector corrector) {
        return RecomputeUserFastViewProjectionItemsTask.module(corrector);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> recomputeAllJmapPreviewsAdditionalInformation() {
        return RecomputeAllFastViewTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminRecomputeAllJmapPreviewsAdditionalInformation() {
        return RecomputeAllFastViewTaskAdditionalInformationDTO.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> populateEmailQueryViewAdditionalInformation() {
        return PopulateEmailQueryViewTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminPopulateEmailQueryViewAdditionalInformation() {
        return PopulateEmailQueryViewTaskAdditionalInformationDTO.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> populateFilteringProjectionAdditionalInformation() {
        return PopulateFilteringProjectionTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminPopulateFilteringProjectionAdditionalInformation() {
        return PopulateFilteringProjectionTaskAdditionalInformationDTO.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> recomputeUserJmapPreviewsAdditionalInformation() {
        return RecomputeUserFastViewTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> webAdminRecomputeUserJmapPreviewsAdditionalInformation() {
        return RecomputeUserFastViewTaskAdditionalInformationDTO.module();
    }
}
