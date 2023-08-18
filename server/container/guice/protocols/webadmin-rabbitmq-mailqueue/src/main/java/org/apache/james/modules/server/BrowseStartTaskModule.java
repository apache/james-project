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

import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueView;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.webadmin.dto.DTOModuleInjections;
import org.apache.james.webadmin.routes.BrowseStartUpdateTaskRegistration;
import org.apache.james.webadmin.service.BrowseStartUpdateTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.BrowseStartUpdateTaskDTO;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class BrowseStartTaskModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), TaskFromRequestRegistry.TaskRegistration.class, Names.named("RabbitMQMailQueuesRoutes"))
            .addBinding()
            .to(BrowseStartUpdateTaskRegistration.class);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> updateBrowseStartTask(CassandraMailQueueView.Factory view) {
        return BrowseStartUpdateTaskDTO.module(view);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> updateBrowseStartAdditionalInformation() {
        return BrowseStartUpdateTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> updateBrowseStartAdditionalInformationWebAdmin() {
        return BrowseStartUpdateTaskAdditionalInformationDTO.module();
    }
}
