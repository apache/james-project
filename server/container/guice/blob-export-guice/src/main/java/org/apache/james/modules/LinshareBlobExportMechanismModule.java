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
import java.net.MalformedURLException;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.linshare.LinshareConfiguration;
import org.apache.james.linshare.client.LinshareAPI;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class LinshareBlobExportMechanismModule extends AbstractModule {
    @Override
    protected void configure() {
    }

    @Singleton
    @Provides
    LinshareConfiguration linshareConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException, MalformedURLException {
        Configuration configuration = propertiesProvider.getConfigurations(ConfigurationComponent.NAMES);
        return LinshareConfiguration.from(configuration);
    }

    @Singleton
    @Provides
    LinshareAPI linshare(LinshareConfiguration configuration) {
        return LinshareAPI.from(configuration);
    }
}
