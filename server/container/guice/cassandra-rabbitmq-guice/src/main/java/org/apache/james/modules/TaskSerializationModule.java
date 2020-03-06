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
import java.util.Set;

import javax.inject.Named;

import org.apache.james.backends.cassandra.migration.MigrationTask;
import org.apache.james.backends.cassandra.migration.MigrationTaskAdditionalInformationDTO;
import org.apache.james.backends.cassandra.migration.MigrationTaskDTO;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.EventNestedTypes;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.json.DTOConverter;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTaskAdditionalInformationDTO;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTaskDTO;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTaskRunner;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxInconsistenciesService;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxInconsistenciesTaskAdditionalInformationDTO;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxInconsistenciesTaskDTO;
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
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.eventsourcing.distributed.TasksSerializationModule;
import org.apache.james.vault.blob.BlobStoreVaultGarbageCollectionTask;
import org.apache.james.vault.blob.BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO;
import org.apache.james.vault.blob.BlobStoreVaultGarbageCollectionTaskDTO;
import org.apache.james.webadmin.data.jmap.MessageFastViewProjectionCorrector;
import org.apache.james.webadmin.data.jmap.RecomputeAllFastViewProjectionItemsTask;
import org.apache.james.webadmin.data.jmap.RecomputeAllFastViewTaskAdditionalInformationDTO;
import org.apache.james.webadmin.data.jmap.RecomputeUserFastViewProjectionItemsTask;
import org.apache.james.webadmin.data.jmap.RecomputeUserFastViewTaskAdditionalInformationDTO;
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
import org.apache.james.webadmin.service.ExportService;
import org.apache.james.webadmin.service.MailboxesExportTask;
import org.apache.james.webadmin.service.MailboxesExportTaskAdditionalInformationDTO;
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

import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;

public class TaskSerializationModule extends AbstractModule {

    @Provides
    @Singleton
    public DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationDTOConverter(Set<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>> modules) {
        return new DTOConverter<>(modules);
    }

    @Provides
    @Singleton
    public DTOConverter<Task, TaskDTO> taskDTOConverter(Set<TaskDTOModule<? extends Task, ? extends TaskDTO>> taskDTOModules) {
        return new DTOConverter<>(taskDTOModules);

    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskCreatedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                                                        DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                                                        DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.CREATED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskStartedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                         DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                         DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.STARTED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskCancelRequestedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                                 DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                                 DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.CANCEL_REQUESTED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskCancelledSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                           DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                           DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.CANCELLED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskCompletedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                           DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                           DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.COMPLETED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskFailedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                        DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                        DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.FAILED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskUpdatedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                        DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                        DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.UPDATED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> blobStoreVaultGarbageCollectionTask(BlobStoreVaultGarbageCollectionTask.Factory factory) {
        return BlobStoreVaultGarbageCollectionTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> cassandraMappingsSolveInconsistenciesTask(MappingsSourcesMigration migration, CassandraMappingsSourcesDAO dao) {
        return CassandraMappingsSolveInconsistenciesTask.module(migration, dao);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> clearMailQueueTask(MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory) {
        return ClearMailQueueTaskDTO.module(mailQueueFactory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> recomputeAllJmapPreviewsTask(MessageFastViewProjectionCorrector corrector) {
        return RecomputeAllFastViewProjectionItemsTask.module(corrector);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> recomputeUserJmapPreviewsTask(MessageFastViewProjectionCorrector corrector) {
        return RecomputeUserFastViewProjectionItemsTask.module(corrector);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> mailboxesExportTask(ExportService exportService) {
        return MailboxesExportTask.module(exportService);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> clearMailRepositoryTask(ClearMailRepositoryTask.Factory factory) {
        return ClearMailRepositoryTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> deleteMailsFromMailQueueTask(MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory) {
        return DeleteMailsFromMailQueueTaskDTO.module(mailQueueFactory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> deletedMessagesVaultDeleteTask(DeletedMessagesVaultDeleteTask.Factory factory) {
        return DeletedMessagesVaultDeleteTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> deletedMessagesVaultExportTask(DeletedMessagesVaultExportTaskDTO.Factory factory) {
        return DeletedMessagesVaultExportTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> deletedMessagesVaultRestoreTask(DeletedMessagesVaultRestoreTaskDTO.Factory factory) {
        return DeletedMessagesVaultRestoreTaskDTO.module(factory);
    }

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
    public TaskDTOModule<? extends Task, ? extends TaskDTO> fullReindexTask(ReIndexerPerformer performer) {
        return FullReindexingTask.module(performer);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> errorRecoveryIndexationTask(ErrorRecoveryIndexationTask.Factory factory) {
        return ErrorRecoveryIndexationTaskDTO.module(factory);
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
    public TaskDTOModule<? extends Task, ? extends TaskDTO> messageIdReindexingTask(MessageIdReIndexingTask.Factory factory) {
        return MessageIdReindexingTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> migrationTask(MigrationTask.Factory factory) {
        return MigrationTaskDTO.module(factory);
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
    public TaskDTOModule<? extends Task, ? extends TaskDTO> singleMailboxReindexingTask(SingleMailboxReindexingTask.Factory factory) {
        return SingleMailboxReindexingTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> singleMessageReindexingTask(SingleMessageReindexingTask.Factory factory) {
        return SingleMessageReindexingTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> userReindexingTask(UserReindexingTask.Factory factory) {
        return UserReindexingTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> blobStoreVaultGarbageCollectionAdditionalInformation() {
        return BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> cassandraMappingsSolveInconsistenciesAdditionalInformation() {
        return MappingsSourcesMigrationTaskAdditionalInformationDTO.serializationModule(CassandraMappingsSolveInconsistenciesTask.TYPE);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> clearMailQueueAdditionalInformation() {
        return ClearMailQueueTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> clearMailRepositoryAdditionalInformation() {
        return ClearMailRepositoryTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> deleteMailsFromMailQueueAdditionalInformation() {
        return DeleteMailsFromMailQueueTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> deletedMessagesVaultDeleteAdditionalInformation(MessageId.Factory factory) {
        return DeletedMessagesVaultDeleteTaskAdditionalInformationDTO.serializationModule(factory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> deletedMessagesVaultExportAdditionalInformation() {
        return DeletedMessagesVaultExportTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> deletedMessagesVaultRestoreAdditionalInformation() {
        return DeletedMessagesVaultRestoreTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> errorRecoveryAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return ReprocessingContextInformationDTO.ReprocessingContextInformationForErrorRecoveryIndexationTask.serializationModule(mailboxIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> eventDeadLettersRedeliveryAdditionalInformationForAll() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForAll.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> eventDeadLettersRedeliveryAdditionalInformationForGroup() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> eventDeadLettersRedeliveryAdditionalInformationForOne() {
        return EventDeadLettersRedeliveryTaskAdditionalInformationDTO.EventDeadLettersRedeliveryTaskAdditionalInformationForOne.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> fullReindexAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return ReprocessingContextInformationDTO.ReprocessingContextInformationForFullReindexingTask.serializationModule(mailboxIdFactory);
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
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> messageIdReindexingAdditionalInformation(MessageId.Factory messageIdFactory) {
        return MessageIdReindexingTaskAdditionalInformationDTO.serializationModule(messageIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> migrationTaskAdditionalInformation() {
        return MigrationTaskAdditionalInformationDTO.serializationModule();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> reprocessingAllMailsAdditionalInformation() {
        return ReprocessingAllMailsTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> reprocessingOneMailAdditionalInformation() {
        return ReprocessingOneMailTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> singleMailboxReindexingAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return SingleMailboxReindexingTaskAdditionalInformationDTO.serializationModule(mailboxIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> singleMessageReindexingAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return SingleMessageReindexingTaskAdditionalInformationDTO.serializationModule(mailboxIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> userReindexingAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return UserReindexingTaskAdditionalInformationDTO.serializationModule(mailboxIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> recomputeAllJmapPreviewsAdditionalInformation() {
        return RecomputeAllFastViewTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> recomputeUserJmapPreviewsAdditionalInformation() {
        return RecomputeUserFastViewTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> mailboxesExportAdditionalInformation() {
        return MailboxesExportTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @Named(EventNestedTypes.EVENT_NESTED_TYPES_INJECTION_NAME)
    @Provides
    public Set<DTOModule<?, ? extends org.apache.james.json.DTO>> eventNestedTypes(Set<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO>> additionalInformationDTOModules,
                                                                                   Set<TaskDTOModule<? extends Task, ? extends TaskDTO>> taskDTOModules) {
        return Sets.union(additionalInformationDTOModules, taskDTOModules);
    }
}
