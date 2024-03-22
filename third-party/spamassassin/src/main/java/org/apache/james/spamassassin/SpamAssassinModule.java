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

package org.apache.james.spamassassin;

import java.io.FileNotFoundException;

import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.util.Host;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class SpamAssassinModule extends AbstractModule {
    private static final String SPAMASSASSIN_HOST = "spamassassin.host";
    private static final String SPAMASSASSIN_PORT = "spamassassin.port";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 783;

    @Provides
    @Singleton
    private SpamAssassinConfiguration getSpamAssassinConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        Configuration configuration = propertiesProvider.getConfiguration("spamassassin");

        Host host = Host.from(configuration.getString(SPAMASSASSIN_HOST, DEFAULT_HOST),
            configuration.getInteger(SPAMASSASSIN_PORT, DEFAULT_PORT));

        return new SpamAssassinConfiguration(host);
    }
}
