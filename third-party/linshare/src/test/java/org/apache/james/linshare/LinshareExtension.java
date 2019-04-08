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
package org.apache.james.linshare;

import static org.apache.james.linshare.LinshareFixture.USER_1;
import static org.apache.james.linshare.LinshareFixture.USER_CREDENTIAL_MAP;
import static org.apache.james.linshare.client.LinshareAPI.Headers.ACCEPT_APPLICATION_JSON;

import java.util.List;
import java.util.Optional;

import org.apache.james.linshare.client.LinshareAPI;
import org.apache.james.linshare.client.User;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.github.fge.lambdas.Throwing;

import feign.Feign;
import feign.Headers;
import feign.Logger;
import feign.RequestLine;
import feign.auth.BasicAuthRequestInterceptor;
import feign.form.FormEncoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public class LinshareExtension implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {

    private interface LinshareAPIForTesting {

        static LinshareAPIForTesting from(LinshareFixture.Credential credential, Linshare linshare) {

            return Feign.builder()
                .requestInterceptor(new BasicAuthRequestInterceptor(credential.getUsername(), credential.getPassword()))
                .logger(new Slf4jLogger(LinshareAPIForTesting.class))
                .logLevel(Logger.Level.FULL)
                .encoder(new FormEncoder(new JacksonEncoder()))
                .decoder(new JacksonDecoder())
                .target(LinshareAPIForTesting.class, linshare.getUrl());
        }

        @RequestLine("GET /linshare/webservice/rest/user/v2/authentication/jwt")
        @Headers(ACCEPT_APPLICATION_JSON)
        AuthorizationToken jwt();

        @RequestLine("GET /linshare/webservice/rest/user/v2/users")
        @Headers(ACCEPT_APPLICATION_JSON)
        List<User> allUsers();
    }

    private Linshare linshare;

    @Override
    public void beforeAll(ExtensionContext context) {
        linshare = new Linshare();
        linshare.start();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        deleteAllUsersDocuments();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        linshare.stop();
    }

    public Linshare getLinshare() {
        return linshare;
    }

    public LinshareAPI getAPIFor(LinshareFixture.Credential credential) throws Exception {
        return LinshareAPI.from(configurationWithJwtFor(credential));
    }

    private void deleteAllUsersDocuments() {
        LinshareAPIForTesting.from(USER_1, linshare)
            .allUsers()
            .stream()
            .map(this::getUsernamePassword)
            .map(Throwing.function(this::configurationWithJwtFor))
            .map(LinshareAPI::from)
            .forEach(LinshareAPI::deleteAllDocuments);
    }

    private LinshareFixture.Credential getUsernamePassword(User user) {
        return Optional.ofNullable(USER_CREDENTIAL_MAP.get(user.getMail()))
            .orElseThrow(() -> new RuntimeException("cannot get token of user " + user.getMail()));
    }

    private LinshareConfiguration configurationWithJwtFor(LinshareFixture.Credential credential) throws Exception {
        AuthorizationToken token = LinshareAPIForTesting.from(credential, linshare).jwt();

        return LinshareConfiguration.builder()
            .urlAsString(linshare.getUrl())
            .authorizationToken(token)
            .build();
    }
}
