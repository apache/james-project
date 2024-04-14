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

package org.apache.james.jmap.draft.methods.integration;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.getSetMessagesUpdateOKResponseAssertions;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Username;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.utils.DataProbeImpl;
import org.apache.mailet.Mail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class SetMessagesOutboxFlagUpdateTest {
    private static final Username USERNAME = Username.of("username@" + DOMAIN);
    private static final String PASSWORD = "password";

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    private AccessToken accessToken;
    private GuiceJamesServer jmapServer;

    protected MailQueueFactory<MailQueue> noopMailQueueFactory = new MailQueueFactory<MailQueue>() {
        @Override
        public Optional<MailQueue> getQueue(MailQueueName name, PrefetchCount prefetchCount) {
            return Optional.of(createQueue(name));
        }

        @Override
        public MailQueue createQueue(MailQueueName name, PrefetchCount prefetchCount) {
            return new MailQueue() {
                @Override
                public void close() throws IOException {
                }

                @Override
                public MailQueueName getName() {
                    return name;
                }

                @Override
                public void enQueue(Mail mail, Duration delay) {
                }

                @Override
                public Publisher<Void> enqueueReactive(Mail mail) {
                    return Mono.fromRunnable(Throwing.runnable(() -> enQueue(mail)).sneakyThrow());
                }

                @Override
                public void enQueue(Mail mail) {

                }

                @Override
                public Flux<MailQueueItem> deQueue() {
                    return Flux.never();
                }
            };
        }

        @Override
        public Set<MailQueueName> listCreatedMailQueues() {
            throw new NotImplementedException("Minimalistic implementation. Please do not list queues");
        }
    };

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME.asString(), PASSWORD);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);
        mailboxProbe.createMailbox("#private", USERNAME.asString(), DefaultMailboxes.INBOX);
        accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), USERNAME, PASSWORD);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void flagsUpdateShouldBeAllowedInTheOutbox() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String toUsername = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + toUsername + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
            .post("/jmap");

        String jmapMessageId = with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
            .post("/jmap")
        .then()
            .extract()
            .<String>path(ARGUMENTS + ".messageIds[0]");


        String updateRequestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + jmapMessageId  + "\" : {" +
            "        \"isUnread\": false" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(updateRequestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(jmapMessageId));
    }

}
