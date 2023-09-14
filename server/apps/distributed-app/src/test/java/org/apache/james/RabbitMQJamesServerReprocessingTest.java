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

import static io.restassured.config.ParamConfig.UpdateStrategy.REPLACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_MINUTE;

import org.apache.james.blob.objectstorage.aws.AwsS3BlobStoreExtension;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import io.restassured.config.ParamConfig;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;

class RabbitMQJamesServerReprocessingTest {
    private static final ConditionFactory AWAIT = Awaitility.await().atMost(ONE_MINUTE);
    private static final MailRepositoryUrl SENDER_DENIED = MailRepositoryUrl.from("cassandra://var/mail/sender-denied/");
    private RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();
    private RequestSpecification webAdminApi;

    @RegisterExtension
    JamesServerExtension jamesServerExtension = CassandraRabbitMQJamesServerFixture
        .baseExtensionBuilder(rabbitMQExtension)
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.TEST_CONFIGURATION)))
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer server) {
        RestAssured.defaultParser = Parser.JSON;
        webAdminApi = WebAdminUtils.spec(server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort())
            .config(WebAdminUtils.defaultConfig()
                .paramConfig(new ParamConfig(REPLACE, REPLACE, REPLACE)));
    }

    @Test
    void reprocessingADeniedMailShouldNotLooseIt(GuiceJamesServer server) throws Exception {
        try (SMTPMessageSender smtpMessageSender = new SMTPMessageSender("other.com")) {
            smtpMessageSender
                .connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .sendMessage("denied@other.com", "any@domain.tld");
        }

        MailRepositoryProbeImpl mailRepositoryProbe = server.getProbe(MailRepositoryProbeImpl.class);
        AWAIT.until(() -> mailRepositoryProbe.listMailKeys(SENDER_DENIED).size() == 1);

        String taskId = webAdminApi
            .param("action", "reprocess")
            .patch("/mailRepositories/var%2Fmail%2Fsender-denied/mails")
            .jsonPath()
            .get("taskId");

        // Awaiting the task ensure the reprocessdid start and that the repository was emptied
        webAdminApi.get("/tasks/" + taskId + "/await");

        // Awaiting that an other mail is present in the mail repository ensures that the reprocessing successfully finished
        AWAIT.until(() -> mailRepositoryProbe.listMailKeys(SENDER_DENIED).size() == 1);
        assertThat(mailRepositoryProbe.listMailKeys(SENDER_DENIED)).hasSize(1);
    }
}
