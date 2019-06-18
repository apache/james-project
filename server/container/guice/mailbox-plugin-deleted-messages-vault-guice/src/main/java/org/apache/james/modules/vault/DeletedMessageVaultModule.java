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

import org.apache.commons.configuration.Configuration;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.MailRepositoryDeletedMessageVault;
import org.apache.james.vault.RetentionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Provides;

public class DeletedMessageVaultModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedMessageVaultModule.class);
    private static final MailRepositoryPath DEFAULT_PATH = MailRepositoryPath.from("var/deletedMessages/vault");

    @Override
    protected void configure() {
        bind(DeletedMessageVault.class)
            .to(MailRepositoryDeletedMessageVault.class);
    }

    @Provides
    MailRepositoryDeletedMessageVault.Configuration providesConfiguration(PropertiesProvider propertiesProvider, MailRepositoryStoreConfiguration mailRepositoryStoreConfiguration) throws ConfigurationException  {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("deletedMessageVault");
            return MailRepositoryDeletedMessageVault.Configuration.from(configuration);
        } catch (Exception e) {
            LOGGER.warn("Error encountered while retrieving Deleted message vault configuration. Using default MailRepository URL instead.");
            Protocol defaultProtocol = mailRepositoryStoreConfiguration.getDefaultProtocol()
                .orElseThrow(() -> new IllegalStateException("No default MailRepository Protocol could be inferred. We can not configure the default deletedMessages vault."));

            return new MailRepositoryDeletedMessageVault.Configuration(
                MailRepositoryUrl.fromPathAndProtocol(defaultProtocol, DEFAULT_PATH));
        }
    }

    @Provides
    RetentionConfiguration providesRetentionConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, org.apache.commons.configuration.ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("deletedMessageVault");
            return RetentionConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Error encountered while retrieving Deleted message vault configuration. Using default MailRepository RetentionTime (1 year) instead.");
            return RetentionConfiguration.DEFAULT;
        }
    }
}
