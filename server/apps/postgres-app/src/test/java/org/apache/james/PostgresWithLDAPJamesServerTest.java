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

import static org.apache.james.MailsShouldBeWellReceived.JAMES_SERVER_HOST;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.LDAP;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.data.LdapTestExtension;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.apache.james.PostgresJamesConfiguration.EventBusImpl;
class PostgresWithLDAPJamesServerTest {
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.openSearch())
            .usersRepository(LDAP)
            .eventBusImpl(EventBusImpl.IN_MEMORY)
            .build())
        .server(PostgresJamesServerMain::createServer)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .extension(new LdapTestExtension())
        .extension(new DockerOpenSearchExtension())
        .extension(postgresExtension)
        .build();


    @Test
    void userFromLdapShouldLoginViaImapProtocol(GuiceJamesServer server) throws IOException {
        IMAPClient imapClient = new IMAPClient();
        imapClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort());

        assertThat(imapClient.login(DockerLdapSingleton.JAMES_USER.asString(), DockerLdapSingleton.PASSWORD)).isTrue();
    }
}
