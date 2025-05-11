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

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.json.DTOConverter;
import org.apache.james.server.task.json.TaskExtensionModule;
import org.apache.james.server.task.json.TaskModuleInjectionKeys;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.utils.ExtensionConfiguration;
import org.apache.james.utils.GuiceLoader;
import org.apache.james.utils.NamingScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

public class TaskSerializationModule extends AbstractModule {

    public static final Logger LOGGER = LoggerFactory.getLogger(TaskSerializationModule.class);

    @Provides
    @Named(TaskModuleInjectionKeys.ADDITIONAL_INFORMATION_DTO)
    @Singleton
    public Set<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>> provideAdditionalInformationDTOModules(
        Set<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>> additionalInformationDTOModules,
        ExtensionConfiguration extensionConfiguration,
        GuiceLoader loader) {

        Set<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>> extensionAdditionalInformationDTOModules = extensionConfiguration.getTaskExtensions()
            .stream()
            .map(Throwing.function(loader.<TaskExtensionModule>withNamingSheme(NamingScheme.IDENTITY)::instantiate))
            .map(TaskExtensionModule::taskAdditionalInformationDTOModules)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

        LOGGER.debug("TaskSerialization/AdditionalInformationDTOModule size = {}", extensionAdditionalInformationDTOModules.size());

        return ImmutableSet.<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>>builder()
            .addAll(additionalInformationDTOModules)
            .addAll(extensionAdditionalInformationDTOModules)
            .build();
    }

    @Provides
    @Named(TaskModuleInjectionKeys.TASK_DTO)
    @Singleton
    public Set<TaskDTOModule<? extends Task, ? extends TaskDTO>> provideTaskDTOModules(
        Set<TaskDTOModule<? extends Task, ? extends TaskDTO>> taskDTOModules,
        ExtensionConfiguration extensionConfiguration,
        GuiceLoader loader) {

        Set<TaskDTOModule<? extends Task, ? extends TaskDTO>> extensionTaskDTOModules = extensionConfiguration.getTaskExtensions()
            .stream()
            .map(Throwing.function(loader.<TaskExtensionModule>withNamingSheme(NamingScheme.IDENTITY)::instantiate))
            .map(TaskExtensionModule::taskDTOModules)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

        LOGGER.debug("TaskSerialization/TaskDTOModule size = {}", extensionTaskDTOModules.size());

        return ImmutableSet.<TaskDTOModule<? extends Task, ? extends TaskDTO>>builder()
            .addAll(taskDTOModules)
            .addAll(extensionTaskDTOModules)
            .build();
    }

    @Provides
    @Singleton
    public DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationDTOConverter(@Named(TaskModuleInjectionKeys.ADDITIONAL_INFORMATION_DTO) Set<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>> modules) {
        return new DTOConverter<>(modules);
    }

    @Provides
    @Singleton
    public DTOConverter<Task, TaskDTO> taskDTOConverter(@Named(TaskModuleInjectionKeys.TASK_DTO) Set<TaskDTOModule<? extends Task, ? extends TaskDTO>> taskDTOModules) {
        return new DTOConverter<>(taskDTOModules);
    }
}
