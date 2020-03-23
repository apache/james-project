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
import org.apache.james.PeriodicalHealthChecks;
import org.apache.james.PeriodicalHealthChecksConfiguration;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class PeriodicalHealthChecksModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodicalHealthChecksModule.class);
    private static final String FILENAME = "healthcheck";

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), HealthCheck.class);
    }

    @Singleton
    @Provides
    PeriodicalHealthChecksConfiguration periodicalHealthChecksConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfigurations(FILENAME);
            return PeriodicalHealthChecksConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find {} configuration file, using default configuration", FILENAME);
            return PeriodicalHealthChecksConfiguration.DEFAULT_CONFIGURATION;
        }
    }

    @ProvidesIntoSet
    InitializationOperation configurePeriodicalHealthChecks(PeriodicalHealthChecks periodicalHealthChecks) {
        return InitilizationOperationBuilder
            .forClass(PeriodicalHealthChecks.class)
            .init(periodicalHealthChecks::start);
    }
}
