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

package org.apache.james.webadmin.routes;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.requestSpecification;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static org.apache.james.webadmin.Constants.JSON_CONTENT_TYPE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.net.InetAddress;

import org.apache.james.core.Domain;
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.james.dlp.eventsourcing.EventSourcingDLPConfigurationStore;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.eventsourcing.eventstore.memory.InMemoryEventStore;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import net.javacrumbs.jsonunit.core.Option;

class DLPConfigurationRoutesTest {

    private static final String DEFAULT_DOMAIN = "james.org";
    private static final Domain SENDER_DOMAIN = Domain.of(DEFAULT_DOMAIN);
    private static final String DOMAIN_2 = "apache.org";
    private static final Domain SENDER_DOMAIN_2 = Domain.of(DOMAIN_2);

    private WebAdminServer webAdminServer;
    private EventSourcingDLPConfigurationStore dlpStore;

    private void createServer(DLPConfigurationStore dlpConfigurationStore, DomainList domainList) {
        webAdminServer = WebAdminUtils.createWebAdminServer(
                new DLPConfigurationRoutes(dlpConfigurationStore, domainList, new JsonTransformer()))
            .start();

        requestSpecification = buildRequestSpecification(webAdminServer);
    }

    RequestSpecification buildRequestSpecification(WebAdminServer server) {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        return WebAdminUtils
                .buildRequestSpecification(server)
                .setBasePath(DLPConfigurationRoutes.BASE_PATH)
                .build();
    }

    @BeforeEach
    void setup() throws Exception {
        MemoryDomainList domainList = new MemoryDomainList();
        domainList.addDomain(SENDER_DOMAIN);
        domainList.addDomain(SENDER_DOMAIN_2);

        dlpStore = new EventSourcingDLPConfigurationStore(new InMemoryEventStore());
        createServer(dlpStore, domainList);
    }

    @Nested
    class DefineStore {

        @Test
        void putShouldStoreTheConfigurations() {
            String storeBody =
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"1\"," +
                "    \"expression\": \"expression 1\"," +
                "    \"explanation\": \"explanation 1\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": true," +
                "    \"targetsContent\": true" +
                "  }," +
                "  {" +
                "    \"id\": \"2\"," +
                "    \"expression\": \"expression 2\"," +
                "    \"explanation\": \"explanation 2\"," +
                "    \"targetsSender\": false," +
                "    \"targetsRecipients\": false," +
                "    \"targetsContent\": false" +
                "  }]}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            String retrievedBody = with()
                .get(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(retrievedBody).isEqualTo(storeBody);
        }

        @Test
        void putShouldStoreTheConfigurationsWhenTargetsAreNotSpecified() {
            String storeBody =
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"3\"," +
                "    \"expression\": \"expression 3\"," +
                "    \"explanation\": \"explanation 3\"" +
                "  }]}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            String retrievedBody = with()
                .get(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(retrievedBody)
                .isEqualTo(
                    "{\"rules\": [" +
                    "  {" +
                    "    \"id\": \"3\"," +
                    "    \"expression\": \"expression 3\"," +
                    "    \"explanation\": \"explanation 3\"," +
                    "    \"targetsSender\": false," +
                    "    \"targetsRecipients\": false," +
                    "    \"targetsContent\": false" +
                    "  }" +
                    "]}");
        }

        @Test
        void putShouldStoreTheConfigurationsWhenExplanationNotSpecified() {
            String storeBody =
                "{\"rules\": [{" +
                "  \"id\": \"3\"," +
                "  \"expression\": \"expression 3\"" +
                "}]}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            String retrievedBody = with()
                .get(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(retrievedBody)
                .isEqualTo(
                    "{\"rules\": [" +
                    "  {" +
                    "    \"id\": \"3\"," +
                    "    \"expression\": \"expression 3\"," +
                    "    \"explanation\": null," +
                    "    \"targetsSender\": false," +
                    "    \"targetsRecipients\": false," +
                    "    \"targetsContent\": false" +
                    "  }" +
                    "]}");
        }

        @Test
        void putShouldReturnBadRequestWhenIdIsNotSpecified() {
            String body =
                "{\"rules\": [{" +
                "  \"expression\": \"expression 4\"," +
                "  \"explanation\": \"explanation 4\"," +
                "  \"targetsSender\": false," +
                "  \"targetsRecipients\": false," +
                "  \"targetsContent\": false" +
                "}]}";

            given()
                .body(body)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(400))
                .body("type", is("InvalidArgument"))
                .body("message", is("JSON payload of the request is not valid"))
                .body("details", containsString("'id' is mandatory"));
        }

        @Test
        void putShouldReturnBadRequestWhenExpressionIsNotSpecified() {
            String body =
                "{\"rules\": [{" +
                "  \"id\": \"5\"," +
                "  \"explanation\": \"explanation 5\"," +
                "  \"targetsSender\": false," +
                "  \"targetsRecipients\": false," +
                "  \"targetsContent\": false" +
                "}]}";

            given()
                .body(body)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(400))
                .body("type", is("InvalidArgument"))
                .body("message", is("JSON payload of the request is not valid"))
                .body("details", containsString("'expression' is mandatory"));
        }

        @Test
        void putShouldReturnNotFoundWhenDomainNotInList() {
            String body =
                "[{" +
                "  \"id\": \"1\"," +
                "  \"expression\": \"expression 1\"," +
                "  \"explanation\": \"explanation 1\"," +
                "  \"targetsSender\": true," +
                "  \"targetsRecipients\": true," +
                "  \"targetsContent\": true" +
                "}," +
                "{" +
                "  \"id\": \"2\"," +
                "  \"expression\": \"expression 2\"," +
                "  \"explanation\": \"explanation 2\"," +
                "  \"targetsSender\": false," +
                "  \"targetsRecipients\": false," +
                "  \"targetsContent\": false" +
                "}]";

            given()
                .body(body)
            .when()
                .put("strange.com")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                .body("type", is("InvalidArgument"))
                .body("message", is("'strange.com' is not managed by this James server"));
        }

        @Test
        void putShouldReturnBadRequestWhenDomainIsNotValid() {
            String body =
                "[{" +
                "  \"id\": \"1\"," +
                "  \"expression\": \"expression 1\"," +
                "  \"explanation\": \"explanation 1\"," +
                "  \"targetsSender\": true," +
                "  \"targetsRecipients\": true," +
                "  \"targetsContent\": true" +
                "}," +
                "{" +
                "  \"id\": \"2\"," +
                "  \"expression\": \"expression 2\"," +
                "  \"explanation\": \"explanation 2\"," +
                "  \"targetsSender\": false," +
                "  \"targetsRecipients\": false," +
                "  \"targetsContent\": false" +
                "}]";

            given()
                .body(body)
            .when()
                .put("dr@strange.com")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("Domain parts ASCII chars must be a-z A-Z 0-9 - or _"));
        }

        @Test
        void putShouldUpdateTheConfigurations() {
            String storeBody =
                "{\"rules\": [" +
                    "  {" +
                    "    \"id\": \"1\"," +
                    "    \"expression\": \"expression 1\"," +
                    "    \"explanation\": \"explanation 1\"," +
                    "    \"targetsSender\": true," +
                    "    \"targetsRecipients\": true," +
                    "    \"targetsContent\": true" +
                    "  }," +
                    "  {" +
                    "    \"id\": \"2\"," +
                    "    \"expression\": \"expression 2\"," +
                    "    \"explanation\": \"explanation 2\"," +
                    "    \"targetsSender\": false," +
                    "    \"targetsRecipients\": false," +
                    "    \"targetsContent\": false" +
                    "  }," +
                    "  {" +
                    "    \"id\": \"3\"," +
                    "    \"expression\": \"expression 3\"," +
                    "    \"explanation\": \"explanation 3\"," +
                    "    \"targetsSender\": false," +
                    "    \"targetsRecipients\": false," +
                    "    \"targetsContent\": true" +
                    "  }]}";
            String updatedBody =
                "{\"rules\": [" +
                    "  {" +
                    "    \"id\": \"4\"," +
                    "    \"expression\": \"expression 4\"," +
                    "    \"explanation\": \"explanation 4\"," +
                    "    \"targetsSender\": true," +
                    "    \"targetsRecipients\": true," +
                    "    \"targetsContent\": true" +
                    "  }," +
                    "  {" +
                    "    \"id\": \"2\"," +
                    "    \"expression\": \"expression 2\"," +
                    "    \"explanation\": \"explanation 2\"," +
                    "    \"targetsSender\": false," +
                    "    \"targetsRecipients\": false," +
                    "    \"targetsContent\": false" +
                    "  }," +
                    "  {" +
                    "    \"id\": \"3\"," +
                    "    \"expression\": \"expression 3 updated\"," +
                    "    \"explanation\": \"explanation 3 updated\"," +
                    "    \"targetsSender\": false," +
                    "    \"targetsRecipients\": false," +
                    "    \"targetsContent\": true" +
                    "  }]}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            given()
                .body(updatedBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            String retrievedBody = with()
                .get(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
            .extract()
                .body().asString();

            assertThatJson(retrievedBody)
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo(updatedBody);
        }
        
        @Test
        void putShouldRejectDuplicatedIds() {
            String storeBody =
                "{\"rules\": [" +
                    "  {" +
                    "    \"id\": \"1\"," +
                    "    \"expression\": \"expression 1\"," +
                    "    \"explanation\": \"explanation 1\"," +
                    "    \"targetsSender\": true," +
                    "    \"targetsRecipients\": true," +
                    "    \"targetsContent\": true" +
                    "  }," +
                    "  {" +
                    "    \"id\": \"1\"," +
                    "    \"expression\": \"expression 3\"," +
                    "    \"explanation\": \"explanation 3\"," +
                    "    \"targetsSender\": false," +
                    "    \"targetsRecipients\": false," +
                    "    \"targetsContent\": true" +
                    "  }]}";

            given()
                .body(storeBody).log().ifValidationFails()
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("'id' duplicates are not allowed in DLP rules"));
        }

        @Test
        void putShouldClearTheConfigurationsWhenNoRule() {
            String storeBody =
                "{\"rules\": [" +
                    "  {" +
                    "    \"id\": \"1\"," +
                    "    \"expression\": \"expression 1\"," +
                    "    \"explanation\": \"explanation 1\"," +
                    "    \"targetsSender\": true," +
                    "    \"targetsRecipients\": true," +
                    "    \"targetsContent\": true" +
                    "  }," +
                    "  {" +
                    "    \"id\": \"2\"," +
                    "    \"expression\": \"expression 2\"," +
                    "    \"explanation\": \"explanation 2\"," +
                    "    \"targetsSender\": false," +
                    "    \"targetsRecipients\": false," +
                    "    \"targetsContent\": false" +
                    "  }]}";
            String updatedBody = "{\"rules\": []}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            given()
                .body(updatedBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            String retrievedBody = with()
                .get(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
            .extract()
                .body().asString();

            assertThatJson(retrievedBody).isEqualTo(updatedBody);
        }
    }

    @Nested
    class DefineClear {

        @Test
        void deleteShouldRemoveTheConfigurations() {
            String storeBody =
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"1\"," +
                "    \"expression\": \"expression 1\"," +
                "    \"explanation\": \"explanation 1\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": true," +
                "    \"targetsContent\": true" +
                "  }," +
                "  {" +
                "    \"id\": \"2\"," +
                "    \"expression\": \"expression 2\"," +
                "    \"explanation\": \"explanation 2\"," +
                "    \"targetsSender\": false," +
                "    \"targetsRecipients\": false," +
                "    \"targetsContent\": false" +
                "  }]}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            when()
                .delete(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            String retrievedBody = with()
                .get(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(retrievedBody).isEqualTo("{\"rules\":[]}");
        }

        @Test
        void deleteShouldRemoveOnlyConfigurationsFromCorrespondingDomain() {
            String storeBody =
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"1\"," +
                "    \"expression\": \"expression 1\"," +
                "    \"explanation\": \"explanation 1\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": true," +
                "    \"targetsContent\": true" +
                "  }," +
                "  {" +
                "    \"id\": \"2\"," +
                "    \"expression\": \"expression 2\"," +
                "    \"explanation\": \"explanation 2\"," +
                "    \"targetsSender\": false," +
                "    \"targetsRecipients\": false," +
                "    \"targetsContent\": false" +
                "  }]}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);


            String storeDomain2Body =
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"3\"," +
                "    \"expression\": \"apache.org\"," +
                "    \"targetsSender\": true" +
                "  }" +
                "]}";

            given()
                .body(storeDomain2Body)
            .when()
                .put(DOMAIN_2)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            when()
                .delete(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            String retrievedBody = with()
                .get(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(retrievedBody).isEqualTo("{\"rules\":[]}");

            String retrievedBodyDomain2 = when()
                .get(DOMAIN_2)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(retrievedBodyDomain2)
                .isEqualTo(
                    "{\"rules\": [" +
                    "    {" +
                    "        \"id\": \"3\"," +
                    "        \"expression\": \"apache.org\"," +
                    "        \"explanation\": null," +
                    "        \"targetsSender\": true," +
                    "        \"targetsRecipients\": false," +
                    "        \"targetsContent\": false" +
                    "    }" +
                    "]}");
        }

        @Test
        void deleteShouldReturnNotFoundWhenDomainNotInList() {
            when()
                .delete("strange.com")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                .body("type", is("InvalidArgument"))
                .body("message", is("'strange.com' is not managed by this James server"));
        }

        @Test
        void deleteShouldReturnBadRequestWhenDomainIsNotValid() {
            when()
                .delete("dr@strange.com")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("Domain parts ASCII chars must be a-z A-Z 0-9 - or _"));
        }
    }

    @Nested
    class DefineList {

        @Test
        void getShouldReturnOK() {
            String storeBody =
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"1\"," +
                "    \"expression\": \"expression 1\"," +
                "    \"explanation\": \"explanation 1\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": true," +
                "    \"targetsContent\": true" +
                "  }" +
                "]}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            when()
                .get(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON);
        }

        @Test
        void getShouldReturnABody() {
            String storeBody =
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"1\"," +
                "    \"expression\": \"james.org\"," +
                "    \"explanation\": \"explanation 1\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": true," +
                "    \"targetsContent\": true" +
                "  }," +
                "  {" +
                "    \"id\": \"2\"," +
                "    \"expression\": \"james.org\"," +
                "    \"explanation\": \"explanation 2\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": false," +
                "    \"targetsContent\": false" +
                "  }]}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            String body = when()
                .get(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(body).isEqualTo(
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"1\"," +
                "    \"expression\": \"james.org\"," +
                "    \"explanation\": \"explanation 1\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": true," +
                "    \"targetsContent\": true" +
                "  }," +
                "  {" +
                "    \"id\": \"2\"," +
                "    \"expression\": \"james.org\"," +
                "    \"explanation\": \"explanation 2\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": false," +
                "    \"targetsContent\": false" +
                "  }" +
                "]}");
        }

        @Test
        void getShouldReturnAnEmptyBodyWhenDLPStoreIsEmpty() {
            String storeBody = "{\"rules\": []}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            String body = when()
                .get(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(body).isEqualTo("{\"rules\":[]}");
        }

        @Test
        void getShouldReturnOnlyConfigurationsFromCorrespondingDomain() {
            String storeBody =
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"1\"," +
                "    \"expression\": \"james.org\"," +
                "    \"explanation\": \"explanation 1\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": true," +
                "    \"targetsContent\": true" +
                "  }," +
                "  {" +
                "    \"id\": \"2\"," +
                "    \"expression\": \"james.org\"," +
                "    \"explanation\": \"explanation 2\"," +
                "    \"targetsSender\": false," +
                "    \"targetsRecipients\": false," +
                "    \"targetsContent\": false" +
                "  }]}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);


            String storeDomain2Body =
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"3\"," +
                "    \"expression\": \"apache.org\"," +
                "    \"targetsSender\": true" +
                "  }" +
                "]}";

            given()
                .body(storeDomain2Body)
            .when()
                .put(DOMAIN_2)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            String body = when()
                .get(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(body).isEqualTo(
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"1\"," +
                "    \"expression\": \"james.org\"," +
                "    \"explanation\": \"explanation 1\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": true," +
                "    \"targetsContent\": true" +
                "  }," +
                "  {" +
                "    \"id\": \"2\"," +
                "    \"expression\": \"james.org\"," +
                "    \"explanation\": \"explanation 2\"," +
                "    \"targetsSender\": false," +
                "    \"targetsRecipients\": false," +
                "    \"targetsContent\": false" +
                "  }" +
                "]}");
        }

        @Test
        void getShouldReturnNotFoundWhenDomainNotInList() {
            when()
                .get("strange.com")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                .body("type", is("InvalidArgument"))
                .body("message", is("'strange.com' is not managed by this James server"));
        }

        @Test
        void getShouldReturnBadRequestWhenDomainIsNotValid() {
            when()
                .get("dr@strange.com")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("Domain parts ASCII chars must be a-z A-Z 0-9 - or _"));
        }
    }
    
    @Nested
    class DefineFetch {
        @Test
        void fetchShouldBeOK() {
            storeRules();

            String jsonAsString =
                when()
                    .get(DEFAULT_DOMAIN + "/rules/1")
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                        .body()
                        .asString();

            assertThatJson(jsonAsString)
                .when(IGNORING_ARRAY_ORDER)
                .when(IGNORING_EXTRA_FIELDS)
                    .isEqualTo("{" +
                            "    \"id\": \"1\"," +
                            "    \"expression\": \"expression 1\"," +
                            "    \"explanation\": \"explanation 1\"," +
                            "    \"targetsSender\": true," +
                            "    \"targetsRecipients\": true," +
                            "    \"targetsContent\": true" +
                            "}");
        }

        @Test
        void fetchOnUnknownDomainShouldBe404() {
            storeRules();

            when()
                .get("strange.com/rules/1")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                .body("type", is("InvalidArgument"))
                .body("message", is("'strange.com' is not managed by this James server"));
        }

        @Test
        void fetchOnUnknownDomainAndRuleShouldBe404() {
            when()
                .get("strange.com/rules/666")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                .body("type", is("InvalidArgument"))
                .body("message", is("'strange.com' is not managed by this James server"));
        }

        @Test
        void fetchOnUnknownRuleIdShouldBe404() {
            storeRules();

            when()
                .get(DEFAULT_DOMAIN + "/rules/666")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .contentType(JSON_CONTENT_TYPE)
                .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                .body("type", is("InvalidArgument"))
                .body("message", is("There is no rule '666' for '" + DEFAULT_DOMAIN + "' managed by this James server"));
        }

        private void storeRules() {
            String storeBody =
                "{\"rules\": [" +
                "  {" +
                "    \"id\": \"1\"," +
                "    \"expression\": \"expression 1\"," +
                "    \"explanation\": \"explanation 1\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": true," +
                "    \"targetsContent\": true" +
                "  }," +
                "  {" +
                "    \"id\": \"2\"," +
                "    \"expression\": \"expression 2\"," +
                "    \"explanation\": \"explanation 2\"," +
                "    \"targetsSender\": true," +
                "    \"targetsRecipients\": false," +
                "    \"targetsContent\": false" +
                "  }]}";

            given()
                .body(storeBody)
            .when()
                .put(DEFAULT_DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

        }
    }
}