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

import jakarta.inject.Inject;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.events.EventListener;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.GuiceGenericLoader;

import com.google.common.base.Preconditions;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;

public class MailboxListenerFactory {

    public static class MailboxListenerBuilder {
        private final GuiceGenericLoader genericLoader;
        private Optional<ClassName> clazz;
        private Optional<EventListener.ExecutionMode> executionMode;
        private Optional<HierarchicalConfiguration<ImmutableNode>> configuration;

        public MailboxListenerBuilder(GuiceGenericLoader genericLoader) {
            this.genericLoader = genericLoader;
            this.clazz = Optional.empty();
            this.executionMode = Optional.empty();
            this.configuration = Optional.empty();
        }

        public MailboxListenerBuilder withExecutionMode(EventListener.ExecutionMode executionMode) {
            this.executionMode = Optional.of(executionMode);
            return this;
        }

        public MailboxListenerBuilder withConfiguration(HierarchicalConfiguration<ImmutableNode> configuration) {
            this.configuration = Optional.of(configuration);
            return this;
        }

        public MailboxListenerBuilder withExecutionMode(Optional<EventListener.ExecutionMode> executionMode) {
            executionMode.ifPresent(this::withExecutionMode);
            return this;
        }

        public MailboxListenerBuilder withConfiguration(Optional<HierarchicalConfiguration<ImmutableNode>> configuration) {
            configuration.ifPresent(this::withConfiguration);
            return this;
        }

        public MailboxListenerBuilder clazz(ClassName clazz) {
            this.clazz = Optional.of(clazz);
            return this;
        }

        public EventListener build() throws ClassNotFoundException {
            Preconditions.checkState(clazz.isPresent(), "'clazz' is mandatory");
            Module childModule = Modules.combine(
                binder -> binder.bind(EventListener.ExecutionMode.class)
                    .toInstance(executionMode.orElse(EventListener.ExecutionMode.SYNCHRONOUS)),
                binder -> binder.bind(new TypeLiteral<HierarchicalConfiguration<ImmutableNode>>() {})
                    .toInstance(configuration.orElse(new BaseHierarchicalConfiguration())));

            return genericLoader.<EventListener>withChildModule(childModule)
                .instantiate(clazz.get());
        }
    }

    private final GuiceGenericLoader genericLoader;

    @Inject
    public MailboxListenerFactory(GuiceGenericLoader genericLoader) {
        this.genericLoader = genericLoader;
    }

    public MailboxListenerBuilder newInstance() {
        return new MailboxListenerBuilder(genericLoader);
    }
}
