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
package org.apache.james.protocols.smtp;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPSClient;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.netty.Encryption;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


public abstract class AbstractSMTPSServerTest extends AbstractSMTPServerTest {


    @Override
    protected SMTPClient createClient() {
        SMTPSClient client = new SMTPSClient(true,BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        return client;
    }


    @Override
    protected ProtocolServer createServer(Protocol protocol) {
        return createEncryptedServer(protocol, Encryption.createTls(BogusSslContextFactory.getServerContext()));
    }

    protected abstract ProtocolServer createEncryptedServer(Protocol protocol, Encryption enc);

    // The UTF-8 "accepted" tests use a raw TCP socket to control bytes on the
    // wire. That does not speak TLS, so skip those cases under SMTPS — the plain
    // variant covers the server-side logic. Rejection tests still work under
    // SMTPS via the regular SMTPClient path.
    @Override
    @Test
    @Disabled("Raw-socket UTF-8 test is not SSL-aware; covered by NettySMTPServerTest")
    void mailFromWithNonAsciiSenderShouldBeAcceptedWhenSmtpUtf8IsAsserted() {
    }

    @Override
    @Test
    @Disabled("Raw-socket UTF-8 test is not SSL-aware; covered by NettySMTPServerTest")
    void rcptToWithNonAsciiRecipientShouldBeAcceptedWhenSmtpUtf8IsAsserted() {
    }

    @Override
    @Test
    @Disabled("Raw-socket test is not SSL-aware; covered by NettySMTPServerTest")
    void mailFromWithAceLabelDomainShouldBeAcceptedWithoutSmtpUtf8() {
    }

    @Override
    @Test
    @Disabled("Raw-socket test is not SSL-aware; covered by NettySMTPServerTest")
    void rcptToWithAceLabelDomainShouldBeAcceptedWithoutSmtpUtf8() {
    }

    @Override
    @Test
    @Disabled("Raw-socket test is not SSL-aware; covered by NettySMTPServerTest")
    void mailFromWithMalformedAceLabelShouldBeRejected() {
    }

    @Override
    @Test
    @Disabled("Raw-socket test is not SSL-aware; covered by NettySMTPServerTest")
    void rcptToWithMalformedAceLabelShouldBeRejected() {
    }

    @Override
    @Test
    @Disabled("Raw-socket test is not SSL-aware; covered by NettySMTPServerTest")
    void aceLabelDomainsShouldBeExposedToHooksAsUnicode() {
    }
}
