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

package org.apache.james.linshare.client;

import static org.apache.james.linshare.client.LinshareAPI.Headers.ACCEPT_APPLICATION_JSON;
import static org.apache.james.linshare.client.LinshareAPI.Headers.CONTENT_TYPE_APPLICATION_JSON;
import static org.apache.james.linshare.client.LinshareAPI.Headers.CONTENT_TYPE_MULTIPART;

import java.io.File;
import java.util.List;

import org.apache.james.linshare.AuthorizationToken;
import org.apache.james.linshare.LinshareConfiguration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import feign.Feign;
import feign.Logger;
import feign.Param;
import feign.RequestInterceptor;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.form.FormEncoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public interface LinshareAPI {

    interface Headers {
        String ACCEPT_APPLICATION_JSON = "Accept: application/json";
        String CONTENT_TYPE_MULTIPART = "Content-Type: multipart/form-data";
        String CONTENT_TYPE_APPLICATION_JSON = "Content-Type: application/json";
    }

    class AuthorizationInterceptor implements RequestInterceptor {

        private final AuthorizationToken authorizationToken;

        AuthorizationInterceptor(AuthorizationToken authorizationToken) {
            this.authorizationToken = authorizationToken;
        }

        @Override
        public void apply(RequestTemplate template) {
            template.header("Authorization", authorizationToken.asBearerHeader());
        }
    }

    @VisibleForTesting
    static LinshareAPI from(LinshareConfiguration configuration) {
        return Feign.builder()
            .requestInterceptor(new AuthorizationInterceptor(configuration.getToken()))
            .logger(new Slf4jLogger(LinshareAPI.class))
            .logLevel(Logger.Level.FULL)
            .encoder(new FormEncoder(new JacksonEncoder()))
            .decoder(new JacksonDecoder())
            .target(LinshareAPI.class, configuration.getUrl().toString());
    }

    @RequestLine("GET /linshare/webservice/rest/user/v2/documents")
    @feign.Headers(ACCEPT_APPLICATION_JSON)
    List<Document> listAllDocuments();

    @RequestLine("POST /linshare/webservice/rest/user/v2/documents")
    @feign.Headers({ ACCEPT_APPLICATION_JSON, CONTENT_TYPE_MULTIPART })
    Document uploadDocument(@Param("file") File file, @Param("filesize") long fileSize);

    default Document uploadDocument(File file) {
        Preconditions.checkNotNull(file);
        Preconditions.checkArgument(file.exists(), "File to upload does not exist: %s", file.getAbsolutePath());

        return uploadDocument(file, file.length());
    }

    @RequestLine("POST /linshare/webservice/rest/user/v2/shares")
    @feign.Headers({ ACCEPT_APPLICATION_JSON, CONTENT_TYPE_APPLICATION_JSON })
    List<ShareResult> share(ShareRequest request);

    @RequestLine("DELETE /linshare/webservice/rest/user/v2/documents/{documentId}")
    @feign.Headers({ ACCEPT_APPLICATION_JSON, CONTENT_TYPE_APPLICATION_JSON })
    Document delete(@Param("documentId") String documentId);

    default Document delete(Document.DocumentId documentId) {
        return delete(documentId.asString());
    }

    @VisibleForTesting
    default void deleteAllDocuments() {
        listAllDocuments()
            .forEach(document -> delete(document.getId()));
    }

    @RequestLine("GET /linshare/webservice/rest/user/v2/received_shares")
    @feign.Headers({ ACCEPT_APPLICATION_JSON, CONTENT_TYPE_APPLICATION_JSON })
    List<ReceivedShare> receivedShares();
}
