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

package org.apache.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.mailbox.ListenersConfiguration;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.reactivestreams.Publisher;

import com.google.inject.multibindings.Multibinder;

import reactor.core.publisher.Mono;

class DisabledGroupExecutionTest {

    public static class ReactiveNoopMailboxListener implements EventListener.ReactiveGroupEventListener {
        public static class NoopMailboxListenerGroup extends Group {

        }

        static final Group GROUP = new NoopMailboxListenerGroup();

        private final AtomicBoolean executed = new AtomicBoolean(false);

        @Override
        public Group getDefaultGroup() {
            return GROUP;
        }

        @Override
        public Publisher<Void> reactiveEvent(Event event) {
            return Mono.fromRunnable(() -> executed.set(true));
        }

        @Override
        public boolean isHandling(Event event) {
            return true;
        }

        @Override
        public void event(Event event) {
            Mono.from(reactiveEvent(event)).block();
        }

        public boolean isExecuted() {
            return executed.get();
        }
    }

    static ReactiveNoopMailboxListener listener = new ReactiveNoopMailboxListener();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, EventListener.ReactiveGroupEventListener.class)
                .addBinding()
                .toInstance(listener))
            .overrideWith(binder -> binder.bind(ListenersConfiguration.class)
                .toInstance(ListenersConfiguration.disabled())))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        DataProbeImpl dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.fluent()
            .addDomain(DOMAIN)
            .addUser(ALICE.asString(), ALICE_PASSWORD)
            .addUser(BOB.asString(), BOB_PASSWORD);
    }

    @Test
    void listenerShouldNotBeExecutedWhenDisabled(GuiceJamesServer server) {
        server.getProbe(MailboxProbeImpl.class)
            .createMailbox(MailboxPath.inbox(ALICE));

        assertThat(listener.isExecuted()).isFalse();
    }
}
