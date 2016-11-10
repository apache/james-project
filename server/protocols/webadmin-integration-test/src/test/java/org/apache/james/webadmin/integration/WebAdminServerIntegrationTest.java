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

package org.apache.james.webadmin.integration;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import org.apache.james.CassandraJamesServer;
import org.apache.james.CassandraJamesServerMain;
import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.modules.CassandraJmapServerModule;
import org.apache.james.webadmin.routes.DomainRoutes;
import org.apache.james.webadmin.routes.UserMailboxesRoutes;
import org.apache.james.webadmin.routes.UserRoutes;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public class WebAdminServerIntegrationTest {

    public static final String DOMAIN = "domain";
    public static final String USERNAME = "username@" + DOMAIN;
    public static final String SPECIFIC_DOMAIN = DomainRoutes.DOMAINS + SEPARATOR + DOMAIN;
    public static final String SPECIFIC_USER = UserRoutes.USERS + SEPARATOR + USERNAME;
    public static final String MAILBOX = "mailbox";
    public static final String SPECIFIC_MAILBOX = SPECIFIC_USER + SEPARATOR + UserMailboxesRoutes.MAILBOXES + SEPARATOR + MAILBOX;

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);
    private EmbeddedCassandra cassandra = EmbeddedCassandra.createStartServer();

    @Rule
    public RuleChain chain = RuleChain
        .outerRule(temporaryFolder)
        .around(embeddedElasticSearch);

    private CassandraJamesServer guiceJamesServer;

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = new CassandraJamesServer()
            .combineWith(CassandraJamesServerMain.cassandraServerModule)
            .overrideWith(new CassandraJmapServerModule(temporaryFolder, embeddedElasticSearch, cassandra),
                new WebAdminConfigurationModule());
        guiceJamesServer.start();

        RestAssured.requestSpecification = new RequestSpecBuilder()
        		.setContentType(ContentType.JSON)
        		.setAccept(ContentType.JSON)
        		.setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8)))
        		.setPort(guiceJamesServer.getWebAdminProbe().getWebAdminPort())
        		.build();
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void postShouldAddTheGivenDomain() throws Exception {
        when()
            .put(SPECIFIC_DOMAIN)
        .then()
            .statusCode(204);

        assertThat(guiceJamesServer.serverProbe().listDomains()).contains(DOMAIN);
    }

    @Test
    public void deleteShouldRemoveTheGivenDomain() throws Exception {
        guiceJamesServer.serverProbe().addDomain(DOMAIN);

        when()
            .delete(SPECIFIC_DOMAIN)
        .then()
            .statusCode(204);

        assertThat(guiceJamesServer.serverProbe().listDomains()).doesNotContain(DOMAIN);
    }

    @Test
    public void postShouldAddTheUser() throws Exception {
        guiceJamesServer.serverProbe().addDomain(DOMAIN);

        given()
            .body("{\"password\":\"password\"}")
        .when()
            .put(SPECIFIC_USER)
        .then()
            .statusCode(204);

        assertThat(guiceJamesServer.serverProbe().listUsers()).contains(USERNAME);
    }

    @Test
    public void deleteShouldRemoveTheUser() throws Exception {
        guiceJamesServer.serverProbe().addDomain(DOMAIN);
        guiceJamesServer.serverProbe().addUser(USERNAME, "anyPassword");

        given()
            .body("{\"username\":\"" + USERNAME + "\",\"password\":\"password\"}")
        .when()
            .delete(SPECIFIC_USER)
        .then()
            .statusCode(204);

        assertThat(guiceJamesServer.serverProbe().listUsers()).doesNotContain(USERNAME);
    }

    @Test
    public void getUsersShouldDisplayUsers() throws Exception {
        guiceJamesServer.serverProbe().addDomain(DOMAIN);
        guiceJamesServer.serverProbe().addUser(USERNAME, "anyPassword");

        when()
            .get(UserRoutes.USERS)
        .then()
            .statusCode(200)
            .body(is("[{\"username\":\"username@domain\"}]"));
    }

    @Test
    public void putMailboxShouldAddAMailbox() throws Exception {
        guiceJamesServer.serverProbe().addDomain(DOMAIN);
        guiceJamesServer.serverProbe().addUser(USERNAME, "anyPassword");

        when()
            .put(SPECIFIC_MAILBOX)
        .then()
            .statusCode(204);

        assertThat(guiceJamesServer.serverProbe().listUserMailboxes(USERNAME)).containsExactly(MAILBOX);
    }



    @Test
    public void deleteMailboxShouldRemoveAMailbox() throws Exception {
        guiceJamesServer.serverProbe().addDomain(DOMAIN);
        guiceJamesServer.serverProbe().addUser(USERNAME, "anyPassword");
        guiceJamesServer.serverProbe().createMailbox("#private", USERNAME, MAILBOX);

        when()
            .delete(SPECIFIC_MAILBOX)
        .then()
            .statusCode(204);

        assertThat(guiceJamesServer.serverProbe().listUserMailboxes(USERNAME)).isEmpty();
    }

}
