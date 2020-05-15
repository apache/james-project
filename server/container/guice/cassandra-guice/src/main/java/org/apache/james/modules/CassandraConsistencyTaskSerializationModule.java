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
package org.apache.james.modules;

import org.apache.james.backends.cassandra.migration.MigrationTask;
import org.apache.james.backends.cassandra.migration.MigrationTaskAdditionalInformationDTO;
import org.apache.james.backends.cassandra.migration.MigrationTaskDTO;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTaskAdditionalInformationDTO;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTaskDTO;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTaskRunner;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersTaskAdditionalInformationDTO;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersTaskDTO;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxInconsistenciesService;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxInconsistenciesTaskAdditionalInformationDTO;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxInconsistenciesTaskDTO;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesTaskAdditionalInformationDTO;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesTaskDTO;
import org.apache.james.rrt.cassandra.CassandraMappingsSourcesDAO;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigration;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigrationTaskAdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.webadmin.service.CassandraMappingsSolveInconsistenciesTask;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;

public class CassandraConsistencyTaskSerializationModule extends AbstractModule {
    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> cassandraMappingsSolveInconsistenciesTask(MappingsSourcesMigration migration, CassandraMappingsSourcesDAO dao) {
        return CassandraMappingsSolveInconsistenciesTask.module(migration, dao);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> mailboxMergingTask(MailboxMergingTaskRunner taskRunner) {
        return MailboxMergingTaskDTO.module(taskRunner);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> solveMailboxInconsistenciesTask(SolveMailboxInconsistenciesService service) {
        return SolveMailboxInconsistenciesTaskDTO.module(service);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> recomputeMailboxCountersTask(RecomputeMailboxCountersService service) {
        return RecomputeMailboxCountersTaskDTO.module(service);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> migrationTask(MigrationTask.Factory factory) {
        return MigrationTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> solveMessageInconsistenciesTask(SolveMessageInconsistenciesService solveMessageInconsistenciesService) {
        return SolveMessageInconsistenciesTaskDTO.module(solveMessageInconsistenciesService);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> cassandraMappingsSolveInconsistenciesAdditionalInformation() {
        return MappingsSourcesMigrationTaskAdditionalInformationDTO.serializationModule(CassandraMappingsSolveInconsistenciesTask.TYPE);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> mailboxMergingAdditionalInformation() {
        return MailboxMergingTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> solveMailboxInconsistenciesAdditionalInformation() {
        return SolveMailboxInconsistenciesTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> recomputeMailboxCountersAdditionalInformation() {
        return RecomputeMailboxCountersTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> migrationTaskAdditionalInformation() {
        return MigrationTaskAdditionalInformationDTO.serializationModule();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> solveMessageInconsistenciesAdditionalInformation() {
        return SolveMessageInconsistenciesTaskAdditionalInformationDTO.MODULE;
    }
}
