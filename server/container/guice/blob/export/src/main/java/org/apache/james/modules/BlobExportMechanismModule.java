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

package org.apache.james.modules;

import java.io.FileNotFoundException;

import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.file.LocalFileBlobExportMechanism;
import org.apache.james.linshare.LinshareBlobExportMechanism;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;

public class BlobExportMechanismModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlobExportMechanismModule.class);

    @Override
    protected void configure() {
        install(new LocalFileBlobExportMechanismModule());
        install(new LinshareBlobExportMechanismModule());
    }

    @VisibleForTesting
    @Provides
    @Singleton
    BlobExportImplChoice provideChoice(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfigurations(ConfigurationComponent.NAMES);
            return BlobExportImplChoice.from(configuration)
                .orElseGet(() -> {
                    LOGGER.warn("No blob export mechanism defined. Defaulting to " + BlobExportImplChoice.LOCAL_FILE.getImplName());
                    return BlobExportImplChoice.LOCAL_FILE;
                });
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ConfigurationComponent.NAME + " configuration file, using localFile blob exporting as the default");
            return BlobExportImplChoice.LOCAL_FILE;
        }
    }

    @VisibleForTesting
    @Provides
    @Singleton
    BlobExportMechanism provideMechanism(BlobExportImplChoice implChoice,
                                         Provider<LocalFileBlobExportMechanism> localFileMechanismProvider,
                                         Provider<LinshareBlobExportMechanism> linshareMechanismProvider) {
        switch (implChoice) {
            case LOCAL_FILE:
                return localFileMechanismProvider.get();
            case LINSHARE:
                return linshareMechanismProvider.get();
            default:
                throw new RuntimeException("blobExportMechanism '" + implChoice.getImplName() + "' is not supported yet");
        }
    }
}
