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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;

import org.apache.james.jmap.JMAPModule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JamesCapabilitiesServerTest {
    private static final MailboxManager mailboxManager = mock(MailboxManager.class);

    @RegisterExtension
    static JamesServerExtension testExtension = TestingDistributedJamesServerBuilder.withSearchConfiguration(SearchConfiguration.openSearch())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .server(configuration -> CassandraJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(MailboxManager.class).toInstance(mailboxManager)))
        .disableAutoStart()
        .build();

    @Test
    void startShouldFailWhenNoMoveCapability(GuiceJamesServer server) {
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)).thenReturn(false);
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.ACL)).thenReturn(true);
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));
        
        assertThatThrownBy(server::start)
            .isInstanceOfSatisfying(
                StartUpChecksPerformer.StartUpChecksException.class,
                exception -> assertThat(exception.badCheckNames())
                    .containsOnly(JMAPModule.RequiredCapabilitiesStartUpCheck.CHECK_NAME));
    }
    
    @Test
    void startShouldFailWhenNoACLCapability(GuiceJamesServer server) {
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)).thenReturn(true);
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.ACL)).thenReturn(false);
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));
        
        assertThatThrownBy(server::start)
            .isInstanceOfSatisfying(
                StartUpChecksPerformer.StartUpChecksException.class,
                exception -> assertThat(exception.badCheckNames())
                    .containsOnly(JMAPModule.RequiredCapabilitiesStartUpCheck.CHECK_NAME));
    }
    
    @Test
    void startShouldFailWhenNoAttachmentSearchCapability(GuiceJamesServer server) {
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)).thenReturn(true);
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.ACL)).thenReturn(true);
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.SearchCapabilities.Attachment)));

        assertThatThrownBy(server::start)
            .isInstanceOfSatisfying(
                StartUpChecksPerformer.StartUpChecksException.class,
                exception -> assertThat(exception.badCheckNames())
                    .containsOnly(JMAPModule.RequiredCapabilitiesStartUpCheck.CHECK_NAME));
    }

    @Test
    void startShouldFailWhenNoAttachmentFileNameSearchCapability(GuiceJamesServer server) {
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)).thenReturn(true);
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.ACL)).thenReturn(true);
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.SearchCapabilities.AttachmentFileName)));

        assertThatThrownBy(server::start)
            .isInstanceOfSatisfying(
                StartUpChecksPerformer.StartUpChecksException.class,
                exception -> assertThat(exception.badCheckNames())
                    .containsOnly(JMAPModule.RequiredCapabilitiesStartUpCheck.CHECK_NAME));
    }
    
    @Test
    void startShouldFailWhenNoMultimailboxSearchCapability(GuiceJamesServer server) {
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)).thenReturn(true);
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.ACL)).thenReturn(true);
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.SearchCapabilities.MultimailboxSearch)));

        assertThatThrownBy(server::start)
            .isInstanceOfSatisfying(
                StartUpChecksPerformer.StartUpChecksException.class,
                exception -> assertThat(exception.badCheckNames())
                    .containsOnly(JMAPModule.RequiredCapabilitiesStartUpCheck.CHECK_NAME));
    }

    @Test
    void startShouldFailWhenNoUniqueIDCapability(GuiceJamesServer server) {
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)).thenReturn(true);
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.ACL)).thenReturn(true);
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.MessageCapabilities.UniqueID)));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));

        assertThatThrownBy(server::start)
            .isInstanceOfSatisfying(
                StartUpChecksPerformer.StartUpChecksException.class,
                exception -> assertThat(exception.badCheckNames())
                    .containsOnly(JMAPModule.RequiredCapabilitiesStartUpCheck.CHECK_NAME));
    }

    @Test
    void startShouldSucceedWhenRequiredCapabilities(GuiceJamesServer server) throws Exception {
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)).thenReturn(true);
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.ACL)).thenReturn(true);
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));

        assertThatCode(server::start)
            .doesNotThrowAnyException();

        assertThat(server.isStarted())
            .isTrue();
    }

}
