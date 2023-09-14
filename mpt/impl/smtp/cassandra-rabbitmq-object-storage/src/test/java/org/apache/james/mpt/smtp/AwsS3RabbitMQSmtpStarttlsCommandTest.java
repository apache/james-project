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

import static org.apache.james.modules.protocols.SmtpGuiceProbe.SmtpServerConnectedType.SMTP_START_TLS_SERVER;

import org.apache.james.CassandraRabbitMQJamesServerFixture;
import org.apache.james.JamesServerExtension;
import org.apache.james.blob.objectstorage.aws.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AwsS3RabbitMQSmtpStarttlsCommandTest extends SmtpStarttlsCommandTest {
    @RegisterExtension
    static JamesServerExtension testExtension = CassandraRabbitMQJamesServerFixture.baseExtensionBuilder()
            .extension(new AwsS3BlobStoreExtension())
            .extension(new InMemoryDnsExtension())
            .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
            .build();
    @RegisterExtension
    static SmtpTestExtension smtpTestExtension = new SmtpTestExtension(SMTP_START_TLS_SERVER, testExtension);
}
