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

package org.apache.james.mpt.smtp;

import static org.apache.james.modules.protocols.SmtpGuiceProbe.SmtpServerConnectedType.SMTP_GLOBAL_SERVER;

import org.apache.james.JamesServerExtension;
import org.apache.james.Main;
import org.apache.james.PostgresExtension;
import org.apache.james.PulsarExtension;
import org.apache.james.TestingSmtpRelayJamesServerBuilder;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
//FIXME rename this test
public class CassandraPulsarForwardSmtpTest implements ForwardSmtpTest {

    @Order(1)
    @RegisterExtension
    static JamesServerExtension testExtension = TestingSmtpRelayJamesServerBuilder.forConfiguration(c -> c)
            .extension(new PostgresExtension())
            .extension(new PulsarExtension())
            .extension(new AwsS3BlobStoreExtension())
            .extension(new InMemoryDnsExtension())
            .server(Main::createServer)
            .lifeCycle(JamesServerExtension.Lifecycle.PER_TEST)
            .build();

    @Order(2)
    @RegisterExtension
    static SmtpTestExtension smtpTestExtension = new SmtpTestExtension(SMTP_GLOBAL_SERVER, testExtension);
}
