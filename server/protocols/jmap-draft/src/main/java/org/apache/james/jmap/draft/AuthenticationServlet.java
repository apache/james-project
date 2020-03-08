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
package org.apache.james.jmap.draft;

import static org.apache.james.metrics.api.TimeMetric.ExecutionResult.DEFAULT_100_MS_THRESHOLD;

import java.io.IOException;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.draft.api.AccessTokenManager;
import org.apache.james.jmap.draft.api.SimpleTokenFactory;
import org.apache.james.jmap.draft.api.SimpleTokenManager;
import org.apache.james.jmap.draft.exceptions.BadRequestException;
import org.apache.james.jmap.draft.exceptions.InternalErrorException;
import org.apache.james.jmap.draft.json.MultipleObjectMapperBuilder;
import org.apache.james.jmap.draft.model.AccessTokenRequest;
import org.apache.james.jmap.draft.model.AccessTokenResponse;
import org.apache.james.jmap.draft.model.ContinuationTokenRequest;
import org.apache.james.jmap.draft.model.ContinuationTokenResponse;
import org.apache.james.jmap.draft.model.EndPointsResponse;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

public class AuthenticationServlet extends HttpServlet {

    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String JSON_CONTENT_TYPE_UTF8 = "application/json; charset=UTF-8";

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationServlet.class);

    private final ObjectMapper mapper;
    private final UsersRepository usersRepository;
    private final SimpleTokenManager simpleTokenManager;
    private final AccessTokenManager accessTokenManager;
    private final SimpleTokenFactory simpleTokenFactory;
    private final MetricFactory metricFactory;
    
    @Inject
    @VisibleForTesting AuthenticationServlet(UsersRepository usersRepository, SimpleTokenManager simpleTokenManager, SimpleTokenFactory simpleTokenFactory, AccessTokenManager accessTokenManager, MetricFactory metricFactory) {
        this.usersRepository = usersRepository;
        this.simpleTokenManager = simpleTokenManager;
        this.simpleTokenFactory = simpleTokenFactory;
        this.accessTokenManager = accessTokenManager;
        this.metricFactory = metricFactory;
        this.mapper = new MultipleObjectMapperBuilder()
            .registerClass(ContinuationTokenRequest.UNIQUE_JSON_PATH, ContinuationTokenRequest.class)
            .registerClass(AccessTokenRequest.UNIQUE_JSON_PATH, AccessTokenRequest.class)
            .build();
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        TimeMetric timeMetric = metricFactory.timer("JMAP-authentication-post");
        try {
            assertJsonContentType(req);
            assertAcceptJsonOnly(req);

            Object request = deserialize(req);

            if (request instanceof ContinuationTokenRequest) {
                handleContinuationTokenRequest((ContinuationTokenRequest)request, resp);
            } else if (request instanceof AccessTokenRequest) {
                handleAccessTokenRequest((AccessTokenRequest)request, resp);
            }
        } catch (BadRequestException e) {
            LOG.warn("Invalid authentication request received.", e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (InternalErrorException e) {
            LOG.error("Internal error", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            timeMetric.stopAndPublish().logWhenExceedP99(DEFAULT_100_MS_THRESHOLD);
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        returnEndPointsResponse(resp);
    }
    
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        accessTokenManager.revoke(AccessToken.fromString(req.getHeader("Authorization")));
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private Object deserialize(HttpServletRequest req) throws BadRequestException {
        Object request;
        try {
            request = mapper.readValue(req.getReader(), Object.class);
        } catch (IOException e) {
            throw new BadRequestException("Request can't be deserialized", e);
        }
        return request;
    }

    private void assertJsonContentType(HttpServletRequest req) {
        if (! req.getContentType().equals(JSON_CONTENT_TYPE_UTF8)) {
            throw new BadRequestException("Request ContentType header must be set to: " + JSON_CONTENT_TYPE_UTF8);
        }
    }

    private void assertAcceptJsonOnly(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        if (accept == null || ! accept.contains(JSON_CONTENT_TYPE)) {
            throw new BadRequestException("Request Accept header must be set to JSON content type");
        }
    }

    private void handleContinuationTokenRequest(ContinuationTokenRequest request, HttpServletResponse resp) throws IOException {
        resp.setContentType(JSON_CONTENT_TYPE_UTF8);
        try {
            ContinuationTokenResponse continuationTokenResponse = ContinuationTokenResponse
                .builder()
                .continuationToken(simpleTokenFactory.generateContinuationToken(request.getUsername()))
                .methods(ContinuationTokenResponse.AuthenticationMethod.PASSWORD)
                .build();
            mapper.writeValue(resp.getOutputStream(), continuationTokenResponse);
        } catch (Exception e) {
            throw new InternalErrorException("Error while responding to continuation token", e);
        }
    }

    private void handleAccessTokenRequest(AccessTokenRequest request, HttpServletResponse resp) throws IOException {
        switch (simpleTokenManager.getValidity(request.getToken())) {
        case EXPIRED:
            returnRestartAuthentication(resp);
            break;
        case INVALID:
            LOG.warn("Use of an invalid ContinuationToken : {}", request.getToken().serialize());
            returnUnauthorizedResponse(resp);
            break;
        case OK:
            manageAuthenticationResponse(request, resp);
            break;
        }
    }

    private void manageAuthenticationResponse(AccessTokenRequest request, HttpServletResponse resp) throws IOException {
        Username username = Username.of(request.getToken().getUsername());
        if (authenticate(request, username)) {
            returnAccessTokenResponse(resp, username);
        } else {
            LOG.info("Authentication failure for {}", username);
            returnUnauthorizedResponse(resp);
        }
    }

    private boolean authenticate(AccessTokenRequest request, Username username) {
        boolean authenticated = false;
        try {
            authenticated = usersRepository.test(username, request.getPassword());
        } catch (UsersRepositoryException e) {
            LOG.error("Error while trying to validate authentication for user '{}'", username, e);
        }
        return authenticated;
    }

    private void returnAccessTokenResponse(HttpServletResponse resp, Username username) throws IOException {
        resp.setContentType(JSON_CONTENT_TYPE_UTF8);
        resp.setStatus(HttpServletResponse.SC_CREATED);
        AccessTokenResponse response = AccessTokenResponse
            .builder()
            .accessToken(accessTokenManager.grantAccessToken(username))
            .api(JMAPUrls.JMAP)
            .eventSource("/notImplemented")
            .upload(JMAPUrls.UPLOAD)
            .download(JMAPUrls.DOWNLOAD)
            .build();
        mapper.writeValue(resp.getOutputStream(), response);
    }

    private void returnEndPointsResponse(HttpServletResponse resp) throws IOException {
        resp.setContentType(JSON_CONTENT_TYPE_UTF8);
        resp.setStatus(HttpServletResponse.SC_OK);
        EndPointsResponse response = EndPointsResponse
            .builder()
            .api(JMAPUrls.JMAP)
            .eventSource("/notImplemented")
            .upload(JMAPUrls.UPLOAD)
            .download(JMAPUrls.DOWNLOAD)
            .build();
        mapper.writeValue(resp.getOutputStream(), response);
    }

    private void returnUnauthorizedResponse(HttpServletResponse resp) throws IOException {
        resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private void returnRestartAuthentication(HttpServletResponse resp) throws IOException {
        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
