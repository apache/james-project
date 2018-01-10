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

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.GuiceJamesServer;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;

public abstract class ProvisioningTest {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String DOMAIN = "mydomain.tld";
    private static final String USER = "myuser@" + DOMAIN;
    private static final String PASSWORD = "secret";
    
    protected abstract GuiceJamesServer createJmapServer();

    private GuiceJamesServer jmapServer;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
            .build();

        DataProbeImpl serverProbe = jmapServer.getProbe(DataProbeImpl.class);
        serverProbe.addDomain(DOMAIN);
        serverProbe.addUser(USER, PASSWORD);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void provisionMailboxesShouldNotDuplicateMailboxByName() throws Exception {
        String token = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER, PASSWORD).serialize();

        boolean termination = new ConcurrentTestRunner(10, 1,
            (a, b) -> with()
                .header("Authorization", token)
                .body("[[\"getMailboxes\", {}, \"#0\"]]")
                .post("/jmap"))
            .run()
            .awaitTermination(1, TimeUnit.MINUTES);

        assertThat(termination).isTrue();

        given()
            .header("Authorization", token)
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(5))
            .body(ARGUMENTS + ".list.name", hasItems(DefaultMailboxes.DEFAULT_MAILBOXES.toArray()));
    }

    @Test
    public void provisionMailboxesShouldSubscribeToThem() throws Exception {
        String token = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER, PASSWORD).serialize();

        with()
            .header("Authorization", token)
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
            .post("/jmap");

        assertThat(jmapServer.getProbe(MailboxProbeImpl.class)
            .listSubscriptions(USER))
            .containsOnlyElementsOf(DefaultMailboxes.DEFAULT_MAILBOXES);
    }

    private URIBuilder baseUri() {
        return new URIBuilder()
            .setScheme("http")
            .setHost("localhost")
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
            .setCharset(StandardCharsets.UTF_8);
    }
}
