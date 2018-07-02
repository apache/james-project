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

import static com.jayway.restassured.RestAssured.delete;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.put;
import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.RestAssured.with;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.james.core.Domain;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.LogDetail;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;

class DomainMappingsRoutesTest {
    private RecipientRewriteTable recipientRewriteTable;
    private WebAdminServer webAdminServer;

    private void createServer(DomainMappingsRoutes domainMappingsRoutes) throws Exception {
        webAdminServer = WebAdminUtils.createWebAdminServer(new DefaultMetricFactory(), domainMappingsRoutes);
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath("/domainMappings")
                .log(LogDetail.METHOD)
                .build();
    }

    @BeforeEach
    void setUp() throws Exception {
        recipientRewriteTable = spy(new MemoryRecipientRewriteTable());

        createServer(new DomainMappingsRoutes(recipientRewriteTable, new JsonTransformer()));
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class NormalBehaviour {

        @Test
        void addDomainMappingShouldRespondWithNoContent() {
            given()
                .body("to.com")
            .when()
                .put("from.com")
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204)
                .body(isEmptyString());
        }

        @Test
        void addDomainMappingShouldBeIdempotent() {
            with()
                .body("to.com")
                .put("from.com");

            given()
                .body("to.com")
            .when()
                .put("from.com")
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204)
                .body(isEmptyString());
        }

        @Test
        void getDomainMappingsShouldReturnAllDomainMappings() throws RecipientRewriteTableException {
            String alias1 = "to_1.com";
            String alias2 = "to_2.com";
            String alias3 = "to_3.com";

            Domain expectedDomain = Domain.of("abc.com");
            MappingSource mappingSource = MappingSource.fromDomain(expectedDomain);

            recipientRewriteTable.addAliasDomainMapping(mappingSource, Domain.of(alias1));
            recipientRewriteTable.addAliasDomainMapping(mappingSource, Domain.of(alias2));
            recipientRewriteTable.addAliasDomainMapping(mappingSource, Domain.of(alias3));

            Map<String, List<String>> map =
                    when()
                        .get()
                    .then()
                        .contentType(ContentType.JSON)
                        .statusCode(HttpStatus.OK_200)
                    .extract()
                        .body()
                        .jsonPath()
                        .getMap(".");

            assertThat(map)
                    .containsOnly(entry(expectedDomain.name(), ImmutableList.of(alias1, alias2, alias3)));
        }

        @Test
        void getDomainMappingsEmptyMappingsAreFilteredOut() throws RecipientRewriteTableException {
            MappingSource nonEmptyMapping = MappingSource.fromDomain(Domain.of("abc.com"));
            MappingSource emptyMapping = MappingSource.fromDomain(Domain.of("def.com"));

            Map<MappingSource, Mappings> mappings = ImmutableMap.of(
                    nonEmptyMapping, MappingsImpl.fromRawString("domain:a.com"),
                    emptyMapping, MappingsImpl.empty()
            );

            when(recipientRewriteTable.getAllMappings()).thenReturn(mappings);

            Map<String, List<String>> map =
                    when()
                        .get()
                    .then()
                        .contentType(ContentType.JSON)
                        .statusCode(HttpStatus.OK_200)
                    .extract()
                        .body()
                        .jsonPath()
                        .getMap(".");

            assertThat(map)
                    .containsKey(nonEmptyMapping.asString())
                    .doesNotContainKey(emptyMapping.asString());
        }

        @Test
        void getDomainMappingsShouldFilterNonDomainMappings() throws RecipientRewriteTableException {
            MappingSource mappingSource = MappingSource.fromDomain(Domain.of("abc.com"));
            String address = "addr@domain.com";

            recipientRewriteTable.addAddressMapping(mappingSource, address);
            recipientRewriteTable.addForwardMapping(mappingSource, address);
            recipientRewriteTable.addErrorMapping(mappingSource, address);
            recipientRewriteTable.addGroupMapping(mappingSource, address);
            recipientRewriteTable.addRegexMapping(mappingSource, address);

            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body(is("{}"));
        }

        @Test
        void getDomainMappingsShouldBeEmptyByDefault() {
            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body(is("{}"));
        }

        @Test
        void deleteDomainMappingShouldRespondWithNoContent() {
            given()
                .body("to.com")
            .when()
                .delete("from.com")
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204)
                .body(isEmptyString());
        }

        @Test
        void deleteDomainMappingShouldRemoveMapping() throws RecipientRewriteTableException {
            MappingSource mappingSource = MappingSource.fromDomain(Domain.of("from.com"));
            String alias = "to.com";

            recipientRewriteTable.addAliasDomainMapping(mappingSource, Domain.of(alias));

            Assumptions.assumeTrue(recipientRewriteTable.getUserDomainMappings(mappingSource) != null);

            given()
                .body("to.com")
            .when()
                .delete("from.com")
            .then()
                .body(isEmptyString());

            assertThat(recipientRewriteTable.getAllMappings()).isEmpty();
        }

        @Test
        void getSpecificDomainMappingShouldRespondWithNotFoundWhenHasNoAliases() throws RecipientRewriteTableException {
            String domain = "from.com";

            when(recipientRewriteTable.getUserDomainMappings(any())).thenReturn(MappingsImpl.empty());

            when()
                .get(domain)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.NOT_FOUND_404)
                .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
                .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                .body("message", is("Cannot find mappings for " + domain));
        }

        @Test
        void getSpecificDomainMappingShouldRespondWithNotFoundWhenHasEmptyAliases() throws RecipientRewriteTableException {
            String domain = "from.com";

            when(recipientRewriteTable.getUserDomainMappings(any())).thenReturn(MappingsImpl.empty());

            when()
                .get(domain)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.NOT_FOUND_404)
                .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
                .body("statusCode", is(HttpStatus.NOT_FOUND_404))
                .body("message", is("Cannot find mappings for " + domain));
        }

        @Test
        void getSpecificDomainMappingShouldFilterOutNonDomainMappings() throws RecipientRewriteTableException {
            String domain = "from.com";
            String aliasDomain = "to.com";
            final MappingSource mappingSource = MappingSource.fromDomain(Domain.of(domain));

            recipientRewriteTable.addRegexMapping(mappingSource, "(.*)@localhost");
            recipientRewriteTable.addGroupMapping(mappingSource, "user@domain.com");
            recipientRewriteTable.addForwardMapping(mappingSource, "user@domain.com");
            recipientRewriteTable.addErrorMapping(mappingSource, "disabled");
            recipientRewriteTable.addAliasDomainMapping(mappingSource, Domain.of(aliasDomain));

            List<String> body =
                    when()
                        .get(domain)
                    .then()
                        .contentType(ContentType.JSON)
                        .statusCode(HttpStatus.OK_200)
                    .extract()
                        .jsonPath()
                        .getList(".");

            assertThat(body).containsOnly(aliasDomain);
        }

        @Test
        void getSpecificDomainMappingShouldReturnDomainMappings() throws RecipientRewriteTableException {
            String domain = "abc.com";
            String aliasDomain = "a.com";
            Mappings mappings = MappingsImpl.fromMappings(Mapping.domain(Domain.of(aliasDomain)));

            when(recipientRewriteTable.getUserDomainMappings(any())).thenReturn(mappings);

            List<String> body =
            when()
                .get(domain)
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .extract()
                .jsonPath()
                .getList(".");

            assertThat(body).contains(aliasDomain);
        }
    }

    @Nested
    class IllegalInputs {
        @Test
        void addDomainMappingShouldRespondWithNotFound() {
            when()
                .put("")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void deleteDomainMappingShouldRespondWithNotFound() {
            when()
                .delete("")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void addDomainMappingWithInvalidDomainInBody() {
            assertBadRequest("abc@domain.com", spec -> put("domain.com"));
        }

        @Test
        void deleteDomainMappingWithInvalidDomainInBody() {
            assertBadRequest("abc@domain.com", spec -> put("domain.com"));
        }

        @Test
        void addDomainMappingWithInvalidDomainInPath() {
            assertBadRequest("domain.com", spec -> put("abc@domain.com"));
        }

        @Test
        void deleteDomainMappingWithInvalidDomainInPath() {
            assertBadRequest("domain.com", spec -> put("abc@domain.com"));
        }

        @Test
        void addDomainMappingWithEmptyAliasDomain() {
            assertBadRequest("", spec -> put("domain.com"));
        }

        @Test
        void deleteDomainMappingWithEmptyAliasDomain() {
            assertBadRequest("", spec -> delete("domain.com"));
        }


        @Test
        void addSpecificDomainMappingWithInvalidDomainInPath() {
            Map<String, Object> errors =
            when()
                .get("abc@domain.com")
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
                    .hasEntrySatisfying("message", o -> assertThat((String) o).matches("^The domain .* is invalid\\.$"));
        }

        private void assertBadRequest(String toDomain, Function<RequestSpecification, Response> op) {
            Map<String, Object> errors = op.apply(given().body(toDomain).when())
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
                    .hasEntrySatisfying("message", o -> assertThat((String) o).matches("^The domain .* is invalid\\.$"));
        }
    }
}