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
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.apache.james.CassandraJmapTestRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.routes.DomainRoutes;
import org.apache.james.webadmin.routes.UserMailboxesRoutes;
import org.apache.james.webadmin.routes.UserRoutes;
import org.apache.james.webadmin.swagger.routes.SwaggerRoutes;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

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
    public static final String VERSION = "/cassandra/version";
    public static final String VERSION_LATEST = VERSION + "/latest";
    public static final String UPGRADE_VERSION = VERSION + "/upgrade";
    public static final String UPGRADE_TO_LATEST_VERSION = UPGRADE_VERSION + "/latest";

    @Rule
    public CassandraJmapTestRule cassandraJmapTestRule = CassandraJmapTestRule.defaultTestRule();

    private GuiceJamesServer guiceJamesServer;
    private DataProbe dataProbe;
    private WebAdminGuiceProbe webAdminGuiceProbe;

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = cassandraJmapTestRule.jmapServer()
                .overrideWith(new WebAdminConfigurationModule());
        guiceJamesServer.start();
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = new RequestSpecBuilder()
        		.setContentType(ContentType.JSON)
        		.setAccept(ContentType.JSON)
        		.setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8)))
        		.build();
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void postShouldAddTheGivenDomain() throws Exception {
        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .put(SPECIFIC_DOMAIN)
        .then()
            .statusCode(204);

        assertThat(dataProbe.listDomains()).contains(DOMAIN);
    }

    @Test
    public void deleteShouldRemoveTheGivenDomain() throws Exception {
        dataProbe.addDomain(DOMAIN);

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .delete(SPECIFIC_DOMAIN)
        .then()
            .statusCode(204);

        assertThat(dataProbe.listDomains()).doesNotContain(DOMAIN);
    }

    @Test
    public void postShouldAddTheUser() throws Exception {
        dataProbe.addDomain(DOMAIN);

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
            .body("{\"password\":\"password\"}")
        .when()
            .put(SPECIFIC_USER)
        .then()
            .statusCode(204);

        assertThat(dataProbe.listUsers()).contains(USERNAME);
    }

    @Test
    public void deleteShouldRemoveTheUser() throws Exception {
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME, "anyPassword");

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
            .body("{\"username\":\"" + USERNAME + "\",\"password\":\"password\"}")
        .when()
            .delete(SPECIFIC_USER)
        .then()
            .statusCode(204);

        assertThat(dataProbe.listUsers()).doesNotContain(USERNAME);
    }

    @Test
    public void getUsersShouldDisplayUsers() throws Exception {
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME, "anyPassword");

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(UserRoutes.USERS)
        .then()
            .statusCode(200)
            .body(is("[{\"username\":\"username@domain\"}]"));
    }

    @Test
    public void putMailboxShouldAddAMailbox() throws Exception {
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME, "anyPassword");

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .put(SPECIFIC_MAILBOX)
        .then()
            .statusCode(204);

        assertThat(guiceJamesServer.getProbe(MailboxProbeImpl.class).listUserMailboxes(USERNAME)).containsExactly(MAILBOX);
    }

    @Test
    public void deleteMailboxShouldRemoveAMailbox() throws Exception {
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME, "anyPassword");
        guiceJamesServer.getProbe(MailboxProbeImpl.class).createMailbox("#private", USERNAME, MAILBOX);

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .delete(SPECIFIC_MAILBOX)
        .then()
            .statusCode(204);

        assertThat(guiceJamesServer.getProbe(MailboxProbeImpl.class).listUserMailboxes(USERNAME)).isEmpty();
    }

    @Test
    public void getCurrentVersionShouldReturnNullForCurrentVersionAsBeginning() throws Exception {
        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(VERSION)
        .then()
            .statusCode(200)
            .body(is("{\"version\":null}"));
    }

    @Test
    public void getLatestVersionShouldReturnTheConfiguredLatestVersion() throws Exception {
        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(VERSION_LATEST)
        .then()
            .statusCode(200)
            .body(is("{\"version\":" + CassandraSchemaVersionManager.MAX_VERSION + "}"));
    }

    @Test
    public void postShouldDoMigrationAndUpdateCurrentVersion() throws Exception {
        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
            .body(String.valueOf(CassandraSchemaVersionManager.MAX_VERSION))
        .when()
            .post(UPGRADE_VERSION)
        .then()
            .statusCode(204);

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(VERSION)
        .then()
            .statusCode(200)
            .body(is("{\"version\":" + CassandraSchemaVersionManager.MAX_VERSION + "}"));
    }

    @Test
    public void postShouldDoMigrationAndUpdateToTheLatestVersion() throws Exception {
        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .post(UPGRADE_TO_LATEST_VERSION)
        .then()
            .statusCode(200);

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(VERSION)
        .then()
            .statusCode(200)
            .body(is("{\"version\":" + CassandraSchemaVersionManager.MAX_VERSION + "}"));
    }

    @Test
    public void getSwaggerShouldReturnJsonDataForSwagger() throws Exception {
        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(SwaggerRoutes.SWAGGER_ENDPOINT)
        .then()
            .statusCode(200)
            .body(containsString("\"swagger\":\"2.0\""))
            .body(containsString("\"info\":{\"description\":\"All the web administration API for JAMES\",\"version\":\"V1.0\",\"title\":\"JAMES Web Admin API\"}"))
            .body(containsString("\"tags\":[\"User's Mailbox\"]"))
            .body(containsString("\"tags\":[\"GlobalQuota\"]"))
            .body(containsString("\"tags\":[\"Domains\"]"))
            .body(containsString("\"tags\":[\"Users\"]"))
        ;
    }

}
