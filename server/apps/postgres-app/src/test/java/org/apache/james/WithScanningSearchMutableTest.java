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

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.PostgresJamesConfiguration.EventBusImpl;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.junit.jupiter.api.extension.RegisterExtension;

public class WithScanningSearchMutableTest implements MailsShouldBeWellReceived {
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.scanning())
            .usersRepository(DEFAULT)
            .eventBusImpl(EventBusImpl.IN_MEMORY)
            .build())
        .server(PostgresJamesServerMain::createServer)
        .extension(postgresExtension)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_TEST)
        .build();

    @Override
    public int imapPort(GuiceJamesServer server) {
        return server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    @Override
    public int smtpPort(GuiceJamesServer server) {
        return server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue();
    }
}
