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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FullReindexingTask implements Task {

    public static final String FULL_RE_INDEXING = "FullReIndexing";

    private final ReIndexerPerformer reIndexerPerformer;
    private final ReprocessingContextInformation additionalInformation;
    private final ReprocessingContext reprocessingContext;

    public static final Function<FullReindexingTask.Factory, TaskDTOModule<FullReindexingTask, FullReindexingTaskDTO>> MODULE = (factory) ->
        DTOModule
            .forDomainObject(FullReindexingTask.class)
            .convertToDTO(FullReindexingTask.FullReindexingTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter((task, type) -> new FullReindexingTaskDTO(type))
            .typeName(FULL_RE_INDEXING)
            .withFactory(TaskDTOModule::new);

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

    public static class Factory {

        private final ReIndexerPerformer reIndexerPerformer;

        @Inject
        public Factory(ReIndexerPerformer reIndexerPerformer) {
            this.reIndexerPerformer = reIndexerPerformer;
        }

        public FullReindexingTask create(FullReindexingTaskDTO dto) {
            return new FullReindexingTask(reIndexerPerformer);
        }
    }

    @Inject
    public FullReindexingTask(ReIndexerPerformer reIndexerPerformer) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.reprocessingContext = new ReprocessingContext();
        this.additionalInformation = new ReprocessingContextInformation(reprocessingContext);
    }

    @Override
    public Result run() {
        try {
            return reIndexerPerformer.reIndex(reprocessingContext);
        } catch (MailboxException e) {
            return Result.PARTIAL;
        }
    }

    @Override
    public String type() {
        return FULL_RE_INDEXING;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }
}
