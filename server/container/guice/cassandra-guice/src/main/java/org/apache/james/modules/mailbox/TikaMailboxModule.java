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

package org.apache.james.modules.mailbox;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaHttpClient;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Ints;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class TikaMailboxModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(TikaMailboxModule.class);

    private static final String TIKA_CONFIGURATION_NAME = "tika";
    private static final String TIKA_HOST = "tika.host";
    private static final String TIKA_PORT = "tika.port";
    private static final String TIKA_TIMEOUT_IN_MS = "tika.timeoutInMillis";

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 9998;
    private static final int DEFAULT_TIMEOUT_IN_MS = Ints.checkedCast(TimeUnit.SECONDS.toMillis(30));

    @Override
    protected void configure() {
        bind(TikaTextExtractor.class).in(Scopes.SINGLETON);
        bind(TextExtractor.class).to(TikaTextExtractor.class);
    }

    @Provides
    @Singleton
    protected TikaHttpClient provideTikaHttpClient(TikaConfiguration tikaConfiguration) throws URISyntaxException {
        return new TikaHttpClientImpl(tikaConfiguration);
    }

    @Provides
    @Singleton
    private TikaConfiguration getTikaConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            PropertiesConfiguration configuration = propertiesProvider.getConfiguration(TIKA_CONFIGURATION_NAME);
            return TikaConfiguration.builder()
                    .host(configuration.getString(TIKA_HOST, DEFAULT_HOST))
                    .port(configuration.getInt(TIKA_PORT, DEFAULT_PORT))
                    .timeoutInMillis(configuration.getInt(TIKA_TIMEOUT_IN_MS, DEFAULT_TIMEOUT_IN_MS))
                    .build();
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find {} configuration file. Using {}:{} as contact point", TIKA_CONFIGURATION_NAME, DEFAULT_HOST, DEFAULT_PORT);
            return TikaConfiguration.builder()
                    .host(DEFAULT_HOST)
                    .port(DEFAULT_PORT)
                    .timeoutInMillis(DEFAULT_TIMEOUT_IN_MS)
                    .build();
        }
    }

}
