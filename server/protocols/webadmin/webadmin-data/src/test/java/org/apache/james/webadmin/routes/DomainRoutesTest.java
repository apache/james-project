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

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

import de.bechte.junit.runners.context.HierarchicalContextRunner;

@RunWith(HierarchicalContextRunner.class)
public class DomainRoutesTest {
    public static final String DOMAIN = "domain";

    private WebAdminServer webAdminServer;

    private void createServer(DomainList domainList) throws Exception {
        webAdminServer = new WebAdminServer(
            new DefaultMetricFactory(),
            new DomainRoutes(domainList, new JsonTransformer()));
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = new RequestSpecBuilder()
        		.setContentType(ContentType.JSON)
        		.setAccept(ContentType.JSON)
        		.setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8)))
        		.setPort(webAdminServer.getPort().toInt())
        		.setBasePath(DomainRoutes.DOMAINS)
        		.build();

    }

    @After
    public void stop() {
        webAdminServer.destroy();
    }

    public class NormalBehaviour {

        @Before
        public void setUp() throws Exception {
            DNSService dnsService = mock(DNSService.class);
            when(dnsService.getHostName(any())).thenReturn("localhost");
            when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("localhost"));

            MemoryDomainList domainList = new MemoryDomainList(dnsService);
            domainList.setAutoDetectIP(false);
            createServer(domainList);
        }

        @Test
        public void getDomainsShouldBeEmptyByDefault() {
            given()
                .get()
            .then()
                .statusCode(200)
                .body(is("[]"));
        }

        @Test
        public void putShouldReturnErrorWhenUsedWithEmptyDomain() {
            given()
                .put(SEPARATOR)
            .then()
                .statusCode(404);
        }

        @Test
        public void deleteShouldReturnErrorWhenUsedWithEmptyDomain() {
            given()
                .delete(SEPARATOR)
            .then()
                .statusCode(404);
        }

        @Test
        public void putShouldBeOk() {
            given()
                .put(DOMAIN)
            .then()
                .statusCode(204);
        }

        @Test
        public void getDomainsShouldDisplayAddedDomains() {
            with()
                .put(DOMAIN);

            when()
                .get()
            .then()
                .statusCode(200)
                .body(containsString(DOMAIN));
        }

        @Test
        public void putShouldReturnUserErrorWhenNameContainsAT() {
            when()
                .put(DOMAIN + "@" + DOMAIN)
            .then()
                .statusCode(400);
        }

        @Test
        public void putShouldReturnUserErrorWhenNameContainsUrlSeparator() {
            when()
                .put(DOMAIN + "/" + DOMAIN)
            .then()
                .statusCode(404);
        }

        @Test
        public void putShouldReturnUserErrorWhenNameIsTooLong() {
            when()
                .put(DOMAIN + "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                    "0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789.0123456789." +
                    "0123456789.0123456789.0123456789.")
            .then()
                .statusCode(400);
        }

        @Test
        public void putShouldWorkOnTheSecondTimeForAGivenValue() {
            with()
                .put(DOMAIN);

            when()
                .put(DOMAIN)
            .then()
                .statusCode(204);
        }

        @Test
        public void deleteShouldRemoveTheGivenDomain() {
            with()
                .put(DOMAIN);

            when()
                .delete(DOMAIN)
            .then()
                .statusCode(204);

            when()
                .get()
            .then()
                .statusCode(200)
                .body(is("[]"));
        }

        @Test
        public void deleteShouldBeOkWhenTheDomainIsNotPresent() {
            given()
                .delete(DOMAIN)
            .then()
                .statusCode(204);
        }

        @Test
        public void getDomainShouldReturnOkWhenTheDomainIsPresent() {
            with()
                .put(DOMAIN);

            when()
                .get(DOMAIN)
            .then()
                .statusCode(204);
        }

        @Test
        public void getDomainShouldReturnNotFoundWhenTheDomainIsAbsent() {
            given()
                .get(DOMAIN)
            .then()
                .statusCode(404);
        }

    }

    public class ExceptionHandling {

        private DomainList domainList;
        private String domain;

        @Before
        public void setUp() throws Exception {
            domainList = mock(DomainList.class);
            createServer(domainList);
            domain = "domain";
        }

        @Test
        public void deleteShouldReturnErrorOnUnknownException() throws Exception {
            doThrow(new RuntimeException()).when(domainList).removeDomain(domain);

            when()
                .delete(DOMAIN)
            .then()
                .statusCode(500);
        }

        @Test
        public void putShouldReturnErrorOnUnknownException() throws Exception {
            doThrow(new RuntimeException()).when(domainList).addDomain(domain);

            when()
                .put(DOMAIN)
            .then()
                .statusCode(500);
        }

        @Test
        public void getDomainShouldReturnErrorOnUnknownException() throws Exception {
            when(domainList.containsDomain(domain)).thenThrow(new RuntimeException());

            when()
                .get(DOMAIN)
            .then()
                .statusCode(500);
        }

        @Test
        public void getDomainsShouldReturnErrorOnUnknownException() throws Exception {
            when(domainList.getDomains()).thenThrow(new RuntimeException());

            when()
                .get()
            .then()
                .statusCode(500);
        }

        @Test
        public void deleteShouldReturnOkWhenDomainListException() throws Exception {
            doThrow(new DomainListException("message")).when(domainList).removeDomain(domain);

            when()
                .delete(DOMAIN)
            .then()
                .statusCode(204);
        }

        @Test
        public void putShouldReturnOkWhenDomainListException() throws Exception {
            doThrow(new DomainListException("message")).when(domainList).addDomain(domain);

            when()
                .put(DOMAIN)
            .then()
                .statusCode(204);
        }

        @Test
        public void getDomainShouldReturnErrorOnDomainListException() throws Exception {
            when(domainList.containsDomain(domain)).thenThrow(new DomainListException("message"));

            when()
                .get(DOMAIN)
            .then()
                .statusCode(500);
        }

        @Test
        public void getDomainsShouldReturnErrorOnDomainListException() throws Exception {
            when(domainList.getDomains()).thenThrow(new DomainListException("message"));

            when()
                .get()
            .then()
                .statusCode(500);
        }

    }

}
