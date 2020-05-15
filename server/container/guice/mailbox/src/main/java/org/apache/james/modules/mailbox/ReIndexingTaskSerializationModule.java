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
package org.apache.james.modules.mailbox;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.mailbox.tools.indexer.ErrorRecoveryIndexationTask;
import org.apache.mailbox.tools.indexer.ErrorRecoveryIndexationTaskDTO;
import org.apache.mailbox.tools.indexer.FullReindexingTaskDTO;
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

public class ReIndexingTaskSerializationModule extends AbstractModule {
    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> fullReindexTask(ReIndexerPerformer performer) {
        return FullReindexingTaskDTO.module(performer);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> errorRecoveryIndexationTask(ErrorRecoveryIndexationTask.Factory factory) {
        return ErrorRecoveryIndexationTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> messageIdReindexingTask(MessageIdReIndexingTask.Factory factory) {
        return MessageIdReindexingTaskDTO.module(factory);
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
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> errorRecoveryAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return ReprocessingContextInformationDTO.ReprocessingContextInformationForErrorRecoveryIndexationTask.serializationModule(mailboxIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> fullReindexAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return ReprocessingContextInformationDTO.ReprocessingContextInformationForFullReindexingTask.serializationModule(mailboxIdFactory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> messageIdReindexingAdditionalInformation(MessageId.Factory messageIdFactory) {
        return MessageIdReindexingTaskAdditionalInformationDTO.serializationModule(messageIdFactory);
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
}
