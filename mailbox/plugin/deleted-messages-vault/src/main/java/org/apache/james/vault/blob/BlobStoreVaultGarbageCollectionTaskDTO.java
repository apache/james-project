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

package org.apache.james.vault.blob;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BlobStoreVaultGarbageCollectionTaskDTO implements TaskDTO {
    static BlobStoreVaultGarbageCollectionTaskDTO fromDomainObject(BlobStoreVaultGarbageCollectionTask task, String type) {
        return new BlobStoreVaultGarbageCollectionTaskDTO(type);
    }

    public static TaskDTOModule<BlobStoreVaultGarbageCollectionTask, BlobStoreVaultGarbageCollectionTaskDTO> module(BlobStoreVaultGarbageCollectionTask.Factory factory) {
        return DTOModule
            .forDomainObject(BlobStoreVaultGarbageCollectionTask.class)
            .convertToDTO(BlobStoreVaultGarbageCollectionTaskDTO.class)
            .toDomainObjectConverter(dto -> BlobStoreVaultGarbageCollectionTaskDTO.toDomainObject(factory))
            .toDTOConverter(BlobStoreVaultGarbageCollectionTaskDTO::fromDomainObject)
            .typeName(BlobStoreVaultGarbageCollectionTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }


    private final String type;

    BlobStoreVaultGarbageCollectionTaskDTO(@JsonProperty("type") String type) {
        this.type = type;
    }

    private static BlobStoreVaultGarbageCollectionTask toDomainObject(BlobStoreVaultGarbageCollectionTask.Factory factory) {
        return factory.create();
    }

    @Override
    public String getType() {
        return type;
    }

}
