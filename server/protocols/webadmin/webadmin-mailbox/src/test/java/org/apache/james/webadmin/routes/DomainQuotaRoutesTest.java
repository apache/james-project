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
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.jackson.QuotaModule;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.path.json.JsonPath;

public class DomainQuotaRoutesTest {

    private static final String QUOTA_DOMAINS = "/quota/domains";
    private static final String PERDU_COM = "perdu.com";
    private static final String TROUVÉ_COM = "trouvé.com";
    private static final String COUNT = "count";
    private static final String SIZE = "size";
    private WebAdminServer webAdminServer;
    private InMemoryPerUserMaxQuotaManager maxQuotaManager;

    @Before
    public void setUp() throws Exception {
        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        MemoryDomainList memoryDomainList = new MemoryDomainList(new InMemoryDNSService());
        memoryDomainList.setAutoDetect(false);
        memoryDomainList.addDomain(TROUVÉ_COM);
        MemoryDomainList domainList = new MemoryDomainList(new InMemoryDNSService());
        domainList.addDomain(TROUVÉ_COM);
        DomainQuotaService domainQuotaService = new DomainQuotaService(maxQuotaManager);
        QuotaModule quotaModule = new QuotaModule();
        DomainQuotaRoutes domainQuotaRoutes = new DomainQuotaRoutes(domainList, domainQuotaService, new JsonTransformer(quotaModule), ImmutableSet.of(quotaModule));
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new NoopMetricFactory(),
            domainQuotaRoutes);
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @After
    public void stop() {
        webAdminServer.destroy();
    }

    @Test
    public void getCountShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .get(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void getCountShouldReturnNoContentByDefault() {
        given()
            .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void getCountShouldReturnStoredValue() {
        int value = 42;
        maxQuotaManager.setDomainMaxMessage(TROUVÉ_COM, QuotaCount.count(value));

        Long actual =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + COUNT)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(actual).isEqualTo(value);
    }

    @Test
    public void putCountShouldReturnNotFoundWhenDomainDoesntExist() {
        given()
            .body("123")
        .when()
            .put(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void putCountShouldRejectInvalid() {
        Map<String, Object> errors = given()
            .body("invalid")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + COUNT)
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
            .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1")
            .containsEntry("cause", "For input string: \"invalid\"");
    }

    @Test
    public void putCountShouldSetToInfiniteWhenMinusOne() {
        given()
            .body("-1")
        .when()
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(TROUVÉ_COM)).contains(QuotaCount.unlimited());
    }

    @Test
    public void putCountShouldRejectNegativeOtherThanMinusOne() {
        Map<String, Object> errors = given()
            .body("-2")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + COUNT)
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
            .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1");
    }

    @Test
    public void putCountShouldAcceptValidValue() {
        given()
            .body("42")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(TROUVÉ_COM)).contains(QuotaCount.count(42));
    }

    @Test
    public void deleteCountShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .delete(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void deleteCountShouldSetQuotaToEmpty() {
        maxQuotaManager.setDomainMaxMessage(TROUVÉ_COM, QuotaCount.count(42));

        given()
            .delete(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + COUNT)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(TROUVÉ_COM)).isEmpty();
    }

    @Test
    public void getSizeShouldReturnNotFoundWhenDomainDoesntExist() {
            when()
                .get(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void getSizeShouldReturnNoContentByDefault() {
        when()
            .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void getSizeShouldReturnStoredValue() {
        long value = 42;
        maxQuotaManager.setDomainMaxStorage(TROUVÉ_COM, QuotaSize.size(value));


        long quota =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + SIZE)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Long.class);

        assertThat(quota).isEqualTo(value);
    }

    @Test
    public void putSizeShouldRejectInvalid() {
        Map<String, Object> errors = given()
            .body("invalid")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + SIZE)
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
            .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1")
            .containsEntry("cause", "For input string: \"invalid\"");
    }

    @Test
    public void putSizeShouldReturnNotFoundWhenDomainDoesntExist() {
        given()
            .body("123")
        .when()
            .put(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void putSizeShouldSetToInfiniteWhenMinusOne() {
        given()
            .body("-1")
        .when()
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(TROUVÉ_COM)).contains(QuotaSize.unlimited());
    }

    @Test
    public void putSizeShouldRejectNegativeOtherThanMinusOne() {
        Map<String, Object> errors = given()
            .body("-2")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + SIZE)
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
            .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1");
    }

    @Test
    public void putSizeShouldAcceptValidValue() {
        given()
            .body("42")
        .when()
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(TROUVÉ_COM)).contains(QuotaSize.size(42));
    }

    @Test
    public void deleteSizeShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .delete(QUOTA_DOMAINS + "/" + PERDU_COM + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void deleteSizeShouldSetQuotaToEmpty() {
        maxQuotaManager.setDomainMaxStorage(TROUVÉ_COM, QuotaSize.size(42));

        given()
            .delete(QUOTA_DOMAINS + "/" + TROUVÉ_COM + "/" + SIZE)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(TROUVÉ_COM)).isEmpty();
    }

    @Test
    public void getQuotaShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .get(QUOTA_DOMAINS + "/" + PERDU_COM)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void getQuotaShouldReturnBothEmptyWhenDefaultValues() {
        JsonPath jsonPath =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(jsonPath.getObject(SIZE, Long.class)).isNull();
        assertThat(jsonPath.getObject(COUNT, Long.class)).isNull();
    }

    @Test
    public void getQuotaShouldReturnSizeWhenNoCount() {
        int maxStorage = 42;
        maxQuotaManager.setDomainMaxStorage(TROUVÉ_COM, QuotaSize.size(maxStorage));

        JsonPath jsonPath =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(jsonPath.getLong(SIZE)).isEqualTo(maxStorage);
        assertThat(jsonPath.getObject(COUNT, Long.class)).isNull();
    }

    @Test
    public void getQuotaShouldReturnBothWhenNoSize() {
        int maxMessage = 42;
        maxQuotaManager.setDomainMaxMessage(TROUVÉ_COM, QuotaCount.count(maxMessage));


        JsonPath jsonPath =
            given()
                .get(QUOTA_DOMAINS + "/" + TROUVÉ_COM)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(jsonPath.getObject(SIZE, Long.class)).isNull();
        assertThat(jsonPath.getLong(COUNT)).isEqualTo(maxMessage);
    }

    @Test
    public void putQuotaShouldReturnNotFoundWhenDomainDoesntExist() {
        when()
            .put(QUOTA_DOMAINS + "/" + PERDU_COM)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void putQuotaShouldUpdateBothQuota() {
        given()
            .body("{\"count\":52,\"size\":42}")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxMessage(TROUVÉ_COM)).contains(QuotaCount.count(52));
        assertThat(maxQuotaManager.getDomainMaxStorage(TROUVÉ_COM)).contains(QuotaSize.size(42));
    }

    @Test
    public void putQuotaShouldBeAbleToRemoveBothQuota() {
        given()
            .body("{\"count\":null,\"count\":null}")
            .put(QUOTA_DOMAINS + "/" + TROUVÉ_COM)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(maxQuotaManager.getDomainMaxStorage(TROUVÉ_COM)).isEmpty();
        assertThat(maxQuotaManager.getDomainMaxMessage(TROUVÉ_COM)).isEmpty();
    }

}
