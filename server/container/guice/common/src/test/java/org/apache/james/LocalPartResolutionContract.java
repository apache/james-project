/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.Pop3GuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.Test;

public interface LocalPartResolutionContract {

    String JAMES_SERVER_HOST = "127.0.0.1";
    String DOMAIN = "apache.org";
    String JAMES_USER = "james-user@" + DOMAIN;
    String PASSWORD = "secret";

    @Test
    default void imapShouldAcceptLocalPart(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        try (TestIMAPClient reader = new TestIMAPClient()) {
            int imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();

            assertThatCode(() -> reader.connect(JAMES_SERVER_HOST, imapPort)
                    .rawLogin("james-user", PASSWORD))
                .doesNotThrowAnyException();
        }
    }

    @Test
    default void pop3ShouldAcceptLocalPart(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());

        assertThat(pop3Client.login("james-user", PASSWORD))
            .isTrue();
    }

    @Test
    default void smtpShouldAcceptLocalPart(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient();
        smtpClient.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());

        assertThat(smtpClient.auth(AuthenticatingSMTPClient.AUTH_METHOD.PLAIN, "james-user", PASSWORD))
            .isTrue();
    }
}
