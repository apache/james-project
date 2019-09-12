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
package org.apache.james.modules.protocols;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.managesieve.api.commands.CoreCommands;
import org.apache.james.managesieve.core.CoreProcessor;
import org.apache.james.managesieveserver.netty.ManageSieveServerFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.util.LoggingLevel;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitialisationOperation;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class ManageSieveServerModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new SieveModule());
        bind(CoreCommands.class).to(CoreProcessor.class);
        Multibinder.newSetBinder(binder(), InitialisationOperation.class).addBinding().to(ManageSieveModuleInitialisationOperation.class);
        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(SieveProbeImpl.class);
    }

    @Singleton
    public static class ManageSieveModuleInitialisationOperation implements InitialisationOperation {
        private final ConfigurationProvider configurationProvider;
        private final ManageSieveServerFactory manageSieveServerFactory;

        @Inject
        public ManageSieveModuleInitialisationOperation(ConfigurationProvider configurationProvider, ManageSieveServerFactory manageSieveServerFactory) {
            this.configurationProvider = configurationProvider;
            this.manageSieveServerFactory = manageSieveServerFactory;
        }

        @Override
        public void initModule() throws Exception {
            manageSieveServerFactory.configure(configurationProvider.getConfiguration("managesieveserver", LoggingLevel.INFO));
            manageSieveServerFactory.init();
        }

        @Override
        public Class<? extends Startable> forClass() {
            return ManageSieveServerFactory.class;
        }
    }
}