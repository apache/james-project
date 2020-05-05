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

import javax.inject.Inject;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;

import reactor.core.publisher.Mono;

public class FullReindexingTask implements Task {

    public static final TaskType FULL_RE_INDEXING = TaskType.of("full-reindexing");

    private final ReIndexerPerformer reIndexerPerformer;
    private final ReprocessingContext reprocessingContext;

    public static TaskDTOModule<FullReindexingTask, FullReindexingTaskDTO> module(ReIndexerPerformer reIndexerPerformer) {
        return DTOModule
            .forDomainObject(FullReindexingTask.class)
            .convertToDTO(FullReindexingTask.FullReindexingTaskDTO.class)
            .toDomainObjectConverter(dto -> new FullReindexingTask(reIndexerPerformer))
            .toDTOConverter((task, type) -> new FullReindexingTaskDTO(type))
            .typeName(FULL_RE_INDEXING.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static class FullReindexingTaskDTO implements TaskDTO {

        private final String type;

        public FullReindexingTaskDTO(@JsonProperty("type") String type) {
            this.type = type;
        }

        @Override
        public String getType() {
            return type;
        }

    }

    @Inject
    public FullReindexingTask(ReIndexerPerformer reIndexerPerformer) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.reprocessingContext = new ReprocessingContext();
    }

    @Override
    public Result run() {
        return reIndexerPerformer.reIndex(reprocessingContext)
            .onErrorResume(e -> Mono.just(Result.PARTIAL))
            .block();
    }

    @Override
    public TaskType type() {
        return FULL_RE_INDEXING;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(ReprocessingContextInformation.forFullReindexingTask(reprocessingContext));
    }
}
