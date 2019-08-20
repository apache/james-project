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
import org.apache.james.lmtpserver.netty.LMTPServerFactory;
import org.apache.james.lmtpserver.netty.OioLMTPServerFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.util.LoggingLevel;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitialisationOperation;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class LMTPServerModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(LMTPServerFactory.class).in(Scopes.SINGLETON);
        bind(OioLMTPServerFactory.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), InitialisationOperation.class).addBinding().to(LMTPModuleInitialisationOperation.class);
        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(LmtpGuiceProbe.class);
    }

    @Singleton
    public static class LMTPModuleInitialisationOperation implements InitialisationOperation {

        private final ConfigurationProvider configurationProvider;
        private final LMTPServerFactory lmtpServerFactory;

        @Inject
        public LMTPModuleInitialisationOperation(ConfigurationProvider configurationProvider, LMTPServerFactory lmtpServerFactory) {
            this.configurationProvider = configurationProvider;
            this.lmtpServerFactory = lmtpServerFactory;
        }

        @Override
        public void initModule() throws Exception {
            lmtpServerFactory.configure(configurationProvider.getConfiguration("lmtpserver", LoggingLevel.INFO));
            lmtpServerFactory.init();
        }

        @Override
        public Class<? extends Startable> forClass() {
            return LMTPServerFactory.class;
        }
    }

}
