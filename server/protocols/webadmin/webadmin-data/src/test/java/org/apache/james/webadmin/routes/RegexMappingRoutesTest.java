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

import static io.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

import java.net.URLEncoder;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
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

class RegexMappingRoutesTest {
    private WebAdminServer webAdminServer;
    private MemoryRecipientRewriteTable memoryRecipientRewriteTable;

    @BeforeEach
    void beforeEach() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        memoryRecipientRewriteTable = new MemoryRecipientRewriteTable();
        memoryRecipientRewriteTable.setDomainList(domainList);
        domainList.addDomain(Domain.of("domain.tld"));

        webAdminServer = WebAdminUtils
            .createWebAdminServer(new RegexMappingRoutes(memoryRecipientRewriteTable))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(RegexMappingRoutes.BASE_PATH)
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
    }

    @Test
    void addRegexMappingShouldReturnNoContentWhenSuccess() {
        with()
            .post("james@domain.tld/targets/bis.*@apache.org")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204)
            .contentType(ContentType.JSON);

        assertThat(memoryRecipientRewriteTable
            .getStoredMappings(MappingSource.fromUser(Username.of("james@domain.tld"))))
            .containsOnly(Mapping.regex("bis.*@apache.org"));
    }

    @Test
    void addRegexMappingShouldAllowUserWithoutDomain() {
        with()
            .post("jamesdomaintld/targets/bis.*@apache.org")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204)
            .contentType(ContentType.JSON);

        assertThat(memoryRecipientRewriteTable
            .getStoredMappings(MappingSource.fromUser(Username.of("jamesdomaintld"))))
            .containsOnly(Mapping.regex("bis.*@apache.org"));
    }

    @Test
    void addRegexMappingShouldReturnNotFoundWhenSourceIsEmpty() {
        with()
            .post("/targets/bis.*@apache.org")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .contentType(ContentType.JSON)
            .body("type", is("notFound"))
            .body("statusCode", is(HttpStatus.NOT_FOUND_404))
            .body("message", is("POST /mappings/regex/targets/bis.*@apache.org can not be found"));
    }

    @Test
    void addRegexMappingShouldReturnNotFoundWhenRegexIsEmpty() {
        with()
            .post("james@domain.tld/targets/")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .contentType(ContentType.JSON)
            .body("type", is("notFound"))
            .body("statusCode", is(HttpStatus.NOT_FOUND_404))
            .body("message", is("POST /mappings/regex/james@domain.tld/targets/ can not be found"));
    }

    @Test
    void addRegexMappingShouldReturnNotFoundWhenSourceAndRegexEmpty() {
        with()
            .post("/targets/")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .contentType(ContentType.JSON)
            .body("type", is("notFound"))
            .body("statusCode", is(HttpStatus.NOT_FOUND_404))
            .body("message", is("POST /mappings/regex/targets/ can not be found"));
    }

    @Test
    void addRegexMappingShouldReturnBadRequestWhenRegexIsInvalid() {
        with()
            .post("james@domain.tld/targets/"+ URLEncoder.encode("O.*[]"))
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
            .body("type", is("InvalidArgument"))
            .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
            .body("message", is("Invalid regex: O.*[]"));
    }

    @Test
    void addRegexMappingShouldReturnNoContentWhenRegexContainsQuestionMark() {
        with()
            .post("james@domain.tld/targets/" + URLEncoder.encode("^[aei?ou].*james@domain.tld"))
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204)
            .contentType(ContentType.JSON);

        assertThat(memoryRecipientRewriteTable
            .getStoredMappings(MappingSource.fromUser(Username.of("james@domain.tld"))))
            .containsOnly(Mapping.regex("^[aei?ou].*james@domain.tld"));
    }

    @Test
    void addRegexMappingShouldJustDecodeOneTime() {
        String originalRegex = "^[aei?ou].*james@domain.tld";
        String onceEncodedRegex = URLEncoder.encode(originalRegex);
        String twiceEncodedRegex = URLEncoder.encode(onceEncodedRegex);

        with()
            .post("james@domain.tld/targets/" + twiceEncodedRegex)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204)
            .contentType(ContentType.JSON);

        assertThat(memoryRecipientRewriteTable
            .getStoredMappings(MappingSource.fromUser(Username.of("james@domain.tld"))))
            .containsOnly(Mapping.regex(onceEncodedRegex));
    }

    @Test
    void removeRegexMappingShouldReturnNoContentWhenSuccess() throws Exception {
        MappingSource mappingSource = MappingSource.fromUser(Username.of("abc@domain.tld"));
        memoryRecipientRewriteTable
            .addRegexMapping(mappingSource, "valar.*@apache.org");

        with()
            .delete("abc@domain.tld/targets/valar.*@apache.org")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204)
            .contentType(ContentType.JSON);

        assertThat(memoryRecipientRewriteTable.getStoredMappings(mappingSource))
            .doesNotContain(Mapping.regex("valar.*@apache.org"));
    }

    @Test
    void removeRegexMappingShouldAllowUserWithoutDomain() throws Exception {
        MappingSource mappingSource = MappingSource.fromUser(Username.of("abcdomaintld"));
        memoryRecipientRewriteTable
            .addRegexMapping(mappingSource, "valar.*@apache.org");

        with()
            .delete("abcdomaintld/targets/valar.*@apache.org")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204)
            .contentType(ContentType.JSON);

        assertThat(memoryRecipientRewriteTable.getStoredMappings(mappingSource))
            .doesNotContain(Mapping.regex("valar.*@apache.org"));
    }

    @Test
    void removeRegexMappingShouldReturnNotFoundWhenSourceIsEmpty() {
        with()
            .delete("/targets/valar.*@apache.org")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .contentType(ContentType.JSON)
            .body("statusCode", is(HttpStatus.NOT_FOUND_404))
            .body("type", is("notFound"))
            .body("message", is("DELETE /mappings/regex/targets/valar.*@apache.org can not be found"));
    }

    @Test
    void removeRegexMappingShouldReturnNotFoundWhenRegexIsEmpty() {
         with()
            .delete("abc@domain.tld/targets/")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .contentType(ContentType.JSON)
            .body("statusCode", is(HttpStatus.NOT_FOUND_404))
            .body("type", is("notFound"))
            .body("message", is("DELETE /mappings/regex/abc@domain.tld/targets/ can not be found"));
    }

    @Test
    void removeRegexMappingShouldReturnNotFoundWhenSourceAndRegexEmpty() {
        with()
            .delete("/targets/")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .contentType(ContentType.JSON)
            .body("statusCode", is(HttpStatus.NOT_FOUND_404))
            .body("type", is("notFound"))
            .body("message", is("DELETE /mappings/regex/targets/ can not be found"));
    }

    @Test
    void removeRegexMappingShouldReturnNoContentWhenTwoSameRequestArrive() throws Exception {
        MappingSource mappingSource = MappingSource.fromUser(Username.of("abc@domain.tld"));
        memoryRecipientRewriteTable
            .addRegexMapping(mappingSource, "valar.*@apache.org");

        with()
            .delete("abc@domain.tld/targets/valar.*@apache.org")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204)
            .contentType(ContentType.JSON);

        with()
            .delete("abc@domain.tld/targets/valar.*@apache.org")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204)
            .contentType(ContentType.JSON);

        assertThat(memoryRecipientRewriteTable.getStoredMappings(mappingSource))
            .doesNotContain(Mapping.regex("valar.*@apache.org"));
    }

    @Test
    void removeRegexMappingShouldReturnNoContentDespiteTheSourceExistence() throws Exception {
        with()
            .delete("abc@domain.tld/targets/valar.*@apache.org")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204)
            .contentType(ContentType.JSON);

        MappingSource mappingSource = MappingSource.fromUser(Username.of("abc@domain.tld"));
        memoryRecipientRewriteTable
            .addRegexMapping(mappingSource, "valar.*@apache.org");
    }

    @Test
    void removeRegexMappingShouldReturnNotFoundWhenRegexContainsBackslash() throws Exception {
        with()
            .delete("abc@domain.tld/targets/valar.*/@apache.org")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .contentType(ContentType.JSON)
            .body("message", is("DELETE /mappings/regex/abc@domain.tld/targets/valar.*/@apache.org can not be found"))
            .body("statusCode", is(HttpStatus.NOT_FOUND_404));
    }

    @Test
    void removeRegexMappingShouldReturnNoContentWhenRegexContainsValidQuestionMark() {
        with()
            .delete("abc@domain.tld/targets/valar.*%3F@apache.org")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204)
            .contentType(ContentType.JSON);
    }
}