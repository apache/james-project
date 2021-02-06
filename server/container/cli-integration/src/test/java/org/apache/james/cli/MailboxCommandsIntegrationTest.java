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

package org.apache.james.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.cli.util.OutputCapture;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.server.JMXServerModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MailboxCommandsIntegrationTest {
    public static final String USER = "user";
    public static final String MAILBOX = "mailboxExampleName";

    @RegisterExtension
    JamesServerExtension memoryJmap = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(conf -> MemoryJamesServerMain.createServer(conf)
            .overrideWith(new JMXServerModule(),
                binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class))))
        .build();
    private MailboxProbeImpl mailboxProbe;
    private OutputCapture outputCapture;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) {
        outputCapture = new OutputCapture();
        mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);
    }

    @Test
    void createMailboxShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "createmailbox", MailboxConstants.USER_NAMESPACE, USER, MAILBOX});

        assertThat(mailboxProbe.listUserMailboxes(USER)).containsOnly(MAILBOX);
    }

    @Test
    void deleteUserMailboxesShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "createmailbox", MailboxConstants.USER_NAMESPACE, USER, MAILBOX});

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "deleteusermailboxes", USER});

        assertThat(mailboxProbe.listUserMailboxes(USER)).isEmpty();
    }

    @Test
    void listUserMailboxesShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "createmailbox", MailboxConstants.USER_NAMESPACE, USER, MAILBOX});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "listusermailboxes", USER},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce(MAILBOX);
    }

    @Test
    void deleteMailboxeShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "createmailbox", MailboxConstants.USER_NAMESPACE, USER, MAILBOX});

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "deletemailbox", MailboxConstants.USER_NAMESPACE, USER, MAILBOX});

        assertThat(mailboxProbe.listUserMailboxes(USER)).isEmpty();
    }
}
