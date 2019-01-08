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

import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class AliasRoutesTest {

    private static final Domain DOMAIN = Domain.of("b.com");
    public static final String BOB = "bob@" + DOMAIN.name();
    public static final String BOB_WITH_SLASH = "bob/@" + DOMAIN.name();
    public static final String BOB_WITH_ENCODED_SLASH = "bob%2F@" + DOMAIN.name();
    public static final String BOB_ALIAS = "bob-alias@" + DOMAIN.name();
    public static final String BOB_ALIAS_2 = "bob-alias2@" + DOMAIN.name();
    public static final String BOB_ALIAS_WITH_SLASH = "bob-alias/@" + DOMAIN.name();
    public static final String BOB_ALIAS_WITH_ENCODED_SLASH = "bob-alias%2F@" + DOMAIN.name();
    public static final String ALICE = "alice@" + DOMAIN.name();
    public static final String BOB_PASSWORD = "123456";
    public static final String BOB_WITH_SLASH_PASSWORD = "abcdef";
    public static final String ALICE_PASSWORD = "789123";

    private static final MappingSource BOB_SOURCE = MappingSource.fromUser("bob", DOMAIN);
    private static final MappingSource BOB_WITH_ENCODED_SLASH_SOURCE = MappingSource.fromUser("bob/", DOMAIN);
    private static final Mapping BOB_MAPPING = Mapping.alias(BOB_ALIAS);

    private WebAdminServer webAdminServer;

    private void createServer(AliasRoutes aliasRoutes) throws Exception {
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            aliasRoutes);
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("address/aliases")
            .log(LogDetail.METHOD)
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
    }

    @Nested
    class NormalBehaviour {

        MemoryUsersRepository usersRepository;
        MemoryDomainList domainList;
        MemoryRecipientRewriteTable memoryRecipientRewriteTable;

        @BeforeEach
        void setUp() throws Exception {
            memoryRecipientRewriteTable = new MemoryRecipientRewriteTable();
            DNSService dnsService = mock(DNSService.class);
            domainList = new MemoryDomainList(dnsService);
            domainList.configure(DomainListConfiguration.builder()
                .autoDetect(false)
                .autoDetectIp(false));
            domainList.addDomain(DOMAIN);

            usersRepository = MemoryUsersRepository.withVirtualHosting();
            usersRepository.setDomainList(domainList);
            usersRepository.configure(new DefaultConfigurationBuilder());

            usersRepository.addUser(BOB, BOB_PASSWORD);
            usersRepository.addUser(BOB_WITH_SLASH, BOB_WITH_SLASH_PASSWORD);
            usersRepository.addUser(ALICE, ALICE_PASSWORD);

            createServer(new AliasRoutes(memoryRecipientRewriteTable, usersRepository));
        }

        @Test
        void putAliasForUserShouldReturnNoContent() {
            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putAliasShouldBeIdempotent() {
            given()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putAliasWithSlashForUserShouldReturnNoContent() {
            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS_WITH_ENCODED_SLASH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putUserForAliasWithEncodedSlashShouldReturnNoContent() {
            when()
                .put(BOB_WITH_ENCODED_SLASH + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putExistingUserAsAliasSourceShouldNotBePossible() {
            Map<String, Object> errors = when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + ALICE)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "The alias source exists as an user already");
        }

        @Test
        void putAliasForUserShouldCreateAlias() {
            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            assertThat(memoryRecipientRewriteTable.getStoredMappings(BOB_SOURCE)).containsOnly(BOB_MAPPING);
        }

        @Test
        void putAliasWithEncodedSlashForUserShouldAddItAsADestination() {
            Mapping mapping = Mapping.alias(BOB_ALIAS_WITH_SLASH);

            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS_WITH_ENCODED_SLASH);

            assertThat(memoryRecipientRewriteTable.getStoredMappings(BOB_SOURCE)).containsOnly(mapping);
        }

        @Test
        void putAliasForUserWithEncodedSlashShouldCreateForward() {
            with()
                .put(BOB_WITH_ENCODED_SLASH + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            assertThat(memoryRecipientRewriteTable.getStoredMappings(BOB_WITH_ENCODED_SLASH_SOURCE)).containsOnly(BOB_MAPPING);
        }

        @Test
        void putSameAliasForUserTwiceShouldBeIdempotent() {
            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            assertThat(memoryRecipientRewriteTable.getStoredMappings(BOB_SOURCE)).containsOnly(BOB_MAPPING);
        }

        @Test
        void putAliasForUserShouldAllowSeveralSources() {
            Mapping mapping2 = Mapping.alias(BOB_ALIAS_2);

            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS);

            with()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS_2);

            assertThat(memoryRecipientRewriteTable.getStoredMappings(BOB_SOURCE)).containsOnly(BOB_MAPPING, mapping2);
        }
    }

    @Nested
    class FilteringOtherRewriteRuleTypes extends NormalBehaviour {

        @BeforeEach
        void setup() throws Exception {
            super.setUp();
            memoryRecipientRewriteTable.addErrorMapping(MappingSource.fromUser("error", DOMAIN), "disabled");
            memoryRecipientRewriteTable.addRegexMapping(MappingSource.fromUser("regex", DOMAIN), ".*@b\\.com");
            memoryRecipientRewriteTable.addAliasDomainMapping(MappingSource.fromDomain(Domain.of("alias")), DOMAIN);
        }

    }

    @Nested
    class ExceptionHandling {

        private RecipientRewriteTable memoryRecipientRewriteTable;

        @BeforeEach
        void setUp() throws Exception {
            memoryRecipientRewriteTable = mock(RecipientRewriteTable.class);
            UsersRepository userRepository = mock(UsersRepository.class);
            DomainList domainList = mock(DomainList.class);
            Mockito.when(domainList.containsDomain(any())).thenReturn(true);
            createServer(new AliasRoutes(memoryRecipientRewriteTable, userRepository));
        }

        @Test
        void putMalformedUserDestinationShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .put("not-an-address" + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "The alias is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void putMalformedAliasSourceShouldReturnBadRequest() {
            Map<String, Object> errors = when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + "not-an-address")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "The alias is not an email address")
                .containsEntry("details", "Out of data at position 1 in 'not-an-address'");
        }

        @Test
        void putUserDestinationInForwardWithSlashShouldReturnNotFound() {
            when()
                .put(BOB_WITH_SLASH + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putAliasSourceWithSlashShouldReturnNotFound() {
            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS_WITH_SLASH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putRequiresTwoPathParams() {
            when()
                .put(BOB)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putShouldReturnErrorWhenRecipientRewriteTableExceptionIsThrown() throws Exception {
            doThrow(RecipientRewriteTableException.class)
                .when(memoryRecipientRewriteTable)
                .addAliasMapping(any(), anyString());

            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void putShouldReturnErrorWhenRuntimeExceptionIsThrown() throws Exception {
            doThrow(RuntimeException.class)
                .when(memoryRecipientRewriteTable)
                .addAliasMapping(any(), anyString());

            when()
                .put(BOB + SEPARATOR + "sources" + SEPARATOR + BOB_ALIAS)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }
    }
}
