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
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.rrt.cassandra.CassandraMappingsSourcesDAO;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigration;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigrationTaskAdditionalInformationDTO;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationsSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.eventsourcing.distributed.TasksSerializationModule;
import org.apache.james.webadmin.service.CassandraMappingsSolveInconsistenciesTask;
import org.apache.james.webadmin.service.DeleteMailsFromMailQueueTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.DeleteMailsFromMailQueueTaskDTO;
import org.apache.james.webadmin.service.ReprocessingAllMailsTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ReprocessingAllMailsTaskDTO;
import org.apache.james.webadmin.service.ReprocessingOneMailTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ReprocessingOneMailTaskDTO;
import org.apache.james.webadmin.service.ReprocessingService;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTaskAdditionalInformationDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTaskDTO;
import org.apache.mailbox.tools.indexer.FullReindexingTask;
import org.apache.mailbox.tools.indexer.MessageIdReIndexingTask;
import org.apache.mailbox.tools.indexer.MessageIdReindexingTaskAdditionalInformationDTO;
import org.apache.mailbox.tools.indexer.MessageIdReindexingTaskDTO;
import org.apache.mailbox.tools.indexer.ReIndexerPerformer;
import org.apache.mailbox.tools.indexer.ReprocessingContextInformationDTO;
import org.apache.mailbox.tools.indexer.SingleMessageReindexingTask;
import org.apache.mailbox.tools.indexer.SingleMessageReindexingTaskAdditionalInformationDTO;
import org.apache.mailbox.tools.indexer.SingleMessageReindexingTaskDTO;
import org.apache.mailbox.tools.indexer.UserReindexingTask;
import org.apache.mailbox.tools.indexer.UserReindexingTaskAdditionalInformationDTO;
import org.apache.mailbox.tools.indexer.UserReindexingTaskDTO;

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
    public TaskDTOModule<?, ?> deleteMailsFromMailQueueTask(MailQueueFactory<?> mailQueueFactory) {
        return DeleteMailsFromMailQueueTaskDTO.module((MailQueueFactory<ManageableMailQueue>) mailQueueFactory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> deletedMessagesVaultRestoreTask(DeletedMessagesVaultRestoreTaskDTO.Factory factory) {
        return DeletedMessagesVaultRestoreTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> fullReindexTask(ReIndexerPerformer performer) {
        return FullReindexingTask.module(performer);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> messageIdReindexingTask(MessageIdReIndexingTask.Factory factory) {
        return MessageIdReindexingTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> migrationTask(MigrationTask.Factory factory) {
        return MigrationTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> reprocessingAllMailsTask(ReprocessingService reprocessingService) {
        return ReprocessingAllMailsTaskDTO.module(reprocessingService);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> reprocessingOneMailsTask(ReprocessingService reprocessingService) {
        return ReprocessingOneMailTaskDTO.module(reprocessingService);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> singleMessageReindexingTask(SingleMessageReindexingTask.Factory factory) {
        return SingleMessageReindexingTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> userReindexingTask(UserReindexingTask.Factory factory) {
        return UserReindexingTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> cassandraMappingsSolveInconsistenciesAdditionalInformation() {
        return MappingsSourcesMigrationTaskAdditionalInformationDTO.serializationModule(CassandraMappingsSolveInconsistenciesTask.TYPE);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> deleteMailsFromMailQueueAdditionalInformation() {
        return DeleteMailsFromMailQueueTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> deletedMessagesVaultAdditionalInformation() {
        return DeletedMessagesVaultRestoreTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> fullReindexAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return ReprocessingContextInformationDTO.serializationModule(FullReindexingTask.FULL_RE_INDEXING, mailboxIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> messageIdReindexingAdditionalInformation(MessageId.Factory messageIdFactory) {
        return MessageIdReindexingTaskAdditionalInformationDTO.serializationModule(messageIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> migrationTaskAdditionalInformation() {
        return MigrationTaskAdditionalInformationsDTO.serializationModule();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> reprocessingAllMailsAdditionalInformation() {
        return ReprocessingAllMailsTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> reprocessingOneMailAdditionalInformation() {
        return ReprocessingOneMailTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> singleMessageReindexingAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return SingleMessageReindexingTaskAdditionalInformationDTO.serializationModule(mailboxIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> userReindexingAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return UserReindexingTaskAdditionalInformationDTO.serializationModule(mailboxIdFactory);
    }
}
