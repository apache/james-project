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

import static io.restassured.RestAssured.when;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.is;

import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import net.javacrumbs.jsonunit.core.Option;

class MappingRoutesTest {

    public static final String ALICE_ADDRESS = "alice123@domain.tld";
    public static final String ALICE_USER = "user123@domain.tld";
    public static final String ALICE_ALIAS = "aliasuser123@domain.tld";
    public static final String ALICE_GROUP = "group123@domain.tld";
    public static final String BOB_ADDRESS = "bob456@domain.tld";
    public static final String BOB_USER = "user456@domain.tld";
    public static final String BOB_ALIAS = "aliasuser456@domain.tld";
    public static final String BOB_GROUP = "group456@domain.tld";

    private WebAdminServer webAdminServer;
    private MemoryRecipientRewriteTable recipientRewriteTable;

    @BeforeEach
    void setUp() throws Exception {
        JsonTransformer jsonTransformer = new JsonTransformer();
        recipientRewriteTable = new MemoryRecipientRewriteTable();
        MemoryDomainList domainList = new MemoryDomainList();
        domainList.addDomain(Domain.of("domain.tld"));
        domainList.addDomain(Domain.of("aliasdomain.tld"));
        domainList.addDomain(Domain.of("domain.mapping.tld"));
        domainList.addDomain(Domain.of("abc"));
        domainList.addDomain(Domain.of("xyz"));

        recipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        recipientRewriteTable.setDomainList(domainList);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        recipientRewriteTable.setUserEntityValidator(UserEntityValidator.NOOP);
        recipientRewriteTable.setUsersRepository(usersRepository);

        webAdminServer = WebAdminUtils.createWebAdminServer(new MappingRoutes(jsonTransformer, recipientRewriteTable))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(MappingRoutes.BASE_PATH)
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
    }

    @Test
    void getMappingsShouldReturnEmptyWhenNoMappings() {
        when()
            .get()
        .then()
            .contentType(ContentType.JSON)
            .statusCode(HttpStatus.OK_200)
            .body(is("{}"));
    }

    @Test
    void getMappingsShouldReturnAliasMappings() throws RecipientRewriteTableException {
        Username aliasDomain = Username.of("alias@domain.tld");

        recipientRewriteTable.addAliasMapping(
            MappingSource.fromUser(aliasDomain),
            "user@domain.tld");
        recipientRewriteTable.addAliasMapping(
            MappingSource.fromUser(aliasDomain),
            "abc@domain.tld");

        String jsonBody = when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "  \"alias@domain.tld\" : [" +
                "    {" +
                "      \"type\": \"Alias\"," +
                "      \"mapping\": \"user@domain.tld\"" +
                "    }," +
                "    {" +
                "      \"type\": \"Alias\"," +
                "      \"mapping\" : \"abc@domain.tld\"" +
                "    }" +
                "  ]" +
                "}");
    }

    @Test
    void getMappingsShouldReturnDomainMappings() throws RecipientRewriteTableException {
        Domain domain = Domain.of("aliasdomain.tld");

        recipientRewriteTable.addDomainMapping(
            MappingSource.fromDomain(domain),
            Domain.of("domain1abc.tld"));
        recipientRewriteTable.addDomainMapping(
            MappingSource.fromDomain(domain),
            Domain.of("domain2cde.tld"));

        String jsonBody = when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "  \"aliasdomain.tld\" : [" +
                "    {" +
                "      \"type\": \"Domain\"," +
                "      \"mapping\": \"domain1abc.tld\"" +
                "    }," +
                "    {" +
                "      \"type\": \"Domain\"," +
                "      \"mapping\" : \"domain2cde.tld\"" +
                "    }" +
                "  ]" +
                "}");
    }

    @Test
    void getMappingsShouldReturnDomainAliases() throws RecipientRewriteTableException {
        Domain domain = Domain.of("aliasdomain.tld");

        recipientRewriteTable.addDomainAliasMapping(
            MappingSource.fromDomain(domain),
            Domain.of("domain1abc.tld"));
        recipientRewriteTable.addDomainAliasMapping(
            MappingSource.fromDomain(domain),
            Domain.of("domain2cde.tld"));

        String jsonBody = when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "  \"aliasdomain.tld\" : [" +
                "    {" +
                "      \"type\": \"DomainAlias\"," +
                "      \"mapping\": \"domain1abc.tld\"" +
                "    }," +
                "    {" +
                "      \"type\": \"DomainAlias\"," +
                "      \"mapping\" : \"domain2cde.tld\"" +
                "    }" +
                "  ]" +
                "}");
    }

    @Test
    void getMappingsShouldReturnAddressMappings() throws Exception {
        MailAddress mailAddress = new MailAddress("group@domain.tld");

        recipientRewriteTable.addAddressMapping(
            MappingSource.fromMailAddress(mailAddress), "user123@domain.tld");
        recipientRewriteTable.addAddressMapping(
            MappingSource.fromMailAddress(mailAddress), "user789@domain.tld");

        String jsonBody = when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "  \"group@domain.tld\" : [" +
                "    {" +
                "      \"type\": \"Address\"," +
                "      \"mapping\": \"user123@domain.tld\"" +
                "    }," +
                "    {" +
                "      \"type\": \"Address\"," +
                "      \"mapping\" : \"user789@domain.tld\"" +
                "    }" +
                "  ]" +
                "}");
    }

    @Test
    void getMappingsShouldReturnGroupMappings() throws Exception {
        MailAddress groupAddress = new MailAddress("group@domain.tld");

        recipientRewriteTable.addGroupMapping(
            MappingSource.fromMailAddress(groupAddress), "member1@domain.tld");
        recipientRewriteTable.addGroupMapping(
            MappingSource.fromMailAddress(groupAddress), "member2@domain.tld");

        String jsonBody = when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "  \"group@domain.tld\": [" +
                "    {" +
                "      \"type\": \"Group\"," +
                "      \"mapping\": \"member1@domain.tld\"" +
                "    }," +
                "    {" +
                "      \"type\": \"Group\"," +
                "      \"mapping\": \"member2@domain.tld\"" +
                "    }" +
                "  ]" +
                "}");
    }

    @Test
    void getMappingsShouldReturnForwardMappings() throws RecipientRewriteTableException {
        Username forwardUsername = Username.of("forwarduser@domain.tld");

        recipientRewriteTable.addForwardMapping(
            MappingSource.fromUser(forwardUsername), "person1@domain.tld");
        recipientRewriteTable.addForwardMapping(
            MappingSource.fromUser(forwardUsername), "person2@domain.tld");

        String jsonBody = when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "  \"forwarduser@domain.tld\": [" +
                "    {" +
                "      \"type\": \"Forward\"," +
                "      \"mapping\": \"person1@domain.tld\"" +
                "    }," +
                "    {" +
                "      \"type\": \"Forward\"," +
                "      \"mapping\": \"person2@domain.tld\"" +
                "    }" +
                "  ]" +
                "}");
    }

    @Test
    void getMappingsShouldReturnRegexMappings() throws RecipientRewriteTableException {
        Username regexUsername = Username.of("regex@domain.tld");

        recipientRewriteTable.addRegexMapping(
            MappingSource.fromUser(regexUsername), "abc");
        recipientRewriteTable.addRegexMapping(
            MappingSource.fromUser(regexUsername), "def");

        String jsonBody = when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "  \"regex@domain.tld\": [" +
                "    {" +
                "      \"type\": \"Regex\"," +
                "      \"mapping\": \"abc\"" +
                "    }," +
                "    {" +
                "      \"type\": \"Regex\"," +
                "      \"mapping\": \"def\"" +
                "    }" +
                "  ]" +
                "}");
    }

    @Test
    void getMappingsShouldReturnErrorMappings() throws RecipientRewriteTableException {
        Username errorUsername = Username.of("error@domain.tld");

        recipientRewriteTable.addErrorMapping(
            MappingSource.fromUser(errorUsername), "Error 123");
        recipientRewriteTable.addErrorMapping(
            MappingSource.fromUser(errorUsername), "Error 456");

        String jsonBody = when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "  \"error@domain.tld\": [" +
                "    {" +
                "      \"type\": \"Error\"," +
                "      \"mapping\": \"Error 123\"" +
                "    }," +
                "    {" +
                "      \"type\": \"Error\"," +
                "      \"mapping\": \"Error 456\"" +
                "    }" +
                "  ]" +
                "}");
    }

    @Test
    void getMappingsShouldReturnAllMappings() throws Exception {
        MailAddress mailAddress = new MailAddress("address@domain.tld");

        recipientRewriteTable.addAliasMapping(
            MappingSource.fromUser(Username.of("alias@domain.tld")),
            "user@domain.tld");

        recipientRewriteTable.addDomainMapping(
            MappingSource.fromDomain(Domain.of("domain.mapping.tld")),
            Domain.of("realdomain.tld"));

        recipientRewriteTable.addDomainAliasMapping(
            MappingSource.fromDomain(Domain.of("aliasdomain.tld")),
            Domain.of("realdomain.tld"));

        recipientRewriteTable.addAddressMapping(
            MappingSource.fromMailAddress(mailAddress), "user@domain.tld");

        recipientRewriteTable.addGroupMapping(
            MappingSource.fromUser(Username.of("group@domain.tld")),
                "member1@domain.tld");

        recipientRewriteTable.addForwardMapping(
            MappingSource.fromUser(Username.of("forward@domain.tld")),
                "abc@domain.tld");

        recipientRewriteTable.addRegexMapping(
            MappingSource.fromUser(Username.of("regex@domain.tld")), "abc");

        recipientRewriteTable.addErrorMapping(
            MappingSource.fromUser(Username.of("error@domain.tld")), "Error 456");

        String jsonBody = when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .isEqualTo("{" +
                "  \"alias@domain.tld\": [" +
                "    {" +
                "      \"type\": \"Alias\"," +
                "      \"mapping\": \"user@domain.tld\"" +
                "    }" +
                "  ]," +
                "  \"domain.mapping.tld\": [" +
                "    {" +
                "      \"type\": \"Domain\"," +
                "      \"mapping\": \"realdomain.tld\"" +
                "    }" +
                "  ]," +
                "  \"aliasdomain.tld\": [" +
                "    {" +
                "      \"type\": \"DomainAlias\"," +
                "      \"mapping\": \"realdomain.tld\"" +
                "    }" +
                "  ]," +
                "  \"address@domain.tld\": [" +
                "    {" +
                "      \"type\": \"Address\"," +
                "      \"mapping\": \"user@domain.tld\"" +
                "    }" +
                "  ]," +
                "  \"group@domain.tld\": [" +
                "    {" +
                "      \"type\": \"Group\"," +
                "      \"mapping\": \"member1@domain.tld\"" +
                "    }" +
                "  ]," +
                "  \"forward@domain.tld\": [" +
                "    {" +
                "      \"type\": \"Forward\"," +
                "      \"mapping\": \"abc@domain.tld\"" +
                "    }" +
                "  ]," +
                "  \"regex@domain.tld\": [" +
                "    {" +
                "      \"type\": \"Regex\"," +
                "      \"mapping\": \"abc\"" +
                "    }" +
                "  ]," +
                "  \"error@domain.tld\": [" +
                "    {" +
                "      \"type\": \"Error\"," +
                "      \"mapping\": \"Error 456\"" +
                "    }" +
                "  ]" +
                "}"
            );
    }

     @Test
    void getUserMappingsShouldReturnNotFoundByDefault() {
        when()
            .get("/user/")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(HttpStatus.NOT_FOUND_404));
    }

    @Test
    void getUserMappingsShouldBeEmptyWhenUserIsNotFound() {
        when()
            .get("/user/randomuser@domain.tld")
        .then()
            .contentType(ContentType.JSON)
            .statusCode(HttpStatus.OK_200)
            .body(is("[]"));
    }

    @Test
    void getUserMappingsShouldReturnCorrespondingMappingsFromUsername() throws Exception {
        recipientRewriteTable.addAddressMapping(
            MappingSource.fromUser(Username.of(ALICE_ADDRESS)), ALICE_USER);
        recipientRewriteTable.addAliasMapping(
            MappingSource.fromUser(Username.of(ALICE_ADDRESS)), ALICE_ALIAS);
        recipientRewriteTable.addGroupMapping(
            MappingSource.fromUser(Username.of(ALICE_ADDRESS)), ALICE_GROUP);

        recipientRewriteTable.addAddressMapping(
            MappingSource.fromUser(Username.of(BOB_ADDRESS)), BOB_USER);
        recipientRewriteTable.addAliasMapping(
            MappingSource.fromUser(Username.of(BOB_ADDRESS)), BOB_ALIAS);
        recipientRewriteTable.addGroupMapping(
            MappingSource.fromUser(Username.of(BOB_ADDRESS)), BOB_GROUP);

        String jsonBody = when()
                .get("/user/alice123@domain.tld")
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("[" +
                "  {" +
                "    \"type\": \"Address\"," +
                "    \"mapping\": \"user123@domain.tld\"" +
                "  }," +
                "  {" +
                "    \"type\": \"Alias\"," +
                "    \"mapping\": \"aliasuser123@domain.tld\"" +
                "  }," +
                "  {" +
                "    \"type\": \"Group\"," +
                "    \"mapping\": \"group123@domain.tld\"" +
                "  }" +
                "]");
    }

    @Test
    void getUserMappingsShouldReturnSameMappingsWhenParametersInUpperCase() throws RecipientRewriteTableException {
        recipientRewriteTable.addAddressMapping(
            MappingSource.fromUser(Username.of(ALICE_ADDRESS)), ALICE_USER);
        recipientRewriteTable.addAliasMapping(
            MappingSource.fromUser(Username.of(ALICE_ADDRESS)), ALICE_ALIAS);
        recipientRewriteTable.addGroupMapping(
            MappingSource.fromUser(Username.of(ALICE_ADDRESS)), ALICE_GROUP);

        recipientRewriteTable.addAddressMapping(
            MappingSource.fromUser(Username.of(BOB_ADDRESS)), BOB_USER);
        recipientRewriteTable.addAliasMapping(
            MappingSource.fromUser(Username.of(BOB_ADDRESS)), BOB_ALIAS);
        recipientRewriteTable.addGroupMapping(
            MappingSource.fromUser(Username.of(BOB_ADDRESS)), BOB_GROUP);

        String jsonBody = when()
                .get("/user/AliCE123@domain.tld")
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("[" +
                "  {" +
                "    \"type\": \"Address\"," +
                "    \"mapping\": \"user123@domain.tld\"" +
                "  }," +
                "  {" +
                "    \"type\": \"Alias\"," +
                "    \"mapping\": \"aliasuser123@domain.tld\"" +
                "  }," +
                "  {" +
                "    \"type\": \"Group\"," +
                "    \"mapping\": \"group123@domain.tld\"" +
                "  }" +
                "]");
    }

    @Test
    void getUserMappingShouldReturnBadRequestWhenInvalidParameter() {
        when()
            .get("/user/alice123@domain@domain.tld")
        .then()
            .contentType((ContentType.JSON))
            .statusCode(HttpStatus.BAD_REQUEST_400)
        .body("statusCode", is(400))
        .body("type", is("InvalidArgument"))
        .body("message", is("Invalid arguments supplied in the user request"))
        .body("details", is("Domain parts ASCII chars must be a-z A-Z 0-9 - or _"));
    }

    @Test
    void getUserMappingShouldReturnEmptyWhenNoDomainOnUserParameter() throws RecipientRewriteTableException {
        recipientRewriteTable.addAddressMapping(
            MappingSource.fromUser(Username.of(ALICE_ADDRESS)), ALICE_USER);
        recipientRewriteTable.addAliasMapping(
            MappingSource.fromUser(Username.of(ALICE_ADDRESS)), ALICE_ALIAS);
        recipientRewriteTable.addGroupMapping(
            MappingSource.fromUser(Username.of(ALICE_ADDRESS)), ALICE_GROUP);

        String jsonBody = when()
                .get("/user/alice")
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
            .extract()
                .body()
                .asString();

        assertThatJson(jsonBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("[]");
    }
    
}