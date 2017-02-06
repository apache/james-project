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

import java.util.List;

import org.apache.james.jspf.core.DNSService;
import org.apache.james.jspf.impl.DNSServiceXBillImpl;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.smtpserver.SendMailHandler;
import org.apache.james.smtpserver.fastfail.SPFHandler;
import org.apache.james.smtpserver.netty.SMTPServerFactory;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.ConfigurationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class SMTPServerModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(SMTPServerModule.class);

    @Override
    protected void configure() {
        bind(DNSServiceXBillImpl.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(SMTPModuleConfigurationPerformer.class);
    }
    
    @Provides
    public DNSServiceXBillImpl provideJSPFDNSService() {
        return new DNSServiceXBillImpl(new SPFHandler.SPFLogger(LOGGER));
    }

    @Singleton
    public static class SMTPModuleConfigurationPerformer implements ConfigurationPerformer {

        private final ConfigurationProvider configurationProvider;
        private final SMTPServerFactory smtpServerFactory;
        private final SendMailHandler sendMailHandler;

        @Inject
        public SMTPModuleConfigurationPerformer(ConfigurationProvider configurationProvider,
                SMTPServerFactory smtpServerFactory,
            SendMailHandler sendMailHandler) {
            this.configurationProvider = configurationProvider;
            this.smtpServerFactory = smtpServerFactory;
            this.sendMailHandler = sendMailHandler;
        }

        @Override
        public void initModule() {
            try {
                smtpServerFactory.setLog(LOGGER);
                smtpServerFactory.configure(configurationProvider.getConfiguration("smtpserver"));
                smtpServerFactory.init();
                sendMailHandler.init(null);
            } catch (Exception e) {
                Throwables.propagate(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(SMTPServerFactory.class);
        }
    }

}
