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
import static io.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class AddressMappingRoutesTest {
    private static final String MAPPING_SOURCE = "source@domain.tld";
    private static final String ALICE_ADDRESS = "alice@domain.tld";
    private static final String ALICE_WITH_SLASH = "alice/@domain.tld";
    private static final String ALICE_WITH_ENCODED_SLASH = "alice%2F@domain.tld";
    private static final String BOB_ADDRESS = "bob@domain.tld";

    private WebAdminServer webAdminServer;
    private MemoryRecipientRewriteTable recipientRewriteTable;

    @BeforeEach
    void setUp() throws Exception {
        recipientRewriteTable = new MemoryRecipientRewriteTable();
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(Domain.of("domain.tld"));

        recipientRewriteTable.setDomainList(domainList);
        recipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);

        webAdminServer = WebAdminUtils.createWebAdminServer(
            new AddressMappingRoutes(recipientRewriteTable))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(AddressMappingRoutes.BASE_PATH)
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
    }

    @Test
    void addAddressMappingShouldAddMappingOnRecipientRewriteTable() {
        when()
            .post(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS);

        assertThat(recipientRewriteTable.getStoredMappings(MappingSource.parse(MAPPING_SOURCE)))
            .containsAnyOf(Mapping.of(ALICE_ADDRESS));
    }

    @Test
    void addAddressMappingShouldSupportOneTimeEncodedAddress() {
        when()
            .post(MAPPING_SOURCE + "/targets/" + ALICE_WITH_ENCODED_SLASH);

        assertThat(recipientRewriteTable.getStoredMappings(MappingSource.parse(MAPPING_SOURCE)))
            .containsAnyOf(Mapping.of(ALICE_WITH_SLASH));
    }

    @Test
    void postShouldDetectLoops() {
        with()
            .post(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS);

        Map<String, Object> errors = when()
            .post(ALICE_ADDRESS + "/targets/" + MAPPING_SOURCE)
        .then()
            .contentType(ContentType.JSON)
            .statusCode(HttpStatus.CONFLICT_409)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        assertThat(errors)
            .containsEntry("statusCode", HttpStatus.CONFLICT_409)
            .containsEntry("type", "WrongState")
            .containsEntry("message", "Creation of redirection of alice@domain.tld to source@domain.tld would lead to a loop, operation not performed");
    }

    @Test
    void addAddressMappingShouldReturnNotFoundWhenOneParameterIsEmpty() {
        when()
            .post(MAPPING_SOURCE + "/targets/")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void addAddressMappingShouldReturnNoContentWhenValidParameter() {
        when()
            .post(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void addAddressMappingShouldReturnBadRequestWhenInvalidMappingSource() {
        when()
            .post("source@domain@domain/targets/" + ALICE_ADDRESS)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void addAddressMappingShouldReturnBadRequestWhenInvalidDestinationAddress() {
        when()
            .post(MAPPING_SOURCE + "/targets/alice")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void addAddressMappingShouldReturnNoContentWithDuplicatedAddress() throws Exception {
        MappingSource mappingSource =  MappingSource.fromMailAddress(new MailAddress(MAPPING_SOURCE));

        recipientRewriteTable.addAddressMapping(mappingSource, ALICE_ADDRESS);
        recipientRewriteTable.addAddressMapping(mappingSource, BOB_ADDRESS);

        when()
            .post(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void addAddressMappingShouldReturnBadRequestWhenSourceAndDestinationIsTheSame() {
        when()
            .post(MAPPING_SOURCE + "/targets/" + MAPPING_SOURCE)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void addAddressMappingShouldReturnBadRequestWhenSourceDomainNotInDomainList() {
        when()
            .post("source@example/targets/" + ALICE_ADDRESS)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void removeAddressMappingShouldRemoveDestinationAddress() {
        when()
            .post(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS);

        when()
            .delete(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS);

        assertThat(recipientRewriteTable.getStoredMappings(MappingSource.parse(MAPPING_SOURCE)))
            .doesNotContain(Mapping.of(ALICE_ADDRESS));
    }

    @Test
    void removeAddressMappingShouldReturnNoContentWhenValidParameter() {
        when()
            .post(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS);

        when()
            .delete(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void removeAddressMappingShouldReturnNoContentWhenDestinationAddressIsNotFound() {
        when()
            .post(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS);

        when()
            .delete(MAPPING_SOURCE + "/targets/" + BOB_ADDRESS)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void removeAddressMappingShouldBeIdempotent() {
        when()
            .post(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS);

        when()
            .delete(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS);

        when()
            .delete(MAPPING_SOURCE + "/targets/" + ALICE_ADDRESS)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void removeAddressMappingShouldReturnBadRequestWhenMappingSourceIsInvalid() {
        when()
            .delete("random@domain@domain/targets/" + ALICE_ADDRESS)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void removeAddressMappingShouldReturnNotFoundWhenOneParameterIsEmpty() {
        when()
            .delete(MAPPING_SOURCE + "/targets/")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }
}