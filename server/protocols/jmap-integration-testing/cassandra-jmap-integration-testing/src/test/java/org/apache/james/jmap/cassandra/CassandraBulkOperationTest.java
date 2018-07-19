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

package org.apache.james.jmap.cassandra;

import static com.jayway.restassured.RestAssured.given;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.DOMAIN;
import static org.apache.james.jmap.TestingConstants.NAME;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.james.CassandraJmapTestRule;
import org.apache.james.DockerCassandraRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.parsing.Parser;

public class CassandraBulkOperationTest {
    private static final Integer NUMBER_OF_MAIL_TO_CREATE = 250;

    @ClassRule
    public static DockerCassandraRule cassandra =  new DockerCassandraRule();

    @Rule
    public CassandraJmapTestRule rule = CassandraJmapTestRule.defaultTestRule();

    private static final String USERNAME = "username@" + DOMAIN;
    private static final MailboxPath TRASH_PATH = MailboxPath.forUser(USERNAME, DefaultMailboxes.TRASH);
    private static final String PASSWORD = "password";

    private GuiceJamesServer jmapServer;

    @Before
    public void setup() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.defaultParser = Parser.JSON;
    }

    @After
    public void teardown() {
        if (jmapServer != null) {
            jmapServer.stop();
        }
    }

    @Test
    public void setMessagesShouldWorkForHugeNumberOfEmailsToTrashWhenChunksConfigurationAreLowEnough() throws Exception {
        jmapServer = createServerWithExpungeChunkSize(85);
        String mailIds = provistionMails(NUMBER_OF_MAIL_TO_CREATE);

        AccessToken accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), USERNAME, PASSWORD);
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [" + mailIds + "]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".destroyed", hasSize(NUMBER_OF_MAIL_TO_CREATE));
    }

    @Test
    public void setMessagesShouldFailForHugeNumberOfEmailsToTrashWhenChunksConfigurationAreTooBig() throws Exception {
        jmapServer = createServerWithExpungeChunkSize(NUMBER_OF_MAIL_TO_CREATE);
        String mailIds = provistionMails(NUMBER_OF_MAIL_TO_CREATE);

        AccessToken accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), USERNAME, PASSWORD);
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [" + mailIds + "]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(400);
    }

    private String provistionMails(int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(i -> appendOneMessageToTrash())
            .map(this::quote)
            .collect(Collectors.joining(","));
    }

    private GuiceJamesServer createServerWithExpungeChunkSize(int expungeChunkSize) throws Exception {
        GuiceJamesServer jmapServer = rule.jmapServer(cassandra.getModule(),
            binder -> binder.bind(CassandraConfiguration.class)
                .toInstance(
                    CassandraConfiguration.builder()
                        .expungeChunkSize(expungeChunkSize)
                        .build()));
        jmapServer.start();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
            .build();

        jmapServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(USERNAME, PASSWORD);
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(TRASH_PATH);
        return jmapServer;
    }

    private String appendOneMessageToTrash() {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        try {
            return jmapServer.getProbe(MailboxProbeImpl.class)
                .appendMessage(USERNAME, TRASH_PATH,
                    AppendCommand.builder()
                        .build(Message.Builder
                            .of()
                            .setSubject("my test subject")
                            .setBody("testmail", StandardCharsets.UTF_8)
                            .setDate(Date.from(dateTime.toInstant()))))
                .getMessageId()
                .serialize();
        } catch (MailboxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String quote(String id) {
        return '"' + id + '"';
    }

}
