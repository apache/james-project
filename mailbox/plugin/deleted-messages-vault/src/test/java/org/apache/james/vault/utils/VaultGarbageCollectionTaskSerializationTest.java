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
package org.apache.james.vault.utils;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.time.ZonedDateTime;

import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.vault.DeletedMessageVault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class VaultGarbageCollectionTaskSerializationTest {

    private DeletedMessageVault vault;
    private JsonTaskSerializer taskSerializer;
    private final ZonedDateTime beginningOfRetentionPeriod = ZonedDateTime.now();
    private final String serializedVaultGarbageCollectionTask = "{\"type\": \"vault-garbage-collection\", \"beginningOfRetentionPeriod\": \"" + beginningOfRetentionPeriod.toString() + "\"}";

    @BeforeEach
    void setUp() {
        vault = mock(DeletedMessageVault.class);
        taskSerializer = new JsonTaskSerializer(moduleVaultGarbageCollection());
    }

    private TaskDTOModule moduleVaultGarbageCollection() {
        VaultGarbageCollectionTask.Factory factory = new VaultGarbageCollectionTask.Factory(vault);
        return TaskDTOModule
            .forTask(VaultGarbageCollectionTask.class)
            .convertToDTO(VaultGarbageCollectionTask.VaultGarbageCollectionTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter((task, typeName) ->
                VaultGarbageCollectionTask.VaultGarbageCollectionTaskDTO.of(typeName, task)
            )
            .typeName("vault-garbage-collection")
            .withFactory(TaskDTOModule::new);
    }

    @Test
    void vaultGarbageCollectionShouldBeSerializable() throws JsonProcessingException {
        VaultGarbageCollectionTask task = new VaultGarbageCollectionTask(vault, beginningOfRetentionPeriod);

        assertThatJson(taskSerializer.serialize(task))
            .isEqualTo(serializedVaultGarbageCollectionTask);
    }

    @Test
    void vaultGarbageCollectionShouldBeDeserializable() throws IOException {
        VaultGarbageCollectionTask task = new VaultGarbageCollectionTask(vault, beginningOfRetentionPeriod);

        assertThat(taskSerializer.deserialize(serializedVaultGarbageCollectionTask))
            .isEqualToComparingOnlyGivenFields(task, "beginningOfRetentionPeriod");
    }

}