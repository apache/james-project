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

import static org.apache.james.linshare.LinshareFixture.ADMIN_ACCOUNT;
import static org.apache.james.linshare.LinshareFixture.USER_1;
import static org.apache.james.linshare.LinshareFixture.USER_CREDENTIAL_MAP;
import static org.apache.james.linshare.client.LinshareAPI.Headers.ACCEPT_APPLICATION_JSON;
import static org.apache.james.linshare.client.LinshareAPI.Headers.CONTENT_TYPE_APPLICATION_JSON;

import java.util.List;
import java.util.Optional;

import org.apache.james.linshare.client.Document;
import org.apache.james.linshare.client.LinshareAPI;
import org.apache.james.linshare.client.TechnicalAccountCreationRequest;
import org.apache.james.linshare.client.TechnicalAccountGrantPermissionsRequest;
import org.apache.james.linshare.client.TechnicalAccountResponse;
import org.apache.james.linshare.client.User;
import org.apache.james.utils.FakeSmtp;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.github.fge.lambdas.Throwing;

import feign.Feign;
import feign.Headers;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.auth.BasicAuthRequestInterceptor;
import feign.form.FormEncoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public class LinshareExtension implements BeforeEachCallback, BeforeAllCallback {

    private interface LinshareAPIForTesting {

        String CONTENT_DISPOSITION_ATTACHMENT = "Content-Disposition: attachment; filename=\"{filename}\"";
        String CONTENT_TYPE_APPLICATION_OCTET_STREAM = "Content-Type: application/octet-stream";

        static LinshareAPIForTesting from(LinshareFixture.Credential credential, Linshare linshare) {

            return Feign.builder()
                .requestInterceptor(new BasicAuthRequestInterceptor(credential.getUsername(), credential.getPassword()))
                .logger(new Slf4jLogger(LinshareAPIForTesting.class))
                .logLevel(Logger.Level.FULL)
                .encoder(new FormEncoder(new JacksonEncoder()))
                .decoder(CombinedDecoder.builder()
                    .defaultDecoder(new JacksonDecoder())
                    .registerSingleTypeDecoder(new ByteArrayDecoder())
                    .build())
                .target(LinshareAPIForTesting.class, linshare.getUrl());
        }

        @RequestLine("GET /linshare/webservice/rest/user/v2/authentication/jwt")
        @Headers(ACCEPT_APPLICATION_JSON)
        AuthorizationToken jwt();

        @RequestLine("GET /linshare/webservice/rest/user/v2/users")
        @Headers(ACCEPT_APPLICATION_JSON)
        List<User> allUsers();

        @RequestLine("GET /linshare/webservice/rest/admin/technical_accounts")
        @Headers(ACCEPT_APPLICATION_JSON)
        List<TechnicalAccountResponse> allTechnicalAccounts();

        @RequestLine("GET /linshare/webservice/rest/user/v2/received_shares/{documentId}/download")
        @Headers({ CONTENT_TYPE_APPLICATION_OCTET_STREAM, CONTENT_DISPOSITION_ATTACHMENT })
        byte[] downloadShare(@Param("documentId") String documentId, @Param("filename") String filename);

        @RequestLine("POST /linshare/webservice/rest/admin/technical_accounts")
        @Headers({ ACCEPT_APPLICATION_JSON, CONTENT_TYPE_APPLICATION_JSON })
        TechnicalAccountResponse createTechnicalAccount(TechnicalAccountCreationRequest accountCreationRequest);

        @RequestLine("PUT /linshare/webservice/rest/admin/technical_accounts")
        @Headers({ ACCEPT_APPLICATION_JSON, CONTENT_TYPE_APPLICATION_JSON })
        TechnicalAccountResponse grantTechnicalAccountPermissions(TechnicalAccountGrantPermissionsRequest accountGrantPermissionsRequest);
    }

    private final Linshare linshare = LinshareSingleton.singleton;

    @Override
    public void beforeEach(ExtensionContext context) {
        deleteAllUsersDocuments();
        FakeSmtp.clean(linshare.fakeSmtpRequestSpecification());
    }

    public Linshare getLinshare() {
        return linshare;
    }

    public LinshareAPI getAPIFor(LinshareFixture.Credential credential) throws Exception {
        return LinshareAPI.from(configurationWithJwtFor(credential));
    }

    public LinshareConfiguration configurationWithJwtFor(LinshareFixture.Credential credential) throws Exception {
        AuthorizationToken token = LinshareAPIForTesting.from(credential, linshare).jwt();

        return LinshareConfiguration.builder()
            .urlAsString(linshare.getUrl())
            .authorizationToken(token)
            .build();
    }

    public byte[] downloadSharedFile(LinshareFixture.Credential credential, Document.DocumentId document, String filename) {
        return LinshareAPIForTesting.from(credential, linshare)
            .downloadShare(document.asString(), filename);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext){
        createTechnicalAccount(TechnicalAccountCreationRequest.defaultAccount());
    }

    private void createTechnicalAccount(TechnicalAccountCreationRequest technicalAccountDTO) {
        TechnicalAccountResponse technicalAccountResponse = LinshareAPIForTesting.from(ADMIN_ACCOUNT, linshare).createTechnicalAccount(technicalAccountDTO);

        TechnicalAccountGrantPermissionsRequest technicalAccountGrantPermissionsRequest = new TechnicalAccountGrantPermissionsRequest(technicalAccountResponse);
        LinshareAPIForTesting.from(ADMIN_ACCOUNT, linshare).grantTechnicalAccountPermissions(technicalAccountGrantPermissionsRequest);
    }

    public List<TechnicalAccountResponse> getAllTechnicalAccounts(LinshareFixture.Credential credential) {
        return LinshareAPIForTesting.from(credential, linshare).allTechnicalAccounts();
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
}
