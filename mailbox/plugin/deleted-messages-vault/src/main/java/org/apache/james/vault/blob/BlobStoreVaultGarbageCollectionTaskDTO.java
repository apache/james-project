/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.vault.blob;

import java.time.ZonedDateTime;
import java.util.Collection;

import org.apache.james.blob.api.BucketName;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.steveash.guavate.Guavate;
import reactor.core.publisher.Flux;

public class BlobStoreVaultGarbageCollectionTaskDTO implements TaskDTO {
    static BlobStoreVaultGarbageCollectionTaskDTO fromDomainObject(BlobStoreVaultGarbageCollectionTask task, String type) {
        return new BlobStoreVaultGarbageCollectionTaskDTO(
            type,
            task.getBeginningOfRetentionPeriod().toString(),
            task.getRetentionOperation()
                .map(BucketName::asString)
                .collect(Guavate.toImmutableList())
                .block()
        );
    }

    public static final TaskDTOModule<BlobStoreVaultGarbageCollectionTask, BlobStoreVaultGarbageCollectionTaskDTO> MODULE =
        DTOModule
            .forDomainObject(BlobStoreVaultGarbageCollectionTask.class)
            .convertToDTO(BlobStoreVaultGarbageCollectionTaskDTO.class)
            .toDomainObjectConverter(BlobStoreVaultGarbageCollectionTaskDTO::toDomainObject)
            .toDTOConverter(BlobStoreVaultGarbageCollectionTaskDTO::fromDomainObject)
            .typeName(BlobStoreVaultGarbageCollectionTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);


    private final String type;
    private final String beginningOfRetentionPeriod;
    private final Collection<String> retentionOperation;

    BlobStoreVaultGarbageCollectionTaskDTO(@JsonProperty("type") String type,
                                           @JsonProperty("beginningOfRetentionPeriod") String beginningOfRetentionPeriod,
                                           @JsonProperty("retentionOperation") Collection<String> retentionOperation) {
        this.type = type;
        this.beginningOfRetentionPeriod = beginningOfRetentionPeriod;
        this.retentionOperation = retentionOperation;
    }

    BlobStoreVaultGarbageCollectionTask toDomainObject() {
        return new BlobStoreVaultGarbageCollectionTask(
            ZonedDateTime.parse(beginningOfRetentionPeriod),
            Flux.fromIterable(retentionOperation)
                .map(BucketName::of));
    }

    @Override
    public String getType() {
        return type;
    }

    public String getBeginningOfRetentionPeriod() {
        return beginningOfRetentionPeriod;
    }

    public Collection<String> getRetentionOperation() {
        return retentionOperation;
    }
}
