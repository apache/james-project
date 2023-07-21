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

package org.apache.james.rspamd;

import static org.apache.james.rspamd.RspamdExtension.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.is;

import org.apache.james.junit.categories.Unstable;
import org.apache.james.util.Port;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.core.IsIterableContaining;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;

@Tag(Unstable.TAG)
class DockerRspamdExtensionTest {
    @RegisterExtension
    static RspamdExtension rspamdExtension = new RspamdExtension();

    @Test
    void dockerRspamdExtensionShouldWork() {
        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rspamdExtension.rspamdPort()));

        String response = rspamdApi
            .get("ping")
        .then()
            .statusCode(HttpStatus.OK_200)
            .extract()
            .body()
            .asString()
            .trim();

        assertThat(response).isEqualTo("pong");
    }

    @Test
    void checkSpamEmailWithExactPasswordHeaderShouldWork() {
        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rspamdExtension.rspamdPort()));

        rspamdApi
            .header(new Header("Password", PASSWORD))
            .body(ClassLoader.getSystemResourceAsStream("mail/spam/spam8.eml"))
            .post("checkv2")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("action", is("reject"));
    }

    @Test
    void checkHamEmailWithExactPasswordHeaderShouldWork() {
        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rspamdExtension.rspamdPort()));
        rspamdApi
            .header(new Header("Password", PASSWORD))
            .body(ClassLoader.getSystemResourceAsStream("mail/ham/ham1.eml"))
            .post("checkv2")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("action", is("no action"));
    }

    @Test
    void learnSpamEmailWithExactPasswordHeaderShouldWork() {
        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rspamdExtension.rspamdPort()));

        rspamdApi
            .header(new Header("Password", PASSWORD))
            .body(ClassLoader.getSystemResourceAsStream("mail/spam/spam8.eml"))
        .post("learnspam")
            .then()
            .statusCode(HttpStatus.OK_200)
            .body("success", is(true));
    }

    @Test
    void learnHamEmailWithExactPasswordHeaderShouldWork() {
        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rspamdExtension.rspamdPort()));

        rspamdApi
            .header(new Header("Password", PASSWORD))
            .body(ClassLoader.getSystemResourceAsStream("mail/ham/ham1.eml"))
            .post("learnham")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("success", is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"checkv2", "learnspam", "learnham"})
    void endpointsWithWrongPasswordHeaderShouldReturnUnauthorized(String endpoint) {
        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rspamdExtension.rspamdPort()));

        rspamdApi
            .header(new Header("Password", "wrongPassword"))
            .body("dummy")
            .post(endpoint)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401)
            .body("error", is("Unauthorized"));
    }

    @Test
    void checkVirusEmailWithExactPasswordHeaderShouldReturnClamVirusSymbol() {
        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rspamdExtension.rspamdPort()));

        rspamdApi
            .header(new Header("Password", PASSWORD))
            .body(ClassLoader.getSystemResourceAsStream("mail/attachment/inlineVirusTextAttachment.eml"))
            .post("checkv2")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("symbols.CLAM_VIRUS.name", is("CLAM_VIRUS"))
            .body("symbols.CLAM_VIRUS.options", IsIterableContaining.hasItem("Eicar-Signature"));
    }

    @Test
    void checkNonVirusEmailWithExactPasswordHeaderShouldNotReturnClamVirusSymbol() {
        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rspamdExtension.rspamdPort()));

        rspamdApi
            .header(new Header("Password", PASSWORD))
            .body(ClassLoader.getSystemResourceAsStream("mail/attachment/inlineNonVirusTextAttachment.eml"))
            .post("checkv2")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("symbols.CLAM_VIRUS", IsNull.nullValue());
    }
}
