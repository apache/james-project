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

package org.apache.mailbox.tools.indexer;

import java.util.Optional;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SingleMessageReindexingTask implements Task {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleMessageReindexingTask.class);

    public static final String MESSAGE_RE_INDEXING = "messageReIndexing";

    public static final Function<SingleMessageReindexingTask.Factory, TaskDTOModule<SingleMessageReindexingTask, SingleMessageReindexingTask.SingleMessageReindexingTaskDTO>> MODULE = (factory) ->
        DTOModule
            .forDomainObject(SingleMessageReindexingTask.class)
            .convertToDTO(SingleMessageReindexingTask.SingleMessageReindexingTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter(SingleMessageReindexingTask.SingleMessageReindexingTaskDTO::of)
            .typeName(MESSAGE_RE_INDEXING)
            .withFactory(TaskDTOModule::new);


    public static class SingleMessageReindexingTaskDTO implements TaskDTO {

        private final String type;
        private final String mailboxId;
        private final long uid;

        private SingleMessageReindexingTaskDTO(@JsonProperty("type") String type, @JsonProperty("mailboxId") String mailboxId, @JsonProperty("uid") long uid) {
            this.type = type;
            this.mailboxId = mailboxId;
            this.uid = uid;
        }

        @Override
        public String getType() {
            return type;
        }

        public String getMailboxId() {
            return mailboxId;
        }

        public long getUid() {
            return uid;
        }

        public static SingleMessageReindexingTaskDTO of(SingleMessageReindexingTask task, String type) {
            return new SingleMessageReindexingTaskDTO(type, task.mailboxId.serialize(), task.uid.asLong());
        }
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final MailboxId mailboxId;
        private final MessageUid uid;

        AdditionalInformation(MailboxId mailboxId, MessageUid uid) {
            this.mailboxId = mailboxId;
            this.uid = uid;
        }

        public String getMailboxId() {
            return mailboxId.serialize();
        }

        public long getUid() {
            return uid.asLong();
        }
    }

    public static class Factory {

        private final ReIndexerPerformer reIndexerPerformer;
        private final MailboxId.Factory mailboxIdFactory;

        @Inject
        public Factory(ReIndexerPerformer reIndexerPerformer, MailboxId.Factory mailboxIdFactory) {
            this.reIndexerPerformer = reIndexerPerformer;
            this.mailboxIdFactory = mailboxIdFactory;
        }

        public SingleMessageReindexingTask create(SingleMessageReindexingTaskDTO dto) {
            MailboxId mailboxId = mailboxIdFactory.fromString(dto.getMailboxId());
            MessageUid uid = MessageUid.of(dto.getUid());
            return new SingleMessageReindexingTask(reIndexerPerformer, mailboxId, uid);
        }
    }

    private final ReIndexerPerformer reIndexerPerformer;
    private final MailboxId mailboxId;
    private final MessageUid uid;
    private final AdditionalInformation additionalInformation;

    @Inject
    SingleMessageReindexingTask(ReIndexerPerformer reIndexerPerformer, MailboxId mailboxId, MessageUid uid) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.mailboxId = mailboxId;
        this.uid = uid;
        this.additionalInformation = new AdditionalInformation(mailboxId, uid);
    }

    @Override
    public Result run() {
        try {
            return reIndexerPerformer.handleMessageReIndexing(mailboxId, uid, new ReprocessingContext());
        } catch (MailboxException e) {
            LOGGER.warn("Error encounteres while reindexing {} : {}", mailboxId, uid, e);
            return Result.PARTIAL;
        }
    }

    @Override
    public String type() {
        return MESSAGE_RE_INDEXING;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }

}
