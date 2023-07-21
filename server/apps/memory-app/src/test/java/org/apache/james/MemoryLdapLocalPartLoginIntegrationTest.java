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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.MailsShouldBeWellReceived.JAMES_SERVER_HOST;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.LDAP;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.user.ldap.DockerLdapSingleton.JAMES_USER;
import static org.apache.james.user.ldap.DockerLdapSingleton.PASSWORD;
import static org.apache.james.user.ldap.ReadOnlyUsersLDAPRepositoryWithLocalPartAsLoginNameTest.JAMES_USER_APP1;
import static org.apache.james.user.ldap.ReadOnlyUsersLDAPRepositoryWithLocalPartAsLoginNameTest.JAMES_USER_WITH_DOMAIN_PART;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Base64;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.data.DockerLdapRule;
import org.apache.james.data.LdapTestExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MemoryLdapLocalPartLoginIntegrationTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(LDAP)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .extension(new LdapTestExtension(new DockerLdapRule(true)))
        .build();

    @Nested
    class IMAP {
        @Test
        void imapFQDNLoginShouldSucceed(GuiceJamesServer server) throws IOException {
            IMAPClient imapClient = new IMAPClient();
            imapClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort());

            assertThat(imapClient.login(JAMES_USER_WITH_DOMAIN_PART.asString(), PASSWORD)).isTrue();
        }

        @Test
        void imapLoginUsingValidLocalPartCredentialShouldSucceed(GuiceJamesServer server) throws IOException {
            IMAPClient imapClient = new IMAPClient();
            imapClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort());

            assertThat(imapClient.login(JAMES_USER.getLocalPart(), PASSWORD)).isTrue();
        }

        @Test
        void imapLoginUsingInvalidLocalPartCredentialShouldFail(GuiceJamesServer server) throws IOException {
            IMAPClient imapClient = new IMAPClient();
            imapClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort());

            assertThat(imapClient.login(JAMES_USER.getLocalPart(), "wrongPassword")).isFalse();
        }

        @Test
        void imapLoginUsingValidAppCredentialShouldSucceed(GuiceJamesServer server) throws IOException {
            IMAPClient imapClient = new IMAPClient();
            imapClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort());

            assertThat(imapClient.login(JAMES_USER_APP1.asString(), PASSWORD)).isTrue();
        }

        @Test
        void imapLoginUsingInvalidAppCredentialShouldSucceed(GuiceJamesServer server) throws IOException {
            IMAPClient imapClient = new IMAPClient();
            imapClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort());

            assertThat(imapClient.login(JAMES_USER_APP1.asString(), "wrongPassword")).isFalse();
        }
    }

    @Nested
    class SMTP {
        @Test
        void smtpFQDNAuthPlainShouldSucceed(GuiceJamesServer server) throws Exception {
            SMTPClient smtpClient = new SMTPClient();
            smtpClient.connect(LOCALHOST_IP, server.getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort().getValue());

            authenticate(smtpClient, JAMES_USER_WITH_DOMAIN_PART.asString(), PASSWORD);

            assertThat(smtpClient.getReplyCode())
                .as("authenticated")
                .isEqualTo(235);
        }

        @Test
        void smtpAuthPlainUsingValidLocalPartCredentialShouldSucceed(GuiceJamesServer server) throws Exception {
            SMTPClient smtpClient = new SMTPClient();
            smtpClient.connect(LOCALHOST_IP, server.getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort().getValue());

            authenticate(smtpClient, JAMES_USER.getLocalPart(), PASSWORD);

            assertThat(smtpClient.getReplyCode())
                .as("authenticated")
                .isEqualTo(235);
        }

        @Test
        void smtpAuthPlainUsingInvalidLocalPartCredentialShouldFail(GuiceJamesServer server) throws Exception {
            SMTPClient smtpClient = new SMTPClient();
            smtpClient.connect(LOCALHOST_IP, server.getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort().getValue());

            authenticate(smtpClient, JAMES_USER.getLocalPart(), "wrongPassword");

            assertThat(smtpClient.getReplyCode())
                .as("authentication rejected")
                .isEqualTo(535);
        }

        @Test
        void smtpAuthPlainUsingValidAppCredentialShouldSucceed(GuiceJamesServer server) throws Exception {
            SMTPClient smtpClient = new SMTPClient();
            smtpClient.connect(LOCALHOST_IP, server.getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort().getValue());

            authenticate(smtpClient, JAMES_USER_APP1.asString(), PASSWORD);

            assertThat(smtpClient.getReplyCode())
                .as("authenticated")
                .isEqualTo(235);
        }

        @Test
        void smtpAuthPlainUsingInvalidAppCredentialShouldFail(GuiceJamesServer server) throws Exception {
            SMTPClient smtpClient = new SMTPClient();
            smtpClient.connect(LOCALHOST_IP, server.getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort().getValue());

            authenticate(smtpClient, JAMES_USER_APP1.asString(), "wrongPassword");

            assertThat(smtpClient.getReplyCode())
                .as("authentication rejected")
                .isEqualTo(535);
        }

        private void authenticate(SMTPClient smtpClient, String loginName, String password) throws IOException {
            smtpClient.sendCommand("AUTH PLAIN");
            smtpClient.sendCommand(Base64.getEncoder().encodeToString(("\0" + loginName + "\0" + password + "\0").getBytes(UTF_8)));
        }
    }

}
