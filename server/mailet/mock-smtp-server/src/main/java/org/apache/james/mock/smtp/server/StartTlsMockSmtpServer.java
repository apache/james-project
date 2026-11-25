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

package org.apache.james.mock.smtp.server;

import javax.net.ssl.SSLContext;

import org.apache.james.util.Port;

/**
 * In-process mock SMTP server accepting STARTTLS, for tests needing an actual TLS session, that the docker image
 * does not offer. STARTTLS is announced only when configured as an SMTP extension, like any other extension.
 */
public class StartTlsMockSmtpServer {
    public static StartTlsMockSmtpServer start(SSLContext sslContext) {
        SMTPBehaviorRepository behaviorRepository = new SMTPBehaviorRepository();
        ReceivedMailRepository receivedMailRepository = new ReceivedMailRepository();

        HTTPConfigurationServer.RunningStage httpServer = HTTPConfigurationServer.onRandomPort(behaviorRepository, receivedMailRepository).start();
        MockSMTPServer smtpServer = MockSMTPServer.onRandomPortWithStartTls(behaviorRepository, receivedMailRepository, sslContext);
        smtpServer.start();
        return new StartTlsMockSmtpServer(httpServer, smtpServer);
    }

    private final HTTPConfigurationServer.RunningStage httpServer;
    private final MockSMTPServer smtpServer;

    private StartTlsMockSmtpServer(HTTPConfigurationServer.RunningStage httpServer, MockSMTPServer smtpServer) {
        this.httpServer = httpServer;
        this.smtpServer = smtpServer;
    }

    public ConfigurationClient getConfigurationClient() {
        return ConfigurationClient.fromServer(httpServer);
    }

    public Port getSmtpPort() {
        return smtpServer.getPort();
    }

    public void stop() {
        smtpServer.stop();
        httpServer.stop();
    }
}
