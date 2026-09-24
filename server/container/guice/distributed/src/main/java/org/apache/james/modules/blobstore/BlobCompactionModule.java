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

import org.apache.james.blob.api.BlobIdUpdater;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.compaction.BlobCompactionAlgorithm;
import org.apache.james.blob.compaction.BlobCompactionDTOModules;
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
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class BlobCompactionModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobCompactionModule.class);

    @Provides
    @Singleton
    public CompactionConfiguration compactionConfiguration() {
        return CompactionConfiguration.DEFAULT;
    }

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
        if (injector.getExistingBinding(Key.get(BlobIdUpdater.Factory.class)) != null
            && injector.getExistingBinding(Key.get(BlobStoreDAO.class)) != null
            && injector.getExistingBinding(Key.get(BlobStoreDAO.class, Names.named(BlobStoreModulesChooser.RAW))) != null) {

            BlobStoreDAO blobStoreDAO = injector.getInstance(BlobStoreDAO.class);
            BlobStoreDAO rawStore = injector.getInstance(Key.get(BlobStoreDAO.class, Names.named(BlobStoreModulesChooser.RAW)));
            BlobIdUpdater.Factory updaterFactory = injector.getInstance(BlobIdUpdater.Factory.class);
            return Optional.of(new BlobCompactionAlgorithm(blobStoreDAO, rawStore, updaterFactory));
        }
        return Optional.empty();
    }

    @Provides
    @Singleton
    public BlobCompactionAlgorithm blobCompactionAlgorithm(Optional<BlobCompactionAlgorithm> algorithm) {
        return algorithm.orElseThrow(() -> new IllegalStateException("BlobCompactionAlgorithm is not available in the current environment"));
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> blobCompactionTask(Provider<BlobCompactionAlgorithm> algorithmProvider, Clock clock) {
        return BlobCompactionDTOModules.taskModule(algorithmProvider::get, clock);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> initialBlobCompactionTask(Provider<BlobCompactionAlgorithm> algorithmProvider, Clock clock) {
        return BlobCompactionDTOModules.initialCompactionTaskModule(algorithmProvider::get, clock);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> gcBlobCompactionTask(Provider<BlobCompactionAlgorithm> algorithmProvider, Clock clock) {
        return BlobCompactionDTOModules.gcCompactionTaskModule(algorithmProvider::get, clock);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> blobCompactionAdditionalInformation() {
        return BlobCompactionDTOModules.additionalInformationModule();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> initialBlobCompactionAdditionalInformation() {
        return BlobCompactionDTOModules.initialAdditionalInformationModule();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> gcBlobCompactionAdditionalInformation() {
        return BlobCompactionDTOModules.gcAdditionalInformationModule();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminBlobCompactionAdditionalInformation() {
        return BlobCompactionDTOModules.additionalInformationModule();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminInitialBlobCompactionAdditionalInformation() {
        return BlobCompactionDTOModules.initialAdditionalInformationModule();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminGCBlobCompactionAdditionalInformation() {
        return BlobCompactionDTOModules.gcAdditionalInformationModule();
    }
}
