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

import java.time.Clock;

import org.apache.james.backends.cassandra.migration.MigrationTask;
import org.apache.james.backends.cassandra.migration.MigrationTaskAdditionalInformationDTO;
import org.apache.james.backends.cassandra.migration.MigrationTaskDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTaskAdditionalInformationDTO;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTaskDTO;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTaskRunner;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.rrt.cassandra.CassandraMappingsSourcesDAO;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigration;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigrationTaskAdditionalInformationDTO;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.eventsourcing.distributed.TasksSerializationModule;
import org.apache.james.vault.blob.BlobStoreVaultGarbageCollectionTask;
import org.apache.james.vault.blob.BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO;
import org.apache.james.vault.blob.BlobStoreVaultGarbageCollectionTaskDTO;
import org.apache.james.webadmin.service.CassandraMappingsSolveInconsistenciesTask;
import org.apache.james.webadmin.service.ClearMailQueueTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ClearMailQueueTaskDTO;
import org.apache.james.webadmin.service.ClearMailRepositoryTask;
import org.apache.james.webadmin.service.ClearMailRepositoryTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ClearMailRepositoryTaskDTO;
import org.apache.james.webadmin.service.DeleteMailsFromMailQueueTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.DeleteMailsFromMailQueueTaskDTO;
import org.apache.james.webadmin.service.EventDeadLettersRedeliverAllTaskDTO;
import org.apache.james.webadmin.service.EventDeadLettersRedeliverGroupTaskDTO;
import org.apache.james.webadmin.service.EventDeadLettersRedeliverOneTaskDTO;
import org.apache.james.webadmin.service.EventDeadLettersRedeliverService;
import org.apache.james.webadmin.service.EventDeadLettersRedeliveryTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ReprocessingAllMailsTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ReprocessingAllMailsTaskDTO;
import org.apache.james.webadmin.service.ReprocessingOneMailTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.ReprocessingOneMailTaskDTO;
import org.apache.james.webadmin.service.ReprocessingService;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultDeleteTask;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultDeleteTaskAdditionalInformationDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultDeleteTaskDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultExportTaskAdditionalInformationDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultExportTaskDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTaskAdditionalInformationDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTaskDTO;
import org.apache.mailbox.tools.indexer.ErrorRecoveryIndexationTask;
import org.apache.mailbox.tools.indexer.ErrorRecoveryIndexationTaskDTO;
import org.apache.mailbox.tools.indexer.FullReindexingTask;
import org.apache.mailbox.tools.indexer.MessageIdReIndexingTask;
import org.apache.mailbox.tools.indexer.MessageIdReindexingTaskAdditionalInformationDTO;
import org.apache.mailbox.tools.indexer.MessageIdReindexingTaskDTO;
import org.apache.mailbox.tools.indexer.ReIndexerPerformer;
import org.apache.mailbox.tools.indexer.ReprocessingContextInformationDTO;
import org.apache.mailbox.tools.indexer.SingleMailboxReindexingTask;
import org.apache.mailbox.tools.indexer.SingleMailboxReindexingTaskAdditionalInformationDTO;
import org.apache.mailbox.tools.indexer.SingleMailboxReindexingTaskDTO;
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
    public EventDTOModule<?, ?> taskCreatedSerialization(JsonTaskSerializer jsonTaskSerializer, DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter) {
        return TasksSerializationModule.CREATED.create(jsonTaskSerializer, additionalInformationConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<?, ?> taskStartedSerialization(JsonTaskSerializer jsonTaskSerializer, DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter) {
        return TasksSerializationModule.STARTED.create(jsonTaskSerializer, additionalInformationConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<?, ?> taskCancelRequestedSerialization(JsonTaskSerializer jsonTaskSerializer, DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter) {
        return TasksSerializationModule.CANCEL_REQUESTED.create(jsonTaskSerializer, additionalInformationConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<?, ?> taskCancelledSerialization(JsonTaskSerializer jsonTaskSerializer, DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter) {
        return TasksSerializationModule.CANCELLED.create(jsonTaskSerializer, additionalInformationConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<?, ?> taskCompletedSerialization(JsonTaskSerializer jsonTaskSerializer, DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter) {
        return TasksSerializationModule.COMPLETED.create(jsonTaskSerializer, additionalInformationConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<?, ?> taskFailedSerialization(JsonTaskSerializer jsonTaskSerializer, DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter) {
        return TasksSerializationModule.FAILED.create(jsonTaskSerializer, additionalInformationConverter);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> blobStoreVaultGarbageCollectionTask(BlobStoreVaultGarbageCollectionTask.Factory factory) {
        return BlobStoreVaultGarbageCollectionTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> cassandraMappingsSolveInconsistenciesTask(MappingsSourcesMigration migration, CassandraMappingsSourcesDAO dao) {
        return CassandraMappingsSolveInconsistenciesTask.module(migration, dao);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> clearMailQueueTask(MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory) {
        return ClearMailQueueTaskDTO.module(mailQueueFactory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> clearMailRepositoryTask(ClearMailRepositoryTask.Factory factory) {
        return ClearMailRepositoryTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> deleteMailsFromMailQueueTask(MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory) {
        return DeleteMailsFromMailQueueTaskDTO.module(mailQueueFactory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> deletedMessagesVaultDeleteTask(DeletedMessagesVaultDeleteTask.Factory factory) {
        return DeletedMessagesVaultDeleteTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> deletedMessagesVaultExportTask(DeletedMessagesVaultExportTaskDTO.Factory factory) {
        return DeletedMessagesVaultExportTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> deletedMessagesVaultRestoreTask(DeletedMessagesVaultRestoreTaskDTO.Factory factory) {
        return DeletedMessagesVaultRestoreTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> eventDeadLettersRedeliverAllTask(EventDeadLettersRedeliverService service) {
        return EventDeadLettersRedeliverAllTaskDTO.module(service);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> eventDeadLettersRedeliverGroupTask(EventDeadLettersRedeliverService service) {
        return EventDeadLettersRedeliverGroupTaskDTO.module(service);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> eventDeadLettersRedeliverOneTask(EventDeadLettersRedeliverService service) {
        return EventDeadLettersRedeliverOneTaskDTO.module(service);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> fullReindexTask(ReIndexerPerformer performer) {
        return FullReindexingTask.module(performer);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> errorRecoveryIndexationTask(ErrorRecoveryIndexationTask.Factory factory) {
        return ErrorRecoveryIndexationTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> mailboxMergingTask(MailboxMergingTaskRunner taskRunner) {
        return MailboxMergingTaskDTO.module(taskRunner);
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
        return ReprocessingOneMailTaskDTO.module(Clock.systemUTC(), reprocessingService);
    }

    @ProvidesIntoSet
    public TaskDTOModule<?, ?> singleMailboxReindexingTask(SingleMailboxReindexingTask.Factory factory) {
        return SingleMailboxReindexingTaskDTO.module(factory);
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
    public AdditionalInformationDTOModule<?, ?> blobStoreVaultGarbageCollectionAdditionalInformation() {
        return BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> cassandraMappingsSolveInconsistenciesAdditionalInformation() {
        return MappingsSourcesMigrationTaskAdditionalInformationDTO.serializationModule(CassandraMappingsSolveInconsistenciesTask.TYPE);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> clearMailQueueAdditionalInformation() {
        return ClearMailQueueTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> clearMailRepositoryAdditionalInformation() {
        return ClearMailRepositoryTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> deleteMailsFromMailQueueAdditionalInformation() {
        return DeleteMailsFromMailQueueTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> deletedMessagesVaultDeleteAdditionalInformation(MessageId.Factory factory) {
        return DeletedMessagesVaultDeleteTaskAdditionalInformationDTO.serializationModule(factory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> deletedMessagesVaultExportAdditionalInformation() {
        return DeletedMessagesVaultExportTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> deletedMessagesVaultRestoreAdditionalInformation() {
        return DeletedMessagesVaultRestoreTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> errorRecoveryAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return ReprocessingContextInformationDTO.ReprocessingContextInformationForErrorRecoveryIndexationTask.serializationModule(mailboxIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> eventDeadLettersRedeliveryAdditionalInformationForAll() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForAll.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> eventDeadLettersRedeliveryAdditionalInformationForGroup() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> eventDeadLettersRedeliveryAdditionalInformationForOne() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForOne.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> fullReindexAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return ReprocessingContextInformationDTO.ReprocessingContextInformationForFullReindexingTask.serializationModule(mailboxIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> mailboxMergingAdditionalInformation() {
        return MailboxMergingTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> messageIdReindexingAdditionalInformation(MessageId.Factory messageIdFactory) {
        return MessageIdReindexingTaskAdditionalInformationDTO.serializationModule(messageIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<?, ?> migrationTaskAdditionalInformation() {
        return MigrationTaskAdditionalInformationDTO.serializationModule();
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
    public AdditionalInformationDTOModule<?, ?> singleMailboxReindexingAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return SingleMailboxReindexingTaskAdditionalInformationDTO.serializationModule(mailboxIdFactory);
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
