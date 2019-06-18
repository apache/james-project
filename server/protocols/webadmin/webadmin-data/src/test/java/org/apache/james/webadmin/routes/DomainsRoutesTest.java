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
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.DomainAliasService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;


class DomainsRoutesTest {
    private static final String DOMAIN = "domain";
    private static final String ALIAS_DOMAIN = "alias.domain";
    private static final String ALIAS_DOMAIN_2 = "alias.domain.bis";
    private static final String EXTERNAL_DOMAIN = "external.domain.tld";

    private WebAdminServer webAdminServer;

    private void createServer(DomainList domainList) {
        DomainAliasService domainAliasService = new DomainAliasService(new MemoryRecipientRewriteTable(), domainList);
        webAdminServer = WebAdminUtils.createWebAdminServer(new DomainsRoutes(domainList, domainAliasService, new JsonTransformer()))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(DomainsRoutes.DOMAINS)
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
    }

    @Nested
    class NormalBehaviour {

        @BeforeEach
        void setUp() throws Exception {
            DNSService dnsService = mock(DNSService.class);
            when(dnsService.getHostName(any())).thenReturn("localhost");
            when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("localhost"));

            MemoryDomainList domainList = new MemoryDomainList(dnsService);
            domainList.setAutoDetectIP(false);
            createServer(domainList);
        }

        @Test
        void getDomainsShouldBeEmptyByDefault() {
            List<String> domains =
                given()
                    .get()
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(domains).isEmpty();
        }

        @Test
        void putShouldReturnErrorWhenUsedWithEmptyDomain() {
            given()
                .put(SEPARATOR)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void deleteShouldReturnErrorWhenUsedWithEmptyDomain() {
            given()
                .delete(SEPARATOR)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putShouldBeOk() {
            given()
                .put(DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void getDomainsShouldDisplayAddedDomains() {
            with()
                .put(DOMAIN);

            List<String> domains =
                when()
                    .get()
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

            assertThat(domains).containsExactly(DOMAIN);
        }

        @Test
        void putShouldReturnUserErrorWhenNameContainsAT() {
            Map<String, Object> errors = when()
                .put(DOMAIN + "@" + DOMAIN)
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
                .containsEntry("message", "Invalid request for domain creation domain@domain");
        }

        @Test
        void putShouldReturnUserErrorWhenNameContainsUrlSeparator() {
            when()
                .put(DOMAIN + "/" + DOMAIN)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putShouldReturnUserErrorWhenNameIsTooLong() {
            Map<String, Object> errors = when()
                .put(DOMAIN + "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                    "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                    "0123456789.0123456789.0123456789.")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument");
        }

        @Test
        void putShouldWorkOnTheSecondTimeForAGivenValue() {
            with()
                .put(DOMAIN);

            when()
                .put(DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void deleteShouldRemoveTheGivenDomain() {
            with()
                .put(DOMAIN);

            when()
                .delete(DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            List<String> domains =
                when()
                    .get()
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

           assertThat(domains).isEmpty();
        }

        @Test
        void deleteShouldBeOkWhenTheDomainIsNotPresent() {
            given()
                .delete(DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void getDomainShouldReturnOkWhenTheDomainIsPresent() {
            with()
                .put(DOMAIN);

            when()
                .get(DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void getDomainShouldReturnNotFoundWhenTheDomainIsAbsent() {
            given()
                .get(DOMAIN)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Nested
        class DomainAlias {
            @Test
            void getAliasesShouldReturnNotFoundWhenDomainDoesNotExist() {
                when()
                    .get(DOMAIN + "/aliases")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                    .body("type", is("InvalidArgument"))
                    .body("message", is("The following domain is not in the domain list and has no registered local aliases: domain"));
            }

            @Test
            void getAliasesShouldReturnEmptyWhenNone() {
                with().put(DOMAIN);

                when()
                    .get(DOMAIN + "/aliases")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .body(".", hasSize(0));
            }

            @Test
            void getAliasesShouldReturnCreatedAliases() {
                with().put(DOMAIN);
                with().put(ALIAS_DOMAIN);
                with().put(ALIAS_DOMAIN_2);

                with().put(DOMAIN + "/aliases/" + ALIAS_DOMAIN);
                with().put(DOMAIN + "/aliases/" + ALIAS_DOMAIN_2);

                when()
                    .get(DOMAIN + "/aliases")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .body("source", containsInAnyOrder(ALIAS_DOMAIN, ALIAS_DOMAIN_2));
            }

            @Test
            void putShouldBeIdempotent() {
                with().put(DOMAIN);
                with().put(ALIAS_DOMAIN);

                with().put(DOMAIN + "/aliases/" + ALIAS_DOMAIN);
                with().put(DOMAIN + "/aliases/" + ALIAS_DOMAIN);

                when()
                    .get(DOMAIN + "/aliases")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .body("source", containsInAnyOrder(ALIAS_DOMAIN));
            }

            @Test
            void deleteShouldNotFailOnNonExistingEvents() {
                with().put(DOMAIN);
                with().put(ALIAS_DOMAIN);

                with().delete(DOMAIN + "/aliases/" + ALIAS_DOMAIN);

                when()
                    .get(DOMAIN + "/aliases")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .body("", hasSize(0));
            }

            @Test
            void putShouldLowercaseDomain() {
                with().put(DOMAIN);
                with().put(ALIAS_DOMAIN);

                with().put(DOMAIN + "/aliases/" + "Alias.Domain");

                when()
                    .get(DOMAIN + "/aliases")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .body("source", containsInAnyOrder(ALIAS_DOMAIN));
            }

            @Test
            void getAliasesShouldNotReturnDeletedAliases() {
                with().put(DOMAIN);
                with().put(ALIAS_DOMAIN);

                with().put(DOMAIN + "/aliases/" + ALIAS_DOMAIN);
                with().delete(DOMAIN + "/aliases/" + ALIAS_DOMAIN);

                when()
                    .get(DOMAIN + "/aliases")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .body(".", hasSize(0));
            }

            @Test
            void deleteShouldReturnNotFoundWhenAliasDomainDoesNotExist() {
                with().put(DOMAIN);

                when()
                    .delete(DOMAIN + "/aliases/" + ALIAS_DOMAIN)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                    .body("type", is("InvalidArgument"))
                    .body("message", is("The domain list does not contain: " + ALIAS_DOMAIN));
            }

            @Test
            void putShouldReturnNotFoundWhenAliasDomainDoesNotExist() {
                with().put(DOMAIN);

                when()
                    .put(DOMAIN + "/aliases/" + ALIAS_DOMAIN)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                    .body("type", is("InvalidArgument"))
                    .body("message", is("The domain list does not contain: " + ALIAS_DOMAIN));
            }

            @Test
            void putShouldReturnBadRequestWhenSourceAndDestinationAreTheSame() {
                with().put(DOMAIN);

                when()
                    .put(DOMAIN + "/aliases/" + DOMAIN)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is("InvalidArgument"))
                    .body("message", is("Source domain and destination domain can not have same value(" + DOMAIN + ")"));
            }

            @Test
            void putShouldNotFailOnExternalDomainAlias() {
                with().put(DOMAIN);

                when()
                    .put(EXTERNAL_DOMAIN + "/aliases/" + DOMAIN).prettyPeek()
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.NO_CONTENT_204);
            }

            @Test
            void deleteShouldNotFailOnExternalDomainDestinationForAnAlias() {
                with().put(DOMAIN);

                when()
                    .delete(EXTERNAL_DOMAIN + "/aliases/" + DOMAIN)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.NO_CONTENT_204);
            }

            @Test
            void getAliasesShouldListAliasesForExternalDomains() {
                with().put(DOMAIN);

                with().put(EXTERNAL_DOMAIN + "/aliases/" + DOMAIN);

                when()
                    .get(EXTERNAL_DOMAIN + "/aliases")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .body("source", containsInAnyOrder(DOMAIN));
            }

            @Test
            void deleteShouldRemoveExternalDomainAlias() {
                with().put(DOMAIN);

                with().put(EXTERNAL_DOMAIN + "/aliases/" + DOMAIN);

                with().delete(EXTERNAL_DOMAIN + "/aliases/" + DOMAIN);

                when()
                    .get(DOMAIN + "/aliases")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .body("source", hasSize(0));
            }

            @Test
            void putShouldReturnBadRequestWhenDestinationDomainIsInvalid() {
                when()
                    .put("invalid@domain/aliases/" + ALIAS_DOMAIN)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is("InvalidArgument"))
                    .body("message", is("Invalid request for domain creation invalid@domain"));
            }

            @Test
            void putShouldReturnBadRequestWhenSourceDomainIsInvalid() {
                when()
                    .put("domain/aliases/invalid@alias.domain")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is("InvalidArgument"))
                    .body("message", is("Invalid request for domain creation invalid@alias.domain"));
            }

            @Test
            void deleteShouldReturnBadRequestWhenDestinationDomainIsInvalid() {
                when()
                    .delete("invalid@domain/aliases/" + ALIAS_DOMAIN)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is("InvalidArgument"))
                    .body("message", is("Invalid request for domain creation invalid@domain"));
            }

            @Test
            void deleteShouldReturnBadRequestWhenSourceDomainIsInvalid() {
                when()
                    .delete("domain/aliases/invalid@alias.domain")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is("InvalidArgument"))
                    .body("message", is("Invalid request for domain creation invalid@alias.domain"));
            }

            @Test
            void getAliasesShouldReturnBadRequestWhenDomainIsInvalid() {
                when()
                    .get("invalid@domain/aliases")
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is("InvalidArgument"))
                    .body("message", is("Invalid request for domain creation invalid@domain"));
            }

            @Test
            void deleteShouldReturnBadRequestWhenSourceAndDestinationAreTheSame() {
                with().put(DOMAIN);

                when()
                    .delete(DOMAIN + "/aliases/" + DOMAIN)
                .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
                    .body("type", is("InvalidArgument"))
                    .body("message", is("Source domain and destination domain can not have same value(" + DOMAIN + ")"));
            }

            @Test
            void deleteSourceDomainShouldRemoveTheCorrespondingAlias() {
                with().put(ALIAS_DOMAIN_2);
                with().put(ALIAS_DOMAIN);

                with().put(DOMAIN + "/aliases/" + ALIAS_DOMAIN);
                with().put(DOMAIN + "/aliases/" + ALIAS_DOMAIN_2);

                with().delete(ALIAS_DOMAIN_2);

                when()
                    .get(DOMAIN + "/aliases")
                    .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .body("source", containsInAnyOrder(ALIAS_DOMAIN));
            }

            @Test
            void deleteDestinationDomainShouldHaveNoImpactOnAliasesAliases() {
                with().put(DOMAIN);
                with().put(ALIAS_DOMAIN);

                with().put(DOMAIN + "/aliases/" + ALIAS_DOMAIN);

                with().delete(DOMAIN);

                when()
                    .get(DOMAIN + "/aliases")
                    .then()
                    .contentType(ContentType.JSON)
                    .statusCode(HttpStatus.OK_200)
                    .body("source", containsInAnyOrder(ALIAS_DOMAIN));
            }
        }

    }

    @Nested
    class ExceptionHandling {

        private DomainList domainList;
        private Domain domain;

        @BeforeEach
        void setUp() throws Exception {
            domainList = mock(DomainList.class);
            createServer(domainList);
            domain = Domain.of("domain");
        }

        @Test
        void deleteShouldReturnErrorOnUnknownException() throws Exception {
            doThrow(new RuntimeException()).when(domainList).removeDomain(domain);

            when()
                .delete(DOMAIN)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void putShouldReturnErrorOnUnknownException() throws Exception {
            doThrow(new RuntimeException()).when(domainList).addDomain(domain);

            when()
                .put(DOMAIN)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getDomainShouldReturnErrorOnUnknownException() throws Exception {
            when(domainList.containsDomain(domain)).thenThrow(new RuntimeException());

            when()
                .get(DOMAIN)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getDomainsShouldReturnErrorOnUnknownException() throws Exception {
            when(domainList.getDomains()).thenThrow(new RuntimeException());

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void deleteShouldReturnOkWhenDomainListException() throws Exception {
            doThrow(new DomainListException("message")).when(domainList).removeDomain(domain);

            when()
                .delete(DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void putShouldReturnOkWhenDomainListException() throws Exception {
            doThrow(new DomainListException("message")).when(domainList).addDomain(domain);

            when()
                .put(DOMAIN)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void getDomainShouldReturnErrorOnDomainListException() throws Exception {
            when(domainList.containsDomain(domain)).thenThrow(new DomainListException("message"));

            when()
                .get(DOMAIN)
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

        @Test
        void getDomainsShouldReturnErrorOnDomainListException() throws Exception {
            when(domainList.getDomains()).thenThrow(new DomainListException("message"));

            when()
                .get()
            .then()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }

    }

}
