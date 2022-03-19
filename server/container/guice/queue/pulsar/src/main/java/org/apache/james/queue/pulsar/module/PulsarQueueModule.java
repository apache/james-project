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

package org.apache.james.queue.pulsar.module;

import java.io.FileNotFoundException;
import java.time.Clock;

import javax.inject.Named;
import javax.inject.Singleton;

import jakarta.mail.internet.MimeMessage;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.pulsar.PulsarClients;
import org.apache.james.backends.pulsar.PulsarConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.pulsar.PulsarMailQueue;
import org.apache.james.queue.pulsar.PulsarMailQueueFactory;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;

public class PulsarQueueModule extends AbstractModule {
    static final String PULSAR_CONFIGURATION_NAME = "pulsar";
    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarQueueModule.class);

    @Provides
    @Named(PULSAR_CONFIGURATION_NAME)
    @Singleton
    private Configuration rawConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return propertiesProvider.getConfiguration(PULSAR_CONFIGURATION_NAME);
        } catch (FileNotFoundException e) {
            LOGGER.error("Could not find " + PULSAR_CONFIGURATION_NAME + " configuration file.");
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    private PulsarConfiguration pulsarConfiguration(@Named(PULSAR_CONFIGURATION_NAME) Configuration configuration) {
        return PulsarConfiguration.from(configuration);
    }

    @Provides
    @Singleton
    private PulsarClients pulsarClients(PulsarConfiguration configuration) {
        return PulsarClients.create(configuration);
    }

    @Provides
    @Singleton
    public MailQueueFactory<PulsarMailQueue> providePulsarMailQueueFactoryProxy(PulsarConfiguration pulsarConfig,
                                                                                PulsarClients pulsarClients,
                                                                                BlobId.Factory blobIdFactory,
                                                                                MimeMessageStore.Factory mimeFactory,
                                                                                MailQueueItemDecoratorFactory decoratorFactory,
                                                                                MetricFactory metricFactory,
                                                                                GaugeRegistry gaugeRegistry,
                                                                                Clock clock) {
        Store<MimeMessage, MimeMessagePartsId> mimeMessageMimeMessagePartsIdStore = mimeFactory.mimeMessageStore();
        return new PulsarMailQueueFactory(
            pulsarConfig,
            pulsarClients,
            blobIdFactory,
            mimeMessageMimeMessagePartsIdStore,
            decoratorFactory,
            metricFactory,
            gaugeRegistry
        );
    }

    @Provides
    @Singleton
    public MailQueueFactory<? extends ManageableMailQueue> providePulsarManageableMailQueueFactory(MailQueueFactory<PulsarMailQueue> queueFactory) {
        return queueFactory;
    }

    @Provides
    @Singleton
    public MailQueueFactory<? extends MailQueue> providePulsarManageableMailQueueFactoryGenerics(MailQueueFactory<PulsarMailQueue> queueFactory) {
        return queueFactory;
    }

    @Provides
    @Singleton
    public MailQueueFactory<?> mailQueue(Provider<MailQueueFactory<? extends ManageableMailQueue>> provider) {
        return provider.get();
    }
}

