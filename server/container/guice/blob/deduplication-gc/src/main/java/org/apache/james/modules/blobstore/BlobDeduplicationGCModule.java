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

import java.io.FileNotFoundException;
import java.time.Clock;
import java.util.Set;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.modules.blobstore.server.BlobRoutesModules;
import org.apache.james.server.blob.deduplication.BlobGCTaskAdditionalInformationDTO;
import org.apache.james.server.blob.deduplication.BlobGCTaskDTO;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.webadmin.dto.DTOModuleInjections;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;

public class BlobDeduplicationGCModule extends AbstractModule {
    private static final String NAME = "blob";
    private static final String LEGACY = "blobstore";

    @Override
    protected void configure() {
        bind(PlainBlobId.Factory.class).in(Scopes.SINGLETON);
        bind(BlobId.Factory.class).to(GenerationAwareBlobId.Factory.class);

        bind(MetricableBlobStore.class).in(Scopes.SINGLETON);
        bind(BlobStore.class).to(MetricableBlobStore.class);

        install(new BlobRoutesModules());
    }

    @Singleton
    @Provides
    public GenerationAwareBlobId.Factory generationAwareBlobIdFactory(Clock clock, PlainBlobId.Factory delegate, GenerationAwareBlobId.Configuration configuration) {
        return new GenerationAwareBlobId.Factory(clock, delegate, configuration);
    }

    @Singleton
    @Provides
    public GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration properties = propertiesProvider.getConfigurations(NAME, LEGACY);
            return GenerationAwareBlobId.Configuration.parse(properties);
        } catch (FileNotFoundException e) {
            return GenerationAwareBlobId.Configuration.DEFAULT;
        }
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> blobGCTask(BlobStoreDAO blobStoreDAO,
                                                                       GenerationAwareBlobId.Factory generationAwareBlobIdFactory,
                                                                       GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration,
                                                                       Set<BlobReferenceSource> blobReferenceSources,
                                                                       Clock clock) {
        return BlobGCTaskDTO.module(blobStoreDAO, generationAwareBlobIdFactory, generationAwareBlobIdConfiguration, blobReferenceSources, clock);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> blobGCAdditionalInformation() {
        return BlobGCTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminBlobGCAdditionalInformation() {
        return BlobGCTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }
}
