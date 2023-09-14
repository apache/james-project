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

package org.apache.james.webadmin.integration.rabbitmq;

import static io.restassured.RestAssured.given;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.util.Optional;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.blob.objectstorage.aws.AwsS3BlobStoreExtension;
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.healthcheck.MailReceptionCheck;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;

@Tag(BasicFeature.TAG)
class MailReceptionCheckIntegrationTest {
    private static final RabbitMQExtension RABBIT_MQ_EXTENSION = new RabbitMQExtension();
    public static final CassandraExtension CASSANDRA_EXTENSION = new CassandraExtension();

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                    .s3()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig())
            .searchConfiguration(SearchConfiguration.openSearch())
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(CASSANDRA_EXTENSION)
        .extension(new AwsS3BlobStoreExtension())
        .extension(RABBIT_MQ_EXTENSION)
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(binder -> binder.bind(MailReceptionCheck.Configuration.class)
                .toInstance(new MailReceptionCheck.Configuration(
                    Optional.of(ALICE), Duration.ofSeconds(10))))
            // Enforce a single eventBus retry. Required as Current Quotas are handled by the eventBus.
            .overrideWith(binder -> binder.bind(RetryBackoffConfiguration.class)
                .toInstance(RetryBackoffConfiguration.builder()
                    .maxRetries(1)
                    .firstBackoff(Duration.ofMillis(2))
                    .jitterFactor(0.5)
                    .build())))
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        guiceJamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(ALICE.asString(), ALICE_PASSWORD);

        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
    }

    @Test
    void shouldBeHealthy() {
        given()
            .pathParam("componentName", "MailReceptionCheck")
        .when()
            .get("/healthcheck/checks/{componentName}")
        .then()
            .body("componentName", equalTo("MailReceptionCheck"))
            .body("escapedComponentName", equalTo("MailReceptionCheck"))
            .body("status", equalTo(ResultStatus.HEALTHY.getValue()))
            .body("cause", is(nullValue()));
    }

    @Test
    void shouldBeUnhealthyWhenRabbitMQIsPaused() throws Exception {
        RABBIT_MQ_EXTENSION.dockerRabbitMQ().pause();
        Thread.sleep(1000);
        try {
            given()
                .pathParam("componentName", "MailReceptionCheck")
            .when()
                .get("/healthcheck/checks/{componentName}")
            .then()
                .body("componentName", equalTo("MailReceptionCheck"))
                .body("escapedComponentName", equalTo("MailReceptionCheck"))
                .body("status", equalTo(ResultStatus.UNHEALTHY.getValue()));
        } finally {
            RABBIT_MQ_EXTENSION.dockerRabbitMQ().unpause();
        }
    }
}
