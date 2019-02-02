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
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.jackson.QuotaModule;
import org.apache.james.webadmin.service.DomainQuotaService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.collect.ImmutableSet;
import io.restassured.RestAssured;

class DomainQuotaRoutesNoVirtualHostingTest {

    private static final String QUOTA_DOMAINS = "/quota/domains";
    private static final String FOUND_COM = "found.com";
    private static final String COUNT = "count";
    private static final String SIZE = "size";
    private WebAdminServer webAdminServer;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryPerUserMaxQuotaManager maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        MemoryDomainList memoryDomainList = new MemoryDomainList(new InMemoryDNSService());
        memoryDomainList.setAutoDetect(false);
        memoryDomainList.addDomain(Domain.of(FOUND_COM));
        DomainQuotaService domainQuotaService = new DomainQuotaService(maxQuotaManager);
        QuotaModule quotaModule = new QuotaModule();
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withoutVirtualHosting();
        DomainQuotaRoutes domainQuotaRoutes = new DomainQuotaRoutes(memoryDomainList, domainQuotaService, usersRepository, new JsonTransformer(quotaModule), ImmutableSet.of(quotaModule));
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new NoopMetricFactory(),
            domainQuotaRoutes);
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        QUOTA_DOMAINS + "/" + FOUND_COM,
        QUOTA_DOMAINS + "/" + FOUND_COM + "/" + COUNT,
        QUOTA_DOMAINS + "/" + FOUND_COM + "/" + SIZE })
    void allGetEndpointsShouldReturnNotAllowed(String endpoint) {
        given()
            .get(endpoint)
        .then()
            .statusCode(HttpStatus.METHOD_NOT_ALLOWED_405);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        QUOTA_DOMAINS + "/" + FOUND_COM,
        QUOTA_DOMAINS + "/" + FOUND_COM + "/" + COUNT,
        QUOTA_DOMAINS + "/" + FOUND_COM + "/" + SIZE })
    void allPutEndpointsShouldReturnNotAllowed(String endpoint) {
        given()
            .put(endpoint)
        .then()
            .statusCode(HttpStatus.METHOD_NOT_ALLOWED_405);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        QUOTA_DOMAINS + "/" + FOUND_COM + "/" + COUNT,
        QUOTA_DOMAINS + "/" + FOUND_COM + "/" + SIZE })
    void allDeleteEndpointsShouldReturnNotAllowed(String endpoint) {
        given()
            .delete(endpoint)
        .then()
            .statusCode(HttpStatus.METHOD_NOT_ALLOWED_405);
    }

}
