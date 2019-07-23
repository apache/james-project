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
import static org.mockito.Mockito.mock;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import net.javacrumbs.jsonunit.core.Option;

class MappingRoutesTest {

    private WebAdminServer webAdminServer;
    private MemoryRecipientRewriteTable recipientRewriteTable;

    @BeforeEach
    void setUp() throws DomainListException {
        JsonTransformer jsonTransformer = new JsonTransformer();
        recipientRewriteTable = new MemoryRecipientRewriteTable();
        DNSService dnsService = mock(DNSService.class);
        DomainList domainList = new MemoryDomainList(dnsService);
        domainList.addDomain(Domain.of("domain.tld"));
        domainList.addDomain(Domain.of("aliasdomain.tld"));

        recipientRewriteTable.setDomainList(domainList);

        webAdminServer = WebAdminUtils.createWebAdminServer(new MappingRoutes(jsonTransformer, recipientRewriteTable))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(MappingRoutes.BASE_PATH)
            .log(LogDetail.METHOD)
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
        User aliasDomain = User.fromUsername("alias@domain.tld");

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
    void getMappingsShouldReturnAliasDomainMappings() throws RecipientRewriteTableException {
        Domain domain = Domain.of("aliasdomain.tld");

        recipientRewriteTable.addAliasDomainMapping(
            MappingSource.fromDomain(domain),
            Domain.of("domain1abc.tld"));
        recipientRewriteTable.addAliasDomainMapping(
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
        User forwardUser = User.fromUsername("forwarduser@domain.tld");

        recipientRewriteTable.addForwardMapping(
            MappingSource.fromUser(forwardUser), "person1@domain.tld");
        recipientRewriteTable.addForwardMapping(
            MappingSource.fromUser(forwardUser), "person2@domain.tld");

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
        User regexUser = User.fromUsername("regex@domain.tld");

        recipientRewriteTable.addRegexMapping(
            MappingSource.fromUser(regexUser), "abc");
        recipientRewriteTable.addRegexMapping(
            MappingSource.fromUser(regexUser), "def");

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
        User errorUser = User.fromUsername("error@domain.tld");

        recipientRewriteTable.addErrorMapping(
            MappingSource.fromUser(errorUser), "Error 123");
        recipientRewriteTable.addErrorMapping(
            MappingSource.fromUser(errorUser), "Error 456");

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
            MappingSource.fromUser(User.fromUsername("alias@domain.tld")),
            "user@domain.tld");

        recipientRewriteTable.addAliasDomainMapping(
            MappingSource.fromDomain(Domain.of("aliasdomain.tld")),
            Domain.of("realdomain.tld"));

        recipientRewriteTable.addAddressMapping(
            MappingSource.fromMailAddress(mailAddress), "user@domain.tld");

        recipientRewriteTable.addGroupMapping(
            MappingSource.fromUser(User.fromUsername("group@domain.tld")),
                "member1@domain.tld");

        recipientRewriteTable.addForwardMapping(
            MappingSource.fromUser(User.fromUsername("forward@domain.tld")),
                "abc@domain.tld");

        recipientRewriteTable.addRegexMapping(
            MappingSource.fromUser(User.fromUsername("regex@domain.tld")), "abc");

        recipientRewriteTable.addErrorMapping(
            MappingSource.fromUser(User.fromUsername("error@domain.tld")), "Error 456");

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
                "  \"aliasdomain.tld\": [" +
                "    {" +
                "      \"type\": \"Domain\"," +
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
}