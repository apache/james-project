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

import static org.apache.mailbox.tools.indexer.FullReindexingTask.FULL_RE_INDEXING;

import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FullReindexingTaskDTO implements TaskDTO {
    private static FullReindexingTaskDTO toDTO(FullReindexingTask task, String type) {
        return new FullReindexingTaskDTO(type, Optional.of(RunningOptionsDTO.toDTO(task.getRunningOptions())));
    }

    public static TaskDTOModule<FullReindexingTask, FullReindexingTaskDTO> module(ReIndexerPerformer reIndexerPerformer) {
        return DTOModule
            .forDomainObject(FullReindexingTask.class)
            .convertToDTO(FullReindexingTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDomainObject(reIndexerPerformer))
            .toDTOConverter(FullReindexingTaskDTO::toDTO)
            .typeName(FULL_RE_INDEXING.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final Optional<RunningOptionsDTO> runningOptions;

    public FullReindexingTaskDTO(@JsonProperty("type") String type,
                                 @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) {
        this.type = type;
        this.runningOptions = runningOptions;
    }

    @Override
    public String getType() {
        return type;
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return runningOptions;
    }

    private FullReindexingTask toDomainObject(ReIndexerPerformer reIndexerPerformer) {
        return new FullReindexingTask(reIndexerPerformer,
            runningOptions
                .map(RunningOptionsDTO::toDomainObject)
                .orElse(RunningOptions.DEFAULT));
    }
}
