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
package org.apache.james.modules.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.dnsjava.DNSJavaService;

import com.google.inject.AbstractModule;
import org.apache.james.utils.ClassPathConfigurationProvider;
import org.apache.james.utils.ConfigurationPerformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DNSServiceModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(DNSServiceModule.class);

    @Override
    protected void configure() {
        bind(DNSService.class).to(DNSJavaService.class);
        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(DNSServiceConfigurationPerformer.class);
    }

    @Singleton
    public static class DNSServiceConfigurationPerformer implements ConfigurationPerformer {

        private final ClassPathConfigurationProvider classPathConfigurationProvider;
        private final DNSJavaService dnsService;

        @Inject
        public DNSServiceConfigurationPerformer(ClassPathConfigurationProvider classPathConfigurationProvider,
                                                DNSJavaService dnsService) {
            this.classPathConfigurationProvider = classPathConfigurationProvider;
            this.dnsService = dnsService;
        }

        public void initModule() throws Exception {
            dnsService.setLog(LOGGER);
            dnsService.configure(classPathConfigurationProvider.getConfiguration("dnsservice"));
            dnsService.init();
        }
    }
}