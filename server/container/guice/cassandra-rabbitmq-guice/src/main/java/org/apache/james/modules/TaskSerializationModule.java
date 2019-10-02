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
import org.apache.james.backends.cassandra.migration.MigrationTaskAdditionalInformationsDTO;
import org.apache.james.backends.cassandra.migration.MigrationTaskDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.rrt.cassandra.CassandraMappingsSourcesDAO;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigration;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigrationTaskAdditionalInformationDTO;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationsSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.eventsourcing.distributed.TasksSerializationModule;
import org.apache.james.webadmin.service.CassandraMappingsSolveInconsistenciesTask;
import org.apache.mailbox.tools.indexer.FullReindexingTask;
import org.apache.mailbox.tools.indexer.ReIndexerPerformer;
import org.apache.mailbox.tools.indexer.ReprocessingContextInformationDTO;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;

public class TaskSerializationModule extends AbstractModule {

    @ProvidesIntoSet
    public EventDTOModule<?, ?> taskCreatedSerialization(JsonTaskSerializer jsonTaskSerializer, JsonTaskAdditionalInformationsSerializer jsonTaskAdditionalInformationsSerializer) {
        return TasksSerializationModule.CREATED.create(jsonTaskSerializer, jsonTaskAdditionalInformationsSerializer);
    }

    @ProvidesIntoSet
    public EventDTOModule<?, ?> taskStartedSerialization(JsonTaskSerializer jsonTaskSerializer, JsonTaskAdditionalInformationsSerializer jsonTaskAdditionalInformationsSerializer) {
        return TasksSerializationModule.STARTED.create(jsonTaskSerializer, jsonTaskAdditionalInformationsSerializer);
    }

    @ProvidesIntoSet
    public EventDTOModule<?, ?> taskCancelRequestedSerialization(JsonTaskSerializer jsonTaskSerializer, JsonTaskAdditionalInformationsSerializer jsonTaskAdditionalInformationsSerializer) {
        return TasksSerializationModule.CANCEL_REQUESTED.create(jsonTaskSerializer, jsonTaskAdditionalInformationsSerializer);
    }

    @ProvidesIntoSet
    public EventDTOModule<?, ?> taskCancelledSerialization(JsonTaskSerializer jsonTaskSerializer, JsonTaskAdditionalInformationsSerializer jsonTaskAdditionalInformationsSerializer) {
        return TasksSerializationModule.CANCELLED.create(jsonTaskSerializer, jsonTaskAdditionalInformationsSerializer);
    }

    @ProvidesIntoSet
    public EventDTOModule<?, ?> taskCompletedSerialization(JsonTaskSerializer jsonTaskSerializer, JsonTaskAdditionalInformationsSerializer jsonTaskAdditionalInformationsSerializer) {
        return TasksSerializationModule.COMPLETED.create(jsonTaskSerializer, jsonTaskAdditionalInformationsSerializer);
    }

    @ProvidesIntoSet
    public EventDTOModule<?, ?> taskFailedSerialization(JsonTaskSerializer jsonTaskSerializer, JsonTaskAdditionalInformationsSerializer jsonTaskAdditionalInformationsSerializer) {
        return TasksSerializationModule.FAILED.create(jsonTaskSerializer, jsonTaskAdditionalInformationsSerializer);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> cassandraMappingsSolveInconsistenciesTask(MappingsSourcesMigration migration, CassandraMappingsSourcesDAO dao) {
        return CassandraMappingsSolveInconsistenciesTask.module(migration, dao);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> fullReindexTask(ReIndexerPerformer performer) {
        return FullReindexingTask.module(performer);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> migrationTask(MigrationTask.Factory factory) {
        return MigrationTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> cassandraMappingsSolveInconsistenciesAdditionalInformation() {
        return MappingsSourcesMigrationTaskAdditionalInformationDTO.serializationModule(CassandraMappingsSolveInconsistenciesTask.TYPE);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> fullReindexAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return ReprocessingContextInformationDTO.serializationModule(FullReindexingTask.FULL_RE_INDEXING, mailboxIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> migrationTaskAdditionalInformation() {
        return MigrationTaskAdditionalInformationsDTO.serializationModule();
    }
}
