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

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.blob.export.file.LocalFileBlobExportMechanism;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class LocalFileBlobExportMechanismModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileBlobExportMechanismModule.class);

    @Override
    protected void configure() {
    }

    @Singleton
    @Provides
    LocalFileBlobExportMechanism.Configuration localFileExportConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfigurations(ConfigurationComponent.NAMES);
            return LocalFileBlobExportMechanism.Configuration.from(configuration)
                .orElseGet(() -> {
                    LOGGER.warn("Missing LocalFileBlobExportMechanism configuration, using default localFile blob exporting configuration");
                    return LocalFileBlobExportMechanism.Configuration.DEFAULT_CONFIGURATION;
                });
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ConfigurationComponent.NAME + " configuration file, using default localFile blob exporting configuration");
            return LocalFileBlobExportMechanism.Configuration.DEFAULT_CONFIGURATION;
        }
    }
}
