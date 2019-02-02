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
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;


public class DomainsRoutesTest {
    public static final String DOMAIN = "domain";

    private WebAdminServer webAdminServer;

    private void createServer(DomainList domainList) throws Exception {
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            new DomainsRoutes(domainList, new JsonTransformer()));
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

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
