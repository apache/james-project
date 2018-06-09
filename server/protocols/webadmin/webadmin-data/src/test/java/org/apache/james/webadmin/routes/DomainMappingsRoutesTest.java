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

import com.google.common.collect.ImmutableList;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.LogDetail;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.james.core.Domain;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.jayway.restassured.RestAssured.*;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.mockito.Mockito.spy;

class DomainMappingsRoutesTest {
    private RecipientRewriteTable recipientRewriteTable;
    private WebAdminServer webAdminServer;

    @SuppressWarnings("unused")
    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("domain.com", ""),
                // Why this params should pass the test ?
                // "domain.com, '    '",
                // "domain.com, '    \n\t\r'",
                Arguments.of("abc@domain.com", "domain.com"),
                Arguments.of("domain.com", "abc@domain.com")

        );
    }

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
            // @formatter:off
            given(with().body("to.com"))
                        .put("from.com")
                    .then()
                        .statusCode(HttpStatus.NO_CONTENT_204)
                        .body(isEmptyString());
            // @formatter:on
        }

        @Test
        void getDomainMappings() throws RecipientRewriteTableException {
            Domain expectedDomain = Domain.of("abc.com");
            MappingSource mappingSource = MappingSource.fromDomain(expectedDomain);
            ImmutableList<String> expectedAliases = ImmutableList.of("to_1.com", "to_2.com", "to_3.com");

            for (String alias : expectedAliases) {
                recipientRewriteTable.addAliasDomainMapping(mappingSource, Domain.of(alias));
            }

            // @formatter:off
            final Map<String, List<String>> map =
                    when()
                        .get()
                    .then()
                        .contentType(ContentType.JSON)
                        .statusCode(HttpStatus.OK_200)
                    .extract()
                        .body()
                        .jsonPath()
                        .getMap(".");
            // @formatter:on

            assertThat(map)
                    .containsOnly(entry(expectedDomain.name(), expectedAliases));
        }

        @Test
        void getDomainMappingsShouldBeEmpty() {
            // @formatter:off
            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .statusCode(HttpStatus.OK_200)
                .body(is("{}"));
            // @formatter:on
        }

        @Test
        void deleteDomainMappingShouldRespondWithNoContent() {
            // @formatter:off
            given(with().body("to.com"))
                        .delete("from.com")
                    .then()
                        .statusCode(HttpStatus.NO_CONTENT_204)
                        .body(isEmptyString());
            // @formatter:on
        }
    }

    @Nested
    class IllegalInputs {
        @Test
        void addDomainMappingShouldRespondWithNotFound() {
            // @formatter:off
            when()
                .put("")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
            // @formatter:on
        }

        @Test
        void deleteDomainMappingShouldRespondWithNotFound() {
            // @formatter:off
            when()
                .delete("")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
            // @formatter:on
        }

        @ParameterizedTest
        @MethodSource("org.apache.james.webadmin.routes.DomainMappingsRoutesTest#invalidInputs")
        void addDomainMappingWithInvalidDomainInPath(String fromDomain, String toDomain) {
            assertBadRequest(toDomain, spec -> put(fromDomain));
        }


        @ParameterizedTest
        @MethodSource("org.apache.james.webadmin.routes.DomainMappingsRoutesTest#invalidInputs")
        void deleteDomainMappingWithInvalidDomainInPath(String fromDomain, String toDomain) {
            assertBadRequest(toDomain, spec -> delete(fromDomain));
        }

        private void assertBadRequest(String toDomain, Function<RequestSpecification, Response> op) {
            // @formatter:off
            Map<String, Object> errors = op.apply(given(with().body(toDomain)))
                    .then()
                        .statusCode(HttpStatus.BAD_REQUEST_400)
                        .contentType(ContentType.JSON)
                    .extract()
                        .body()
                        .jsonPath()
                        .getMap(".");
            // @formatter:on

            assertThat(errors)
                    .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                    .containsEntry("type", "InvalidArgument")
                    .hasEntrySatisfying("message", o -> assertThat((String) o).matches("^The domain .* is invalid\\.$"));
        }
    }
}