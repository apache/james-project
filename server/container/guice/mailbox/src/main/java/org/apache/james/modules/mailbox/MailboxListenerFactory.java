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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.mailbox.MailboxListener;

import com.google.common.base.Preconditions;
import com.google.inject.Injector;

public class MailboxListenerFactory {

    public static class MailboxListenerBuilder {
        private final Injector injector;
        private Optional<Class<MailboxListener>> clazz;
        private Optional<MailboxListener.ExecutionMode> executionMode;
        private Optional<HierarchicalConfiguration> configuration;

        public MailboxListenerBuilder(Injector injector) {
            this.injector = injector;
            this.clazz = Optional.empty();
            this.executionMode = Optional.empty();
            this.configuration = Optional.empty();
        }

        public MailboxListenerBuilder withExecutionMode(MailboxListener.ExecutionMode executionMode) {
            this.executionMode = Optional.of(executionMode);
            return this;
        }

        public MailboxListenerBuilder withConfiguration(HierarchicalConfiguration configuration) {
            this.configuration = Optional.of(configuration);
            return this;
        }

        public MailboxListenerBuilder withExecutionMode(Optional<MailboxListener.ExecutionMode> executionMode) {
            executionMode.ifPresent(this::withExecutionMode);
            return this;
        }

        public MailboxListenerBuilder withConfiguration(Optional<HierarchicalConfiguration> configuration) {
            configuration.ifPresent(this::withConfiguration);
            return this;
        }

        public MailboxListenerBuilder clazz(Class<MailboxListener> clazz) {
            this.clazz = Optional.of(clazz);
            return this;
        }

        public MailboxListener build() {
            Preconditions.checkState(clazz.isPresent(), "'clazz' is mandatory");
            return injector.createChildInjector(
                binder -> binder.bind(MailboxListener.ExecutionMode.class)
                    .toInstance(executionMode.orElse(MailboxListener.ExecutionMode.SYNCHRONOUS)),
                binder -> binder.bind(HierarchicalConfiguration.class)
                    .toInstance(configuration.orElse(new HierarchicalConfiguration())))
                .getInstance(clazz.get());
        }
    }

    private final Injector injector;

    @Inject
    public MailboxListenerFactory(Injector injector) {
        this.injector = injector;
    }

    public MailboxListenerBuilder newInstance() {
        return new MailboxListenerBuilder(injector);
    }
}
