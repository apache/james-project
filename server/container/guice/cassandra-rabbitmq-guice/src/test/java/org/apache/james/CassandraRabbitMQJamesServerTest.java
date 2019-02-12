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
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.junit.jupiter.api.TestInstance.Lifecycle;

import org.apache.james.blob.objectstorage.AESPayloadCodec;
import org.apache.james.blob.objectstorage.DefaultPayloadCodec;
import org.apache.james.blob.objectstorage.PayloadCodec;
import org.apache.james.core.Domain;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.SwiftBlobStoreExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.objectstorage.PayloadCodecFactory;
import org.apache.james.modules.objectstorage.guice.DockerSwiftTestRule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraRabbitMQJamesServerTest {

    interface MailsShouldBeWellReceived {
        String JAMES_SERVER_HOST = "127.0.0.1";

        @Test
        default void mailsShouldBeWellReceived(GuiceJamesServer server) throws Exception {
            server.getProbe(DataProbeImpl.class).fluent()
                .addDomain(DOMAIN)
                .addUser(JAMES_USER, PASSWORD);

            try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
                sender.connect(JAMES_SERVER_HOST, server.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                    .sendMessage("bob@any.com", JAMES_USER);
            }

            CALMLY_AWAIT.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

            try (IMAPMessageReader reader = new IMAPMessageReader()) {
                reader.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
                    .login(JAMES_USER, PASSWORD)
                    .select(IMAPMessageReader.INBOX)
                    .awaitMessage(CALMLY_AWAIT);
            }
        }
    }

    interface ContractSuite extends JmapJamesServerContract, MailsShouldBeWellReceived, JamesServerContract {}

    private static final String DOMAIN = "domain";
    private static final String JAMES_USER = "james-user@" + DOMAIN;
    private static final String PASSWORD = "secret";
    private static final int LIMIT_TO_10_MESSAGES = 10;

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    private static final JamesServerExtensionBuilder.ServerProvider CONFIGURATION_BUILDER =
        configuration -> GuiceJamesServer
            .forConfiguration(configuration)
            .combineWith(CassandraRabbitMQJamesServerMain.MODULES)
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
            .overrideWith(JmapJamesServerContract.DOMAIN_LIST_CONFIGURATION_MODULE);

    @Nested
    @TestInstance(Lifecycle.PER_METHOD)
    class WithEncryptedSwift implements ContractSuite {
        @RegisterExtension
        JamesServerExtension testExtension = baseExtensionBuilder()
            .extension(new SwiftBlobStoreExtension(PayloadCodecFactory.AES256))
            .server(CONFIGURATION_BUILDER)
            .build();

        @Test
        void encryptedPayloadShouldBeConfiguredWhenProvidingEncryptedPayloadConfiguration(GuiceJamesServer jamesServer) {
            PayloadCodec payloadCodec = jamesServer.getProbe(DockerSwiftTestRule.TestSwiftBlobStoreProbe.class)
                .getSwiftPayloadCodec();

            assertThat(payloadCodec)
                .isInstanceOf(AESPayloadCodec.class);
        }
    }

    @Nested
    @TestInstance(Lifecycle.PER_METHOD)
    class WithDefaultSwift implements ContractSuite {
        @RegisterExtension
        JamesServerExtension testExtension = baseExtensionBuilder()
            .extension(new SwiftBlobStoreExtension())
            .build();

        @Test
        void defaultPayloadShouldBeByDefault(GuiceJamesServer jamesServer) {
            PayloadCodec payloadCodec = jamesServer.getProbe(DockerSwiftTestRule.TestSwiftBlobStoreProbe.class)
                .getSwiftPayloadCodec();

            assertThat(payloadCodec)
                .isInstanceOf(DefaultPayloadCodec.class);
        }
    }

    @Nested
    @TestInstance(Lifecycle.PER_METHOD)
    class WithoutSwift implements ContractSuite {
        @RegisterExtension
        JamesServerExtension testExtension = baseExtensionBuilder().build();
    }

    private static JamesServerExtensionBuilder baseExtensionBuilder() {
        return new JamesServerExtensionBuilder()
            .extension(new EmbeddedElasticSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .server(CONFIGURATION_BUILDER);
    }
}
