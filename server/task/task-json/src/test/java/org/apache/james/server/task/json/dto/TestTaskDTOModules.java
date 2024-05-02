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

package org.apache.james.server.task.json.dto;

import java.util.function.Function;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.TestTask;
import org.apache.james.task.CompletedTask;
import org.apache.james.task.FailedTask;
import org.apache.james.task.FailsDeserializationTask;
import org.apache.james.task.MemoryReferenceTask;
import org.apache.james.task.MemoryReferenceWithCounterTask;
import org.apache.james.task.ThrowingTask;

public interface TestTaskDTOModules {

    TaskDTOModule<TestTask, TestTaskDTO> TEST_TYPE = TaskDTOModule
        .forTask(TestTask.class)
        .convertToDTO(TestTaskDTO.class)
        .toDomainObjectConverter(TestTaskDTO::toTask)
        .toDTOConverter((task, typeName) -> new TestTaskDTO(
            typeName,
            task.getParameter()))
        .typeName("test-task")
        .withFactory(TaskDTOModule::new);

    TaskDTOModule<FailedTask, FailedTaskDTO> FAILED_TASK_MODULE = DTOModule
        .forDomainObject(FailedTask.class)
        .convertToDTO(FailedTaskDTO.class)
        .toDomainObjectConverter(dto -> new FailedTask())
        .toDTOConverter((task, typeName) -> new FailedTaskDTO(typeName))
        .typeName("failed-task")
        .withFactory(TaskDTOModule::new);

    TaskDTOModule<CompletedTask, CompletedTaskDTO> COMPLETED_TASK_MODULE = DTOModule
        .forDomainObject(CompletedTask.class)
        .convertToDTO(CompletedTaskDTO.class)
        .toDomainObjectConverter(dto -> new CompletedTask())
        .toDTOConverter((task, typeName) -> new CompletedTaskDTO(typeName))
        .typeName("completed-task")
        .withFactory(TaskDTOModule::new);

    TaskDTOModule<FailsDeserializationTask, FailsDeserializationTaskDTO> FAILS_DESERIALIZATION_TASK_MODULE = DTOModule
        .forDomainObject(FailsDeserializationTask.class)
        .convertToDTO(FailsDeserializationTaskDTO.class)
        .toDomainObjectConverter(dto -> {
            throw new RuntimeException("fail to deserialize");
        })
        .toDTOConverter((task, typeName) -> new FailsDeserializationTaskDTO(typeName))
        .typeName(FailsDeserializationTask.TASK_TYPE)
        .withFactory(TaskDTOModule::new);

    TaskDTOModule<ThrowingTask, ThrowingTaskDTO> THROWING_TASK_MODULE = DTOModule
        .forDomainObject(ThrowingTask.class)
        .convertToDTO(ThrowingTaskDTO.class)
        .toDomainObjectConverter(dto -> new ThrowingTask())
        .toDTOConverter((task, typeName) -> new ThrowingTaskDTO(typeName))
        .typeName("throwing-task")
        .withFactory(TaskDTOModule::new);


    Function<MemoryReferenceTaskStore, TaskDTOModule<MemoryReferenceTask, MemoryReferenceTaskDTO>> MEMORY_REFERENCE_TASK_MODULE = store -> DTOModule
        .forDomainObject(MemoryReferenceTask.class)
        .convertToDTO(MemoryReferenceTaskDTO.class)
        .toDomainObjectConverter(dto -> store.get(dto.getReference()))
        .toDTOConverter((task, typeName) -> new MemoryReferenceTaskDTO(typeName, store.add(task)))
        .typeName(MemoryReferenceTask.TYPE.asString())
        .withFactory(TaskDTOModule::new);

    Function<MemoryReferenceWithCounterTaskStore, TaskDTOModule<MemoryReferenceWithCounterTask, MemoryReferenceWithCounterTaskDTO>> MEMORY_REFERENCE_WITH_COUNTER_TASK_MODULE = store -> DTOModule
        .forDomainObject(MemoryReferenceWithCounterTask.class)
        .convertToDTO(MemoryReferenceWithCounterTaskDTO.class)
        .toDomainObjectConverter(dto -> store.get(dto.getReference()))
        .toDTOConverter((task, typeName) -> new MemoryReferenceWithCounterTaskDTO(typeName, store.add(task)))
        .typeName(MemoryReferenceWithCounterTask.TYPE.asString())
        .withFactory(TaskDTOModule::new);

}
