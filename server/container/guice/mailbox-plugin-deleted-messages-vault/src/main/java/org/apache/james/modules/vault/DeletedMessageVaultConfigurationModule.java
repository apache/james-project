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

package org.apache.james.modules.vault;

import java.io.FileNotFoundException;

import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.vault.VaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Provides;

public class DeletedMessageVaultConfigurationModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedMessageVaultConfigurationModule.class);

    @Provides
    @Singleton
    VaultConfiguration providesVaultConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, org.apache.commons.configuration2.ex.ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("deletedMessageVault");
            return VaultConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Error encountered while retrieving Deleted message vault configuration. Using default RetentionTime (1 year) and RestoreLocation (Restored-Messages) instead.");
            return VaultConfiguration.DEFAULT;
        }
    }
}
