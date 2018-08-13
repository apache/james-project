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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.EnumSet;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.inject.Module;

public class JamesCapabilitiesServerTest {

    private GuiceJamesServer server;
    private TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public RuleChain chain = RuleChain.outerRule(temporaryFolder);

    @After
    public void teardown() {
        server.stop();
    }
    
    private GuiceJamesServer createJPAJamesServer(final MailboxManager mailboxManager) throws IOException {
        Module mockMailboxManager = (binder) -> binder.bind(MailboxManager.class).toInstance(mailboxManager);
        Configuration configuration = Configuration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .build();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(JPAJamesServerMain.JPA_SERVER_MODULE)
            .overrideWith(
                new TestJPAConfigurationModule(),
                mockMailboxManager);
    }
    
    @Test
    public void startShouldSucceedWhenRequiredCapabilities() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedMailboxCapabilities())
            .thenReturn(EnumSet.noneOf(MailboxManager.MailboxCapabilities.class));
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.noneOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.noneOf(MailboxManager.SearchCapabilities.class));

        server = createJPAJamesServer(mailboxManager);

        server.start();
    }

}
