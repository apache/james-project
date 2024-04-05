/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.time.Duration;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Username;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.utils.DataProbeImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import io.restassured.RestAssured;

public abstract class ProvisioningTest {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String DOMAIN = "mydomain.tld";
    private static final Username USER = Username.of("myuser@" + DOMAIN);
    private static final String PASSWORD = "secret";

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    private GuiceJamesServer jmapServer;
    private AccessToken userToken;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
            .build();

        jmapServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(USER.asString(), PASSWORD);
        userToken = authenticateJamesUser(baseUri(jmapServer), USER, PASSWORD);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void provisionMailboxesShouldNotDuplicateMailboxByName() throws Exception {
        ConcurrentTestRunner.builder()
            .operation((a, b) -> with()
                .header("Authorization", userToken.asString())
                .body("[[\"getMailboxes\", {}, \"#0\"]]")
                .post("/jmap"))
            .threadCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        given()
            .header("Authorization", userToken.asString())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .header("Content-Length", notNullValue())
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(DefaultMailboxes.DEFAULT_MAILBOXES.size()))
            .body(ARGUMENTS + ".list.name", hasItems(DefaultMailboxes.DEFAULT_MAILBOXES.toArray()));
    }

    @Category(BasicFeature.class)
    @Test
    public void provisionMailboxesShouldSubscribeToThem() throws Exception {
        with()
            .header("Authorization", userToken.asString())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
            .post("/jmap");

        assertThat(jmapServer.getProbe(MailboxProbeImpl.class)
            .listSubscriptions(USER.asString()))
            .containsAll(DefaultMailboxes.DEFAULT_MAILBOXES);
    }
}
