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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.events.GenericGroup;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.utils.ExtendedClassLoader;
import org.apache.james.utils.GuiceGenericLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class MailboxListenersLoaderImplTest {

    private InVMEventBus eventBus;
    private MailboxListenersLoaderImpl testee;

    @BeforeEach
    void setup() throws Exception {
        FileSystem fileSystem = mock(FileSystem.class);
        when(fileSystem.getFile(anyString()))
            .thenThrow(new FileNotFoundException());

        eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());

        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(fileSystem));
        testee = new MailboxListenersLoaderImpl(new MailboxListenerFactory(genericLoader), eventBus, ImmutableSet.of(), ImmutableSet.of());
    }

    @Test
    void deserializeNoopMailboxListenerGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.modules.mailbox.NoopMailboxListener$NoopMailboxListenerGroup"))
            .isEqualTo(new NoopMailboxListener.NoopMailboxListenerGroup());
    }

    @Test
    void createListenerShouldThrowWhenClassCantBeLoaded() {
        ListenerConfiguration configuration = ListenerConfiguration.forClass("MyUnknownClass");

        assertThatThrownBy(() -> testee.createListener(configuration))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void createListenerShouldThrowWhenClassCantBeCastToMailboxListener() {
        ListenerConfiguration configuration = ListenerConfiguration.forClass("java.lang.String");

        assertThatThrownBy(() -> testee.createListener(configuration))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void createListenerShouldThrowWhenNotFullClassName() {
        ListenerConfiguration configuration = ListenerConfiguration.forClass("NoopMailboxListener");

        assertThatThrownBy(() -> testee.createListener(configuration))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void createListenerShouldReturnMailboxListenerWhenConfigurationIsGood() {
        ListenerConfiguration configuration = ListenerConfiguration.forClass("org.apache.james.modules.mailbox.ReactiveNoopMailboxListener");

        Pair<Group, EventListener.ReactiveEventListener> listener = testee.createListener(configuration);

        assertThat(listener.getRight()).isInstanceOf(ReactiveNoopMailboxListener.class);
    }

    @Test
    void configureShouldAddMailboxListenersWhenConfigurationIsGood() throws Exception {
        XMLConfiguration configuration = toConfigutation("<listeners>" +
                    "<listener>" +
                        "<class>org.apache.james.modules.mailbox.NoopMailboxListener</class>" +
                    "</listener>" +
                "</listeners>");

        testee.configure(configuration);

        assertThat(eventBus.registeredGroups()).containsExactly(NoopMailboxListener.GROUP);
    }

    @Test
    void configurationShouldBeOptional() throws Exception {
        XMLConfiguration configuration = toConfigutation("<listeners>" +
                    "<listener>" +
                        "<class>org.apache.james.modules.mailbox.ConfiguredListener</class>" +
                    "</listener>" +
                "</listeners>");

        testee.configure(configuration);

        assertThat(ConfiguredListener.value).isNull();
    }

    @Test
    void configurationShouldBeTakenIntoAccount() throws Exception {
        ConfiguredListener.value = "v1";

        XMLConfiguration configuration = toConfigutation("<listeners>" +
                    "<listener>" +
                        "<class>org.apache.james.modules.mailbox.ConfiguredListener</class>" +
                        "<configuration>" +
                            "<value>v2</value>>" +
                        "</configuration>" +
                    "</listener>" +
                "</listeners>");

        testee.configure(configuration);

        assertThat(ConfiguredListener.value).isEqualTo("v2");
    }

    @Test
    void customGroupCanBePassed() throws Exception {
        XMLConfiguration configuration = toConfigutation("<listeners>" +
                    "<listener>" +
                        "<class>org.apache.james.modules.mailbox.NoopMailboxListener</class>" +
                        "<group>Avengers</group>" +
                    "</listener>" +
                "</listeners>");

        testee.configure(configuration);

        assertThat(eventBus.registeredGroups()).containsExactly(new GenericGroup("Avengers"));
    }

    @Test
    void aListenerCanBeRegisteredOnSeveralGroups() throws Exception {
        XMLConfiguration configuration = toConfigutation("<listeners>" +
                    "<listener>" +
                        "<class>org.apache.james.modules.mailbox.NoopMailboxListener</class>" +
                        "<group>Avengers</group>" +
                    "</listener>" +
                    "<listener>" +
                        "<class>org.apache.james.modules.mailbox.NoopMailboxListener</class>" +
                        "<group>Fantastic 4</group>" +
                    "</listener>" +
                "</listeners>");

        testee.configure(configuration);

        assertThat(eventBus.registeredGroups()).containsExactlyInAnyOrder(new GenericGroup("Avengers"), new GenericGroup("Fantastic 4"));
    }

    private XMLConfiguration toConfigutation(String configurationString) throws ConfigurationException, IOException {
        return FileConfigurationProvider
            .getConfig(new ByteArrayInputStream(configurationString.getBytes(StandardCharsets.UTF_8)));
    }
}
