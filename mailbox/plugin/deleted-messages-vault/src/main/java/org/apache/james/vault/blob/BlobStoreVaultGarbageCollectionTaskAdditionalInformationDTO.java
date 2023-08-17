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

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;

import org.apache.james.blob.api.BucketName;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;

public class BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    static BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO fromDomainObject(BlobStoreVaultGarbageCollectionTask.AdditionalInformation additionalInformation, String type) {
        return new BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO(
            type,
            additionalInformation.getBeginningOfRetentionPeriod().toString(),
            additionalInformation.getDeletedBuckets(),
            additionalInformation.timestamp()
        );
    }

    public static final AdditionalInformationDTOModule<BlobStoreVaultGarbageCollectionTask.AdditionalInformation, BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(BlobStoreVaultGarbageCollectionTask.AdditionalInformation.class)
            .convertToDTO(BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(BlobStoreVaultGarbageCollectionTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    public static final AdditionalInformationDTOModule<BlobStoreVaultGarbageCollectionTask.AdditionalInformation, BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(BlobStoreVaultGarbageCollectionTask.AdditionalInformation.class)
            .convertToDTO(BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(BlobStoreVaultGarbageCollectionTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String beginningOfRetentionPeriod;
    private final Collection<String> deletedBuckets;
    private final String type;
    private final Instant timestamp;

    BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO(
        @JsonProperty("type") String type,
        @JsonProperty("beginningOfRetentionPeriod") String beginningOfRetentionPeriod,
        @JsonProperty("deletedBuckets") Collection<String> deletedBuckets,
        @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.beginningOfRetentionPeriod = beginningOfRetentionPeriod;
        this.deletedBuckets = deletedBuckets;
        this.timestamp = timestamp;
    }

    BlobStoreVaultGarbageCollectionTask.AdditionalInformation toDomainObject() {
        return new BlobStoreVaultGarbageCollectionTask.AdditionalInformation(
            ZonedDateTime.parse(beginningOfRetentionPeriod),
            deletedBuckets
                .stream()
                .map(BucketName::of)
                .collect(ImmutableSet.toImmutableSet()),
            timestamp);
    }

    public String getBeginningOfRetentionPeriod() {
        return beginningOfRetentionPeriod;
    }

    public Collection<String> getDeletedBuckets() {
        return deletedBuckets;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}
