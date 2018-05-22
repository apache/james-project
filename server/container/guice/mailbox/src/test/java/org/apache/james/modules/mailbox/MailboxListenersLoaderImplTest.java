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
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.store.event.MailboxListenerRegistry;
import org.apache.james.utils.ExtendedClassLoader;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;

public class MailboxListenersLoaderImplTest {

    private MailboxListenerRegistry registry;
    private MailboxListenersLoaderImpl testee;

    @Before
    public void setup() throws Exception {
        FileSystem fileSystem = mock(FileSystem.class);
        when(fileSystem.getFile(anyString()))
            .thenThrow(new FileNotFoundException());

        registry = new MailboxListenerRegistry();
        testee = new MailboxListenersLoaderImpl(new MailboxListenerFactory(Guice.createInjector()), registry,
            new ExtendedClassLoader(fileSystem), ImmutableSet.of());
    }

    @Test
    public void createListenerShouldThrowWhenClassCantBeLoaded() {
        ListenerConfiguration configuration = ListenerConfiguration.forClass("MyUnknownClass");

        assertThatThrownBy(() -> testee.createListener(configuration))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void createListenerShouldThrowWhenClassCantBeCastToMailboxListener() {
        ListenerConfiguration configuration = ListenerConfiguration.forClass("java.lang.String");

        assertThatThrownBy(() -> testee.createListener(configuration))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void createListenerShouldThrowWhenNotFullClassName() {
        ListenerConfiguration configuration = ListenerConfiguration.forClass("NoopMailboxListener");

        assertThatThrownBy(() -> testee.createListener(configuration))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void createListenerShouldReturnMailboxListenerWhenConfigurationIsGood() {
        ListenerConfiguration configuration = ListenerConfiguration.forClass("org.apache.james.modules.mailbox.NoopMailboxListener");

        MailboxListener listener = testee.createListener(configuration);

        assertThat(listener).isInstanceOf(NoopMailboxListener.class);
    }

    @Test
    public void configureShouldAddMailboxListenersWhenConfigurationIsGood() throws ConfigurationException {
        DefaultConfigurationBuilder configuration = toConfigutation("<listeners>" +
                    "<listener>" +
                        "<class>org.apache.james.modules.mailbox.NoopMailboxListener</class>" +
                    "</listener>" +
                    "<listener>" +
                        "<class>org.apache.james.modules.mailbox.NoopMailboxListener</class>" +
                    "</listener>" +
                "</listeners>");

        testee.configure(configuration);

        assertThat(registry.getGlobalListeners()).hasSize(2);
    }

    private DefaultConfigurationBuilder toConfigutation(String configurationString) throws ConfigurationException {
        DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder();
        configuration.load(new ByteArrayInputStream(configurationString.getBytes(StandardCharsets.UTF_8)));
        return configuration;
    }
}
