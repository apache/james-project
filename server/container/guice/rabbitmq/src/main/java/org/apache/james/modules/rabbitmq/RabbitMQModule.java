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
package org.apache.james.modules.rabbitmq;

import java.io.FileNotFoundException;

import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backend.rabbitmq.RabbitMQConfiguration;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class RabbitMQModule extends AbstractModule {

    public static final String RABBITMQ_CONFIGURATION_NAME = "rabbitmq";

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQModule.class);

    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    private RabbitMQConfiguration getMailQueueConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            PropertiesConfiguration configuration = propertiesProvider.getConfiguration(RABBITMQ_CONFIGURATION_NAME);
            return RabbitMQConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.error("Could not find " + RABBITMQ_CONFIGURATION_NAME + " configuration file.");
            throw new RuntimeException(e);
        }
    }
}
