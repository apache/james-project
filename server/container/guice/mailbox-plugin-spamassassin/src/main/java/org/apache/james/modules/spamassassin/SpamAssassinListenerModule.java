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

package org.apache.james.modules.spamassassin;

import java.io.FileNotFoundException;

import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.spamassassin.SpamAssassinConfiguration;
import org.apache.james.mailbox.spamassassin.SpamAssassinListener;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class SpamAssassinListenerModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpamAssassinListenerModule.class);

    public static final String SPAMASSASSIN_CONFIGURATION_NAME = "spamassassin";

    @Override
    protected void configure() {
        bind(SpamAssassinListener.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), MailboxListener.class).addBinding().to(SpamAssassinListener.class);
    }

    @Provides
    @Singleton
    private SpamAssassinConfiguration getSpamAssassinConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            PropertiesConfiguration configuration = propertiesProvider.getConfiguration(SPAMASSASSIN_CONFIGURATION_NAME);
            return SpamAssassinConfigurationLoader.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + SPAMASSASSIN_CONFIGURATION_NAME + " configuration file. Disabling this service.");
            return SpamAssassinConfigurationLoader.disable();
        }
    }

}
