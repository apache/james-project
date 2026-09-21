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

package org.apache.james.modules.blobstore;

import java.time.Clock;
import java.util.Optional;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.compaction.BlobCompactionAlgorithm;
import org.apache.james.blob.compaction.BlobCompactionDTOModules;
import org.apache.james.blob.compaction.BlobIdUpdater;
import org.apache.james.blob.compaction.BlobReferenceMappingSource;
import org.apache.james.blob.compaction.CompactionConfiguration;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.webadmin.dto.DTOModuleInjections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;

public class BlobCompactionModule extends AbstractModule {

    @Provides
    @Singleton
    public CompactionConfiguration compactionConfiguration() {
        return CompactionConfiguration.DEFAULT;
    }

    @Provides
    @Singleton
    public BlobCompactionAlgorithm blobCompactionAlgorithm(BlobStoreDAO blobStoreDAO,
                                                          @Named(BlobStoreModulesChooser.RAW) BlobStoreDAO rawStore,
                                                          BlobReferenceMappingSource mappingSource,
                                                          BlobIdUpdater blobIdUpdater) {
        return new BlobCompactionAlgorithm(blobStoreDAO, rawStore, mappingSource, blobIdUpdater);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BlobCompactionModule.class);

    @Provides
    @Singleton
    public Optional<BlobCompactionAlgorithm> optionalBlobCompactionAlgorithm(Injector injector) {
        if (injector.getExistingBinding(Key.get(BlobStoreConfiguration.class)) != null) {
            BlobStoreConfiguration config = injector.getInstance(BlobStoreConfiguration.class);
            if (config.getCryptoConfig().isPresent()) {
                LOGGER.warn("Blob compaction is disabled because client-side encryption is enabled in BlobStoreConfiguration");
                return Optional.empty();
            }
            if (config.getCompressionConfiguration().enabled()) {
                LOGGER.warn("Blob compaction is disabled because blob compression is enabled in BlobStoreConfiguration");
                return Optional.empty();
            }
        }
        if (injector.getExistingBinding(Key.get(BlobCompactionAlgorithm.class)) != null) {
            return Optional.of(injector.getInstance(BlobCompactionAlgorithm.class));
        }
        return Optional.empty();
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> blobCompactionTask(BlobCompactionAlgorithm algorithm, Clock clock) {
        return BlobCompactionDTOModules.taskModule(algorithm, clock);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> blobCompactionAdditionalInformation() {
        return BlobCompactionDTOModules.additionalInformationModule();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminBlobCompactionAdditionalInformation() {
        return BlobCompactionDTOModules.additionalInformationModule();
    }
}
