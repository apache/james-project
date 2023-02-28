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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.identity.DefaultIdentitySupplier;
import org.apache.james.jmap.api.identity.IdentityCreationRequest;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.api.model.EmailAddress;
import org.apache.james.jmap.api.model.Identity;
import org.apache.james.jmap.memory.identity.MemoryCustomIdentityDAO;
import org.apache.james.json.DTOConverter;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
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
            .thenReturn(CollectionConverters.asScala(List.of(UserIdentitiesHelper.IDENTITY1())).toList());

        IdentityCreationRequest creationRequest = IdentityCreationRequest.fromJava(
            BOB.asMailAddress(),
            Optional.of("identity name 1"),
            Optional.of(EmailAddress.from(new MailboxList(
                new Mailbox("replyTo1", "james.org"),
                new Mailbox("replyTo2", "james.org")))),
            Optional.of(EmailAddress.from(new MailboxList(
                new Mailbox("bcc1", "james.org"),
                new Mailbox("bcc2", "james.org")))),
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
            "        \"name\": \"base name\"," +
            "        \"email\": \"bob@domain.tld\"," +
            "        \"id\": \"${json-unit.ignore}\"," +
            "        \"mayDelete\": false," +
            "        \"textSignature\": \"text signature base\"," +
            "        \"htmlSignature\": \"html signature base\"," +
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
            .thenReturn(CollectionConverters.asScala(List.of(UserIdentitiesHelper.IDENTITY1())).toList());

        Integer highPriorityOrder = 1;
        Integer lowPriorityOrder = 2;
        IdentityCreationRequest creationRequest1 = IdentityCreationRequest.fromJava(
            BOB.asMailAddress(),
            Optional.of("identity name 1"),
            Optional.of(UserIdentitiesHelper.emailAddressFromJava("reply name 1", new MailAddress("reply1@domain.org"))),
            Optional.of(UserIdentitiesHelper.emailAddressFromJava("bcc name 1", new MailAddress("bcc1@domain.org"))),
            Optional.of(highPriorityOrder),
            Optional.of("textSignature 1"),
            Optional.of("htmlSignature 1"));

        IdentityCreationRequest creationRequest2 = IdentityCreationRequest.fromJava(
            BOB.asMailAddress(),
            Optional.of("identity name 2"),
            Optional.of(UserIdentitiesHelper.emailAddressFromJava("reply name 2", new MailAddress("reply2@domain.org"))),
            Optional.of(UserIdentitiesHelper.emailAddressFromJava("bcc name 2", new MailAddress("bcc2@domain.org"))),
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
            .thenReturn(CollectionConverters.asScala(List.of(UserIdentitiesHelper.IDENTITY1())).toList());

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
}
