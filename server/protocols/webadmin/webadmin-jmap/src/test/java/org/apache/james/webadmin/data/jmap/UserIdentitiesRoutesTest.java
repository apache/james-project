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

package org.apache.james.webadmin.data.jmap;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.identity.DefaultIdentitySupplier;
import org.apache.james.jmap.api.identity.IdentityCreationRequest;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.api.identity.IdentityRepositoryTest;
import org.apache.james.jmap.api.model.EmailAddress;
import org.apache.james.jmap.api.model.Identity;
import org.apache.james.jmap.memory.identity.MemoryCustomIdentityDAO;
import org.apache.james.json.DTOConverter;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.restassured.RestAssured;
import net.javacrumbs.jsonunit.core.Option;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scala.publisher.SMono;
import scala.jdk.javaapi.CollectionConverters;

class UserIdentitiesRoutesTest {

    private static final Username BOB = Username.of("bob@domain.tld");
    private static final String BASE_PATH = "/users";
    private static final String GET_IDENTITIES_USERS_PATH = "/%s/identities";
    private WebAdminServer webAdminServer;
    private IdentityRepository identityRepository;
    private DefaultIdentitySupplier identityFactory;

    @BeforeEach
    void setUp() {
        MemoryTaskManager taskManager = new MemoryTaskManager(new Hostname("foo"));
        identityFactory = mock(DefaultIdentitySupplier.class);
        Mockito.when(identityFactory.userCanSendFrom(any(), any())).thenReturn(SMono.just(true).hasElement());

        identityRepository = new IdentityRepository(new MemoryCustomIdentityDAO(), identityFactory);

        JsonTransformer jsonTransformer = new JsonTransformer();
        TasksRoutes tasksRoutes = new TasksRoutes(taskManager, jsonTransformer, DTOConverter.of(UploadCleanupTaskAdditionalInformationDTO.SERIALIZATION_MODULE));
        UserIdentityRoutes userIdentityRoutes = new UserIdentityRoutes(identityRepository, new JsonTransformer());

        webAdminServer = WebAdminUtils.createWebAdminServer(userIdentityRoutes, tasksRoutes).start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(BASE_PATH)
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
    }

    @Test
    void listIdentitiesShouldReturnBothCustomAndServerSetIdentities() throws Exception {
        // identity: server set
        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        IdentityCreationRequest creationRequest = IdentityCreationRequest.fromJava(
            BOB.asMailAddress(),
            Optional.of("identity name 1"),
            Optional.of(List.of(EmailAddress.from(Optional.empty(), new MailAddress("replyTo1@james.org")),
                EmailAddress.from(Optional.empty(), new MailAddress("replyTo2@james.org")))),
            Optional.of(List.of(EmailAddress.from(Optional.empty(), new MailAddress("bcc1@james.org")),
                EmailAddress.from(Optional.empty(), new MailAddress("bcc2@james.org")))),
            Optional.of(1),
            Optional.of("textSignature 1"),
            Optional.of("htmlSignature 1"));

        // identity: custom
        Mono.from(identityRepository.save(BOB, creationRequest)).block();

        String response = when()
            .get(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(io.restassured.http.ContentType.JSON)
            .extract()
            .body()
            .asString();

        String expectedResponse = "[" +
            "    {" +
            "        \"name\": \"identity name 1\"," +
            "        \"email\": \"bob@domain.tld\"," +
            "        \"id\": \"${json-unit.ignore}\"," +
            "        \"mayDelete\": true," +
            "        \"textSignature\": \"textSignature 1\"," +
            "        \"htmlSignature\": \"htmlSignature 1\"," +
            "        \"sortOrder\": 1," +
            "        \"bcc\": [" +
            "            {" +
            "                \"name\": null," +
            "                \"email\": \"bcc1@james.org\"" +
            "            }," +
            "            {" +
            "                \"name\": null," +
            "                \"email\": \"bcc2@james.org\"" +
            "            }" +
            "        ]," +
            "        \"replyTo\": [" +
            "            {" +
            "                \"name\": null," +
            "                \"email\": \"replyTo1@james.org\"" +
            "            }," +
            "            {" +
            "                \"name\": null," +
            "                \"email\": \"replyTo2@james.org\"" +
            "            }" +
            "        ]" +
            "    }," +
            "    {" +
            "        \"name\": \"\"," +
            "        \"email\": \"bob@domain.tld\"," +
            "        \"id\": \"${json-unit.ignore}\"," +
            "        \"mayDelete\": false," +
            "        \"textSignature\": \"\"," +
            "        \"htmlSignature\": \"\"," +
            "        \"sortOrder\": 100," +
            "        \"bcc\": [" +
            "            {" +
            "                \"name\": \"My Boss bcc 1\"," +
            "                \"email\": \"boss_bcc_1@domain.tld\"" +
            "            }" +
            "        ]," +
            "        \"replyTo\": [" +
            "            {" +
            "                \"name\": \"My Boss 1\"," +
            "                \"email\": \"boss1@domain.tld\"" +
            "            }" +
            "        ]" +
            "    }" +
            "]";
        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(expectedResponse);
    }

    @Test
    void listIdentitiesShouldSupportDefaultParam() throws Exception {
        // identity: server set
        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        Integer highPriorityOrder = 1;
        Integer lowPriorityOrder = 2;
        IdentityCreationRequest creationRequest1 = IdentityCreationRequest.fromJava(
            BOB.asMailAddress(),
            Optional.of("identity name 1"),
            Optional.of(List.of(EmailAddress.from(Optional.of("reply name 1"), new MailAddress("reply1@domain.org")))),
            Optional.of(List.of(EmailAddress.from(Optional.of("bcc name 1"), new MailAddress("bcc1@domain.org")))),
            Optional.of(highPriorityOrder),
            Optional.of("textSignature 1"),
            Optional.of("htmlSignature 1"));

        IdentityCreationRequest creationRequest2 = IdentityCreationRequest.fromJava(
            BOB.asMailAddress(),
            Optional.of("identity name 2"),
            Optional.of(List.of(EmailAddress.from(Optional.of("reply name 2"), new MailAddress("reply2@domain.org")))),
            Optional.of(List.of(EmailAddress.from(Optional.of("bcc name 2"), new MailAddress("bcc2@domain.org")))),
            Optional.of(lowPriorityOrder),
            Optional.of("textSignature 2"),
            Optional.of("htmlSignature 2"));

        // identity: custom
        Mono.from(identityRepository.save(BOB, creationRequest1)).block();
        Mono.from(identityRepository.save(BOB, creationRequest2)).block();

        String response = given()
            .queryParam("default", "true")
            .get(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(io.restassured.http.ContentType.JSON)
            .extract()
            .body()
            .asString();

        String expectedResponse = "[" +
            "    {" +
            "        \"name\": \"identity name 1\"," +
            "        \"email\": \"bob@domain.tld\"," +
            "        \"id\": \"${json-unit.ignore}\"," +
            "        \"mayDelete\": true," +
            "        \"textSignature\": \"textSignature 1\"," +
            "        \"htmlSignature\": \"htmlSignature 1\"," +
            "        \"sortOrder\": 1," +
            "        \"bcc\": [" +
            "            {" +
            "                \"name\": \"bcc name 1\"," +
            "                \"email\": \"bcc1@domain.org\"" +
            "            }" +
            "        ]," +
            "        \"replyTo\": [" +
            "            {" +
            "                \"name\": \"reply name 1\"," +
            "                \"email\": \"reply1@domain.org\"" +
            "            }" +
            "        ]" +
            "    }" +
            "]";
        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(expectedResponse);
    }

    @Test
    void listIdentitiesShouldReturnBadRequestWhenInvalidDefaultParam() {
        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        String response = given()
            .queryParam("default", "invalid")
            .get(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(io.restassured.http.ContentType.JSON)
            .extract()
            .body()
            .asString();

        assertThatJson(response)
            .isEqualTo("{" +
                "    \"statusCode\": 400," +
                "    \"type\": \"InvalidArgument\"," +
                "    \"message\": \"Invalid arguments supplied in the user request\"," +
                "    \"details\": \"Invalid 'default' query parameter\"" +
                "}");
    }

    @Test
    void listIdentitiesShouldReturnNotFoundWhenCanNotQueryDefaultIdentity() {
        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.<Identity>of()).toList());

        String response = given()
            .queryParam("default", "true")
            .get(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .contentType(io.restassured.http.ContentType.JSON)
            .extract()
            .body()
            .asString();

        assertThatJson(response)
            .isEqualTo("{" +
                "    \"statusCode\": 404," +
                "    \"type\": \"notFound\"," +
                "    \"message\": \"Default identity can not be found\"," +
                "    \"details\": null" +
                "}");
    }

    @Test
    void createIdentityShouldWork() {
        String creationRequest = "" +
            "    {" +
            "        \"name\": \"create name 1\"," +
            "        \"email\": \"bob@domain.tld\"," +
            "        \"textSignature\": \"create textSignature1\"," +
            "        \"htmlSignature\": \"create htmlSignature1\"," +
            "        \"sortOrder\": 99," +
            "        \"bcc\": [" +
            "            {" +
            "                \"name\": \"create bcc 1\"," +
            "                \"email\": \"create_boss_bcc_1@domain.tld\"" +
            "            }" +
            "        ]," +
            "        \"replyTo\": [" +
            "            {" +
            "                \"name\": \"create replyTo 1\"," +
            "                \"email\": \"create_boss1@domain.tld\"" +
            "            }" +
            "        ]" +
            "    }" +
            "";

        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        given()
            .body(creationRequest)
            .post(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.CREATED_201);

        assertThat(Flux.from(identityRepository.list(BOB)).collectList().block())
            .hasSize(2);

        // verify by api get user identities
        String response = when()
            .get(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(io.restassured.http.ContentType.JSON)
            .extract()
            .body()
            .asString();

        assertThatJson(response)
            .isArray()
            .contains("{" +
                "        \"name\": \"create name 1\"," +
                "        \"email\": \"bob@domain.tld\"," +
                "        \"mayDelete\": true," +
                "        \"textSignature\": \"create textSignature1\"," +
                "        \"htmlSignature\": \"create htmlSignature1\"," +
                "        \"sortOrder\": 99," +
                "        \"bcc\": [" +
                "            {" +
                "                \"name\": \"create bcc 1\"," +
                "                \"email\": \"create_boss_bcc_1@domain.tld\"" +
                "            }" +
                "        ]," +
                "        \"replyTo\": [" +
                "            {" +
                "                \"name\": \"create replyTo 1\"," +
                "                \"email\": \"create_boss1@domain.tld\"" +
                "            }" +
                "        ]," +
                "        \"id\": \"${json-unit.ignore}\"" +
                "    }");
    }

    @Test
    void createIdentityShouldWorkWhenMissingOptionalPropertiesInRequest() {
        String creationRequest = "" +
            "    {" +
            "        \"email\": \"bob@domain.tld\"" +
            "    }" +
            "";

        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        given()
            .body(creationRequest)
            .post(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.CREATED_201);

        assertThat(Flux.from(identityRepository.list(BOB)).collectList().block())
            .hasSize(2);

        // verify by api get user identities
        String response = when()
            .get(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(io.restassured.http.ContentType.JSON)
            .extract()
            .body()
            .asString();

        assertThatJson(response)
            .isArray()
            .contains("{" +
                "        \"name\": \"\"," +
                "        \"email\": \"bob@domain.tld\"," +
                "        \"mayDelete\": true," +
                "        \"textSignature\": \"\"," +
                "        \"htmlSignature\": \"\"," +
                "        \"sortOrder\": 100," +
                "        \"bcc\": []," +
                "        \"replyTo\": []," +
                "        \"id\": \"${json-unit.ignore}\"" +
                "    }");
    }

    @Test
    void createIdentityShouldFailWhenMissingRequirePropertyInRequest() {
        String creationRequest = "{" +
            "        \"sortOrder\": 99" +
            "    }";

        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        String response = given()
            .body(creationRequest)
            .post(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .extract()
            .body().asString();

        assertThatJson(response)
            .isEqualTo("{" +
                "    \"statusCode\": 400," +
                "    \"type\": \"InvalidArgument\"," +
                "    \"message\": \"Invalid arguments supplied in the user request\"," +
                "    \"details\": \"email must be not null\"" +
                "}");
    }

    @Test
    void createIdentityShouldFailWhenInvalidRequest() {
        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        String response = given()
            .body("invalid")
            .post(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .extract()
            .body().asString();

        assertThatJson(response)
            .isEqualTo("{" +
                "    \"statusCode\": 400," +
                "    \"type\": \"InvalidArgument\"," +
                "    \"message\": \"JSON payload of the request is not valid\"," +
                "    \"details\": \"${json-unit.ignore}\"" +
                "}");
    }

    @Test
    void createIdentityShouldNotSupportMayDeleteProperty() {
        String creationRequest = "" +
            "    {" +
            "        \"mayDelete\": \"false\"," +
            "        \"name\": \"create11\"," +
            "        \"email\": \"bob@domain.tld\"" +
            "    }" +
            "";

        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        given()
            .body(creationRequest)
            .post(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.CREATED_201);

        String response = when()
            .get(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(io.restassured.http.ContentType.JSON)
            .extract()
            .body()
            .asString();

        assertThatJson(response)
            .isArray()
            .contains("{" +
                "        \"name\": \"create11\"," +
                "        \"email\": \"bob@domain.tld\"," +
                "        \"textSignature\": \"\"," +
                "        \"htmlSignature\": \"\"," +
                "        \"sortOrder\": 100," +
                "        \"bcc\": []," +
                "        \"replyTo\": []," +
                "        \"id\": \"${json-unit.ignore}\"," +
                "        \"mayDelete\": true" +
                "    }");
    }

    @Test
    void updateIdentityShouldWork() throws Exception {
        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        IdentityCreationRequest creationRequest = IdentityCreationRequest.fromJava(
            BOB.asMailAddress(),
            Optional.of("identity name 1"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        Identity customIdentity = Mono.from(identityRepository.save(BOB, creationRequest)).block();
        String updateRequest = "" +
            "    {" +
            "        \"name\": \"identity name 1 changed\"," +
            "        \"textSignature\": \"create textSignature1\"," +
            "        \"htmlSignature\": \"create htmlSignature1\"," +
            "        \"sortOrder\": 99," +
            "        \"bcc\": [" +
            "            {" +
            "                \"name\": \"create bcc 1\"," +
            "                \"email\": \"create_boss_bcc_1@domain.tld\"" +
            "            }" +
            "        ]," +
            "        \"replyTo\": [" +
            "            {" +
            "                \"name\": \"create replyTo 1\"," +
            "                \"email\": \"create_boss1@domain.tld\"" +
            "            }" +
            "        ]" +
            "    }" +
            "";

        String customIdentityId = customIdentity.id().id().toString();

        given()
            .body(updateRequest)
            .put(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()) + "/" + customIdentityId)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        // verify by api get user identities
        String response = when()
            .get(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(io.restassured.http.ContentType.JSON)
            .extract()
            .body()
            .asString();

        assertThatJson(response)
            .isArray()
            .contains(String.format("{" +
                "        \"name\": \"identity name 1 changed\"," +
                "        \"email\": \"bob@domain.tld\"," +
                "        \"textSignature\": \"create textSignature1\"," +
                "        \"htmlSignature\": \"create htmlSignature1\"," +
                "        \"sortOrder\": 99," +
                "        \"bcc\": [" +
                "            {" +
                "                \"name\": \"create bcc 1\"," +
                "                \"email\": \"create_boss_bcc_1@domain.tld\"" +
                "            }" +
                "        ]," +
                "        \"replyTo\": [" +
                "            {" +
                "                \"name\": \"create replyTo 1\"," +
                "                \"email\": \"create_boss1@domain.tld\"" +
                "            }" +
                "        ]," +
                "        \"id\": \"%s\"," +
                "        \"mayDelete\": true" +
                "    }", customIdentityId));
    }

    @Test
    void updateIdentityShouldFailWhenIdNotFound() {
        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        String updateRequest = "" +
            "    {" +
            "        \"name\": \"identity name 1 changed\"," +
            "        \"textSignature\": \"create textSignature1\"," +
            "        \"htmlSignature\": \"create htmlSignature1\"," +
            "        \"sortOrder\": 99," +
            "        \"bcc\": [" +
            "            {" +
            "                \"name\": \"create bcc 1\"," +
            "                \"email\": \"create_boss_bcc_1@domain.tld\"" +
            "            }" +
            "        ]," +
            "        \"replyTo\": [" +
            "            {" +
            "                \"name\": \"create replyTo 1\"," +
            "                \"email\": \"create_boss1@domain.tld\"" +
            "            }" +
            "        ]" +
            "    }" +
            "";

        String notFoundIdentityId = UUID.randomUUID().toString();

        String response = given()
            .body(updateRequest)
            .put(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()) + "/" + notFoundIdentityId)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .extract()
            .body()
            .asString();

        assertThatJson(response)
            .isEqualTo(String.format("{" +
                "    \"statusCode\": 404," +
                "    \"type\": \"notFound\"," +
                "    \"message\": \"IdentityId '%s' can not be found\"," +
                "    \"details\": null" +
                "}", notFoundIdentityId));
    }

    @Test
    void updateIdentityShouldNotModifyAbsentPropertyInRequest() throws Exception {
        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        IdentityCreationRequest creationRequest = IdentityCreationRequest.fromJava(
            BOB.asMailAddress(),
            Optional.of("identity name 1"),
            Optional.of(List.of(EmailAddress.from(Optional.of("reply name 1"), new MailAddress("reply1@domain.org")))),
            Optional.empty(),
            Optional.empty(),
            Optional.of("textSignature 1"),
            Optional.empty());

        Identity customIdentity = Mono.from(identityRepository.save(BOB, creationRequest)).block();
        String updateRequest = "" +
            "    {" +
            "        \"name\": null," +
            "        \"textSignature\": \"\"," +
            "        \"htmlSignature\": \"htmlSignature 1\"," +
            "        \"bcc\": [" +
            "            {" +
            "                \"name\": \"create bcc 1\"," +
            "                \"email\": \"create_boss_bcc_1@domain.tld\"" +
            "            }" +
            "        ]," +
            "        \"replyTo\": []" +
            "    }" +
            "";

        String customIdentityId = customIdentity.id().id().toString();

        given()
            .body(updateRequest)
            .put(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()) + "/" + customIdentityId)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        String response = when()
            .get(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(io.restassured.http.ContentType.JSON)
            .extract()
            .body()
            .asString();

        assertThatJson(response)
            .isArray()
            .contains(String.format("{" +
                "    \"name\": \"identity name 1\"," +
                "    \"email\": \"bob@domain.tld\"," +
                "    \"textSignature\": \"\"," +
                "    \"htmlSignature\": \"htmlSignature 1\"," +
                "    \"sortOrder\": 100," +
                "    \"bcc\": [" +
                "        {" +
                "            \"name\": \"create bcc 1\"," +
                "            \"email\": \"create_boss_bcc_1@domain.tld\"" +
                "        }" +
                "    ]," +
                "    \"replyTo\": []," +
                "    \"id\": \"%s\"," +
                "    \"mayDelete\": true" +
                "}", customIdentityId));
    }

    @Test
    void updateIdentityShouldNotAcceptChangeMayDeleteProperty() throws Exception {
        Mockito.when(identityFactory.listIdentities(BOB))
            .thenReturn(CollectionConverters.asScala(List.of(IdentityRepositoryTest.IDENTITY1())).toList());

        IdentityCreationRequest creationRequest = IdentityCreationRequest.fromJava(
            BOB.asMailAddress(),
            Optional.of("identity name 1"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        Identity customIdentity = Mono.from(identityRepository.save(BOB, creationRequest)).block();
        String updateRequest = "" +
            "    {" +
            "        \"mayDelete\": \"false\"" +
            "    }" +
            "";

        String customIdentityId = customIdentity.id().id().toString();

        given()
            .body(updateRequest)
            .put(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()) + "/" + customIdentityId)
            .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        // verify by api get user identities
        String response = when()
            .get(String.format(GET_IDENTITIES_USERS_PATH, BOB.asString()))
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(io.restassured.http.ContentType.JSON)
            .extract()
            .body()
            .asString();

        assertThatJson(response)
            .isArray()
            .contains(String.format("{" +
                "    \"name\": \"identity name 1\"," +
                "    \"email\": \"bob@domain.tld\"," +
                "    \"textSignature\": \"\"," +
                "    \"htmlSignature\": \"\"," +
                "    \"sortOrder\": 100," +
                "    \"bcc\": []," +
                "    \"replyTo\": []," +
                "    \"id\": \"%s\"," +
                "    \"mayDelete\": true" +
                "}", customIdentityId));
    }
}
