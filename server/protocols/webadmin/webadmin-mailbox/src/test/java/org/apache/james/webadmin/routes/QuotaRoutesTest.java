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
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.mailbox.cassandra.modules.CassandraQuotaModule;
import org.apache.james.mailbox.cassandra.quota.CassandraQuotaLimitDao;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.jackson.QuotaModule;
import org.apache.james.webadmin.service.QuotaService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

class QuotaRoutesTest {

    private static CassandraQuotaLimitDao cassandraQuotaLimitDao;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(CassandraQuotaModule.MODULE));

    @BeforeAll
    static void setup() {
        cassandraQuotaLimitDao = new CassandraQuotaLimitDao(cassandraCluster.getCassandraCluster().getConf());
        QuotaService quotaLimitService = new QuotaService(cassandraQuotaLimitDao);
        QuotaModule quotaModule = new QuotaModule();
        JsonTransformer jsonTransformer = new JsonTransformer(quotaModule);
        QuotaRoutes quotaLimitRoutes = new QuotaRoutes(
            quotaLimitService,
            jsonTransformer);
        WebAdminServer webAdminServer = WebAdminUtils.createWebAdminServer(quotaLimitRoutes)
            .start();
        RequestSpecification requestSpecBuilder = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .build();
        RestAssured.requestSpecification = requestSpecBuilder;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void putQuotaLimitShouldReturnNotFoundWhenIdentifierDoesNotExist() {
        when()
            .put("/quota/limit/jmapUploads/user")
            .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void putQuotaLimitShouldUpdateDataSuccessfully() {
        given()
            .body("{\"count\":100,\"size\":30}")
            .put("/quota/limit/jmapUploads/user/abc")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        QuotaLimit expectedOne = QuotaLimit.builder().quotaComponent(QuotaComponent.JMAP_UPLOADS).quotaScope(QuotaScope.USER).identifier("abc").quotaType(QuotaType.COUNT).quotaLimit(100l).build();
        QuotaLimit expectedTwo = QuotaLimit.builder().quotaComponent(QuotaComponent.JMAP_UPLOADS).quotaScope(QuotaScope.USER).identifier("abc").quotaType(QuotaType.SIZE).quotaLimit(30l).build();
        assertThat(cassandraQuotaLimitDao.getQuotaLimits(QuotaComponent.JMAP_UPLOADS, QuotaScope.USER, "abc").collectList().block()).containsExactlyInAnyOrder(expectedOne, expectedTwo);
    }

    @Test
    void putQuotaLimitShouldRejectInvalidValueType() {
        Map<String, Object> errors = given()
            .body("{\"count\": \"aaa\"}")
            .put("/quota/limit/jmapUploads/user/abc")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
        .extract()
            .body()
            .jsonPath()
            .getMap(".");

        assertThat(errors)
            .containsEntry("type", "InvalidArgument")
            .containsEntry("message", "Wrong JSON format");
    }

    @Test
    void putQuotaLimitShouldRejectWhenValueIsLessThanMinusOne() {
        Map<String, Object> errors = given()
            .body("{\"count\": -5}")
            .put("/quota/limit/jmapUploads/user/abc")
            .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        assertThat(errors)
            .containsEntry("type", "InvalidArgument")
            .containsEntry("message", "Invalid quota limit. Need to be greater or equal to -1");
    }
}
