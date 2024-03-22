/****************************************************************
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

package org.apache.james.modules.blobstore.validation;

import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.server.blob.deduplication.StorageStrategy;

import com.google.common.annotations.VisibleForTesting;

public class BlobStoreConfigurationValidationStartUpCheck implements StartUpCheck {
    @FunctionalInterface
    public interface StorageStrategySupplier extends Supplier<StorageStrategy> {

    }

    private static final String BLOB_STORE_CONFIGURATION_VALIDATION = "blobStore-configuration-validation";
    private final StorageStrategySupplier storageStrategySupplier;
    private final EventsourcingStorageStrategy eventsourcingStorageStrategy;

    @VisibleForTesting
    @Inject
    public BlobStoreConfigurationValidationStartUpCheck(StorageStrategySupplier storageStrategySupplier, EventsourcingStorageStrategy eventsourcingStorageStrategy) {
        this.storageStrategySupplier = storageStrategySupplier;
        this.eventsourcingStorageStrategy = eventsourcingStorageStrategy;
    }

    @Override
    public CheckResult check() {
        try {
            eventsourcingStorageStrategy.registerStorageStrategy(storageStrategySupplier.get());
            return CheckResult.builder()
                .checkName(BLOB_STORE_CONFIGURATION_VALIDATION)
                .resultType(ResultType.GOOD)
                .build();
        } catch (IllegalStateException e) {
            return CheckResult.builder()
                .checkName(BLOB_STORE_CONFIGURATION_VALIDATION)
                .resultType(ResultType.BAD)
                .description(e.getMessage())
                .build();
        }
    }

    @Override
    public String checkName() {
        return BLOB_STORE_CONFIGURATION_VALIDATION;
    }
}
