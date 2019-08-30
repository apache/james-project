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

import org.apache.james.core.User;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserReindexingTask implements Task {

    public static final TaskType USER_RE_INDEXING = TaskType.of("userReIndexing");

    public static final Function<UserReindexingTask.Factory, TaskDTOModule<UserReindexingTask, UserReindexingTask.UserReindexingTaskDTO>> MODULE = (factory) ->
        DTOModule
            .forDomainObject(UserReindexingTask.class)
            .convertToDTO(UserReindexingTask.UserReindexingTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter(UserReindexingTask.UserReindexingTaskDTO::of)
            .typeName(USER_RE_INDEXING.asString())
            .withFactory(TaskDTOModule::new);

    public static class UserReindexingTaskDTO implements TaskDTO {

        public static UserReindexingTaskDTO of(UserReindexingTask task, String type) {
            return new UserReindexingTaskDTO(type, task.user.asString());
        }

        private final String type;
        private final String username;

        private UserReindexingTaskDTO(@JsonProperty("type") String type, @JsonProperty("username") String username) {
            this.type = type;
            this.username = username;
        }

        @Override
        public String getType() {
            return type;
        }

        public String getUsername() {
            return username;
        }

    }

    public static class AdditionalInformation extends ReprocessingContextInformation {
        private final User user;

        AdditionalInformation(ReprocessingContext reprocessingContext, User user) {
            super(reprocessingContext);
            this.user = user;
        }

        public String getUser() {
            return user.asString();
        }
    }

    private final ReIndexerPerformer reIndexerPerformer;
    private final User user;
    private final AdditionalInformation additionalInformation;
    private final ReprocessingContext reprocessingContext;

    @Inject
    public UserReindexingTask(ReIndexerPerformer reIndexerPerformer, User user) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.user = user;
        this.reprocessingContext = new ReprocessingContext();
        this.additionalInformation = new AdditionalInformation(reprocessingContext, user);
    }

    public static class Factory {

        private final ReIndexerPerformer reIndexerPerformer;

        @Inject
        public Factory(ReIndexerPerformer reIndexerPerformer) {
            this.reIndexerPerformer = reIndexerPerformer;
        }

        public UserReindexingTask create(UserReindexingTaskDTO dto) {
            User user = User.fromUsername(dto.getUsername());
            return new UserReindexingTask(reIndexerPerformer, user);
        }
    }

    @Override
    public Result run() {
        try {
            return reIndexerPerformer.reIndex(user, reprocessingContext);
        } catch (MailboxException e) {
            return Result.PARTIAL;
        }
    }

    @Override
    public TaskType type() {
        return USER_RE_INDEXING;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }
}
