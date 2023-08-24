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
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.junit.TemporaryFolderExtension;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.file.SieveFileRepository;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.RestAssured;

@ExtendWith(TemporaryFolderExtension.class)
class SieveScriptRoutesTest {
    private static final DomainList NO_DOMAIN_LIST = null;

    private WebAdminServer webAdminServer;
    private SieveRepository sieveRepository;
    private String sieveContent;

    @BeforeEach
    void setUp(TemporaryFolderExtension.TemporaryFolder temporaryFolder) throws Exception {
        FileSystem fileSystem = new FileSystem() {
            @Override
            public File getBasedir() {
                return temporaryFolder.getTempDir();
            }

            @Override
            public InputStream getResource(String url) throws IOException {
                return new FileInputStream(getFile(url));
            }

            @Override
            public File getFile(String fileURL) {
                return new File(getBasedir(), fileURL.substring(FileSystem.FILE_PROTOCOL.length()));
            }
        };

        sieveRepository = new SieveFileRepository(fileSystem);
        UsersRepository usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        usersRepository.addUser(Username.of("userA"), "password");

        URL sieveResource = ClassLoader.getSystemResource("sieve/my_sieve");
        sieveContent = IOUtils.toString(sieveResource, StandardCharsets.UTF_8);

        webAdminServer = WebAdminUtils.createWebAdminServer(new SieveScriptRoutes(sieveRepository, usersRepository))
            .start();

        RestAssured.requestSpecification = WebAdminUtils
            .buildRequestSpecification(webAdminServer)
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void defineAddActiveSieveScriptShouldReturnNotFoundWhenUserNotExisted() {
        given()
            .pathParam("userName", "unknown")
            .pathParam("scriptName", "scriptA")
            .body(sieveContent)
        .when()
            .put("sieve/{userName}/scripts/{scriptName}")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void defineAddActiveSieveScriptShouldReturnNotFoundWhenScriptNameIsWhiteSpace() {
        String errorBody =
            "{\"statusCode\": 400," +
            " \"type\":\"InvalidArgument\"," +
            " \"message\":\"Invalid Sieve script name\"," +
            " \"details\":null" +
            "}";
        String body = given()
            .pathParam("userName", "userA")
            .pathParam("scriptName", "%20")
            .body(sieveContent)
        .when()
            .put("sieve/{userName}/scripts/{scriptName}")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .extract()
            .body().asString();

        assertThatJson(body).isEqualTo(errorBody);
    }

    @Test
    void defineAddActiveSieveScriptShouldReturnNotFoundWhenUserNameWhiteSpace() {
        String errorBody =
            "{\"statusCode\": 400," +
            " \"type\":\"InvalidArgument\"," +
            " \"message\":\"Invalid username\"," +
            " \"details\":null" +
            "}";
        String body = given()
            .pathParam("userName", "%20")
            .pathParam("scriptName", "scriptA")
            .body(sieveContent)
        .when()
            .put("sieve/{userName}/scripts/{scriptName}")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .extract()
            .body().asString();

        assertThatJson(body).isEqualTo(errorBody);
    }

    @Test
    void defineAddActiveSieveScriptShouldReturnBadRequestWhenScriptIsNotSet() {
        given()
            .pathParam("userName", "userA")
            .pathParam("scriptName", "scriptA")
        .when()
            .put("sieve/{userName}/scripts/{scriptName}")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
     void defineAddActiveSieveScriptShouldReturnSucceededWhenScriptIsWhiteSpace() throws Exception {
        given()
            .pathParam("userName", "userA")
            .pathParam("scriptName", "scriptA")
            .body(" ")
        .when()
            .put("sieve/{userName}/scripts/{scriptName}")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(getScriptContent(sieveRepository
            .getScript(Username.of("userA"), new ScriptName("scriptA"))))
            .isEqualTo(new ScriptContent(" "));
    }

    @Test
    void defineAddActiveSieveScriptAddScriptSucceededOneWhenNotAddActivateParam() throws Exception {
        given()
            .pathParam("userName", "userA")
            .pathParam("scriptName", "scriptA")
            .body(sieveContent)
        .when()
            .put("sieve/{userName}/scripts/{scriptName}")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(getScriptContent(sieveRepository
            .getScript(Username.of("userA"), new ScriptName("scriptA"))))
            .isEqualTo(new ScriptContent(sieveContent));
    }

    @Test
    void defineAddActiveSieveScriptSetActiveTrueWhenAddActivateParamTrue() throws Exception {
        given()
            .pathParam("userName", "userA")
            .pathParam("scriptName", "scriptA")
            .queryParam("activate", true)
            .body(sieveContent)
        .when()
            .put("sieve/{userName}/scripts/{scriptName}")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(getScriptContent(sieveRepository
            .getActive(Username.of("userA"))))
            .isEqualTo(new ScriptContent(sieveContent));
    }

    @Test
    void defineAddActiveSieveScriptGetActiveShouldThrowsExceptionWhenAddActivateParamFalse() {
        given()
            .pathParam("userName", "userA")
            .pathParam("scriptName", "scriptA")
            .queryParam("activate", false)
            .body(sieveContent)
        .when()
            .put("sieve/{userName}/scripts/{scriptName}")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThatThrownBy(() -> sieveRepository.getActive(Username.of("userA")))
            .isInstanceOf(ScriptNotFoundException.class);
    }

    @Test
    void defineAddActiveSieveScriptInvokeShouldReturnBadRequestWhenAddActivateParamWithNotBooleanValue() {
        given()
            .pathParam("userName", "userA")
            .pathParam("scriptName", "scriptA")
            .queryParam("activate", "activate")
            .body(sieveContent)
        .when()
            .put("sieve/{userName}/scripts/{scriptName}")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", equalTo("Invalid activate query parameter"));
    }

    ScriptContent getScriptContent(InputStream inputStream) throws IOException {
        return new ScriptContent(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
    }
}
