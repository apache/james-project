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

package org.apache.james.modules.event;

import java.util.List;

import org.apache.james.event.json.EventSerializer;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.events.RabbitMQEventBus;
import org.apache.james.mailbox.events.RegistrationKey;
import org.apache.james.mailbox.events.RetryBackoffConfiguration;
import org.apache.james.utils.ConfigurationPerformer;
import org.parboiled.common.ImmutableList;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class RabbitMQEventBusModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(EventSerializer.class).in(Scopes.SINGLETON);

        bind(RabbitMQEventBus.class).in(Scopes.SINGLETON);
        bind(EventBus.class).to(RabbitMQEventBus.class);

        Multibinder.newSetBinder(binder(), RegistrationKey.Factory.class)
            .addBinding().to(MailboxIdRegistrationKey.Factory.class);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class)
            .addBinding().to(RabbitMQEventBusInitializer.class);

        bind(RetryBackoffConfiguration.class).toInstance(RetryBackoffConfiguration.DEFAULT);
    }

    static class RabbitMQEventBusInitializer implements ConfigurationPerformer {
        private final RabbitMQEventBus rabbitMQEventBus;

        @Inject
        RabbitMQEventBusInitializer(RabbitMQEventBus rabbitMQEventBus) {
            this.rabbitMQEventBus = rabbitMQEventBus;
        }

        @Override
        public void initModule() {
            rabbitMQEventBus.start();
        }

        @Override
        public List<Class<? extends Startable>> forClasses() {
            return ImmutableList.of(RabbitMQEventBus.class);
        }
    }
}
