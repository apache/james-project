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
import static org.apache.james.linshare.LinshareFixture.TECHNICAL_ACCOUNT;
import static org.apache.james.linshare.LinshareFixture.USER_1;
import static org.apache.james.linshare.LinshareFixture.USER_CREDENTIAL_MAP;
import static org.apache.james.linshare.client.LinshareAPI.Headers.ACCEPT_APPLICATION_JSON;
import static org.apache.james.linshare.client.LinshareAPI.Headers.CONTENT_TYPE_APPLICATION_JSON;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

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

public class LinshareExtension implements BeforeEachCallback, BeforeAllCallback, ParameterResolver {

    private static final Linshare linshare = LinshareSingleton.singleton;

    private UUID technicalAccountUUID;

    public interface LinshareAPIForAdminTesting {
        @VisibleForTesting
        static LinshareAPIForAdminTesting from(LinshareFixture.Credential credential) {

            return Feign.builder()
                .requestInterceptor(new BasicAuthRequestInterceptor(credential.getUsername(), credential.getPassword()))
                .logger(new Slf4jLogger(LinshareAPIForAdminTesting.class))
                .logLevel(Logger.Level.FULL)
                .encoder(new FormEncoder(new JacksonEncoder()))
                .decoder(CombinedDecoder.builder()
                    .defaultDecoder(new JacksonDecoder())
                    .registerSingleTypeDecoder(new ByteArrayDecoder())
                    .build())
                .target(LinshareAPIForAdminTesting.class, linshare.getUrl());
        }

        @RequestLine("GET /linshare/webservice/rest/admin/technical_accounts")
        @Headers(ACCEPT_APPLICATION_JSON)
        List<TechnicalAccountResponse> allTechnicalAccounts();

        @RequestLine("POST /linshare/webservice/rest/admin/technical_accounts")
        @Headers({ ACCEPT_APPLICATION_JSON, CONTENT_TYPE_APPLICATION_JSON })
        TechnicalAccountResponse createTechnicalAccount(TechnicalAccountCreationRequest accountCreationRequest);

        @RequestLine("PUT /linshare/webservice/rest/admin/technical_accounts")
        @Headers({ ACCEPT_APPLICATION_JSON, CONTENT_TYPE_APPLICATION_JSON })
        TechnicalAccountResponse grantTechnicalAccountPermissions(TechnicalAccountGrantPermissionsRequest accountGrantPermissionsRequest);
    }

    public interface LinshareAPIForTechnicalAccountTesting {
        @VisibleForTesting
        static LinshareAPIForTechnicalAccountTesting from(LinshareFixture.Credential credential) {

            return Feign.builder()
                .requestInterceptor(new BasicAuthRequestInterceptor(credential.getUsername(), credential.getPassword()))
                .logger(new Slf4jLogger(LinshareAPIForTechnicalAccountTesting.class))
                .logLevel(Logger.Level.FULL)
                .encoder(new FormEncoder(new JacksonEncoder()))
                .decoder(CombinedDecoder.builder()
                    .defaultDecoder(new JacksonDecoder())
                    .registerSingleTypeDecoder(new ByteArrayDecoder())
                    .build())
                .target(LinshareAPIForTechnicalAccountTesting.class, linshare.getUrl());
        }

        @RequestLine("GET /linshare/webservice/rest/user/documents/{documentId}/download")
        @Headers({ACCEPT_APPLICATION_JSON, CONTENT_TYPE_APPLICATION_JSON})
        byte[] download(@Param("documentId") String documentId);

        default byte[] downloadFileFrom(LinshareFixture.Credential credential, Document.DocumentId document) {
            return from(credential).download(document.asString());
        }
    }

    public interface LinshareAPIForUserTesting {
        @VisibleForTesting
         static LinshareAPIForUserTesting from(LinshareFixture.Credential credential) {

            return Feign.builder()
                .requestInterceptor(new BasicAuthRequestInterceptor(credential.getUsername(), credential.getPassword()))
                .logger(new Slf4jLogger(LinshareAPIForUserTesting.class))
                .logLevel(Logger.Level.FULL)
                .encoder(new FormEncoder(new JacksonEncoder()))
                .decoder(CombinedDecoder.builder()
                    .defaultDecoder(new JacksonDecoder())
                    .registerSingleTypeDecoder(new ByteArrayDecoder())
                    .build())
                .target(LinshareAPIForUserTesting.class, linshare.getUrl());
        }

        @RequestLine("GET /linshare/webservice/rest/user/v2/documents")
        @feign.Headers(ACCEPT_APPLICATION_JSON)
        List<Document> listAllDocuments();

        @RequestLine("DELETE /linshare/webservice/rest/user/v2/documents/{documentId}")
        @feign.Headers({ ACCEPT_APPLICATION_JSON, CONTENT_TYPE_APPLICATION_JSON })
        Document delete(@Param("documentId") String documentId);

        default Document delete(Document.DocumentId documentId) {
            return delete(documentId.asString());
        }

        default void deleteAllDocuments() {
            listAllDocuments().forEach(document -> delete(document.getId()));
        }

        @RequestLine("GET /linshare/webservice/rest/user/v2/users")
        @Headers(ACCEPT_APPLICATION_JSON)
        List<User> allUsers();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        createTechnicalAccount(TechnicalAccountCreationRequest.defaultAccount());
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        deleteAllUsersDocuments();
        FakeSmtp.clean(linshare.fakeSmtpRequestSpecification());
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == LinshareAPIForTechnicalAccountTesting.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Preconditions.checkArgument(parameterContext.getParameter().getType() == LinshareAPIForTechnicalAccountTesting.class);
        return getDelegationAccountTestingAPI();
    }

    public LinshareAPI getDelegationAccountAPI() throws Exception {
        return LinshareAPI.from(configurationWithBasicAuthFor(
            new LinshareFixture.Credential(
                technicalAccountUUID.toString(),
                TECHNICAL_ACCOUNT.getPassword())));
    }

    public LinshareAPIForTechnicalAccountTesting getDelegationAccountTestingAPI() {
        return LinshareAPIForTechnicalAccountTesting.from(
            new LinshareFixture.Credential(
                technicalAccountUUID.toString(),
                TECHNICAL_ACCOUNT.getPassword()));
    }

    public LinshareConfiguration configurationWithBasicAuthFor(LinshareFixture.Credential credential) throws Exception {
        return LinshareConfiguration.builder()
            .urlAsString(linshare.getUrl())
            .basicAuthorization(credential.getUsername(), credential.getPassword())
            .build();
    }

    private void createTechnicalAccount(TechnicalAccountCreationRequest technicalAccountCreationRequest) {
        TechnicalAccountResponse technicalAccountResponse = LinshareAPIForAdminTesting.from(ADMIN_ACCOUNT).createTechnicalAccount(technicalAccountCreationRequest);

        TechnicalAccountGrantPermissionsRequest technicalAccountGrantPermissionsRequest = new TechnicalAccountGrantPermissionsRequest(technicalAccountResponse);
        LinshareAPIForAdminTesting.from(ADMIN_ACCOUNT).grantTechnicalAccountPermissions(technicalAccountGrantPermissionsRequest);
        this.technicalAccountUUID = UUID.fromString(technicalAccountResponse.getUuid());
    }

    private void deleteAllUsersDocuments() {
        LinshareAPIForUserTesting.from(USER_1)
            .allUsers()
            .stream()
            .map(this::getUsernamePassword)
            .map(LinshareAPIForUserTesting::from)
            .forEach(LinshareAPIForUserTesting::deleteAllDocuments);
    }

    private LinshareFixture.Credential getUsernamePassword(User user) {
        return Optional.ofNullable(USER_CREDENTIAL_MAP.get(user.getMail()))
            .orElseThrow(() -> new RuntimeException("cannot get token of user " + user.getMail()));
    }

    public UUID getTechnicalAccountUUID() {
        return technicalAccountUUID;
    }

    public Linshare getLinshare() {
        return linshare;
    }
}
