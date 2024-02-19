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

import org.apache.james.util.Port;
import org.subethamail.smtp.server.SMTPServer;

class MockSMTPServer {
    private static final int RANDOM_PORT = 0;

    public static MockSMTPServer onRandomPort(SMTPBehaviorRepository behaviorRepository, ReceivedMailRepository mailRepository) {
        return new MockSMTPServer(behaviorRepository, mailRepository, RANDOM_PORT);
    }

    public static MockSMTPServer onPort(SMTPBehaviorRepository behaviorRepository, ReceivedMailRepository mailRepository, Port port) {
        return new MockSMTPServer(behaviorRepository, mailRepository, port.getValue());
    }

    private final SMTPServer server;

    private MockSMTPServer(SMTPBehaviorRepository behaviorRepository, ReceivedMailRepository mailRepository, int port) {
        this.server = SMTPServer.port(port).messageHandlerFactory(ctx -> new MockMessageHandler(mailRepository, behaviorRepository)).build();
        this.server.getCommandHandler().addCommand(new ExtendedEhloCommand(behaviorRepository));
        this.server.getCommandHandler().addCommand(new ExtendedMailFromCommand());
        this.server.getCommandHandler().addCommand(new ExtendedRcptToCommand());
    }

    void start() {
        if (!server.isRunning()) {
           server.start();
        }
    }

    Port getPort() {
        return Port.of(server.getPortAllocated());
    }

    void stop() {
        server.stop();
    }
}
