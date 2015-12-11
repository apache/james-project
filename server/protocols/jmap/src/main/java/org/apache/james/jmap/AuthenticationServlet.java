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
package org.apache.james.jmap;

import java.io.IOException;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.james.jmap.api.AccessTokenManager;
import org.apache.james.jmap.api.ContinuationTokenManager;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.exceptions.BadRequestException;
import org.apache.james.jmap.exceptions.InternalErrorException;
import org.apache.james.jmap.json.MultipleObjectMapperBuilder;
import org.apache.james.jmap.model.AccessTokenRequest;
import org.apache.james.jmap.model.AccessTokenResponse;
import org.apache.james.jmap.model.ContinuationTokenRequest;
import org.apache.james.jmap.model.ContinuationTokenResponse;
import org.apache.james.jmap.model.EndPointsResponse;
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
    private final ContinuationTokenManager continuationTokenManager;
    private final AccessTokenManager accessTokenManager;
    
    @Inject
    @VisibleForTesting AuthenticationServlet(UsersRepository usersRepository, ContinuationTokenManager continuationTokenManager, AccessTokenManager accessTokenManager) {
        this.usersRepository = usersRepository;
        this.continuationTokenManager = continuationTokenManager;
        this.accessTokenManager = accessTokenManager;
        this.mapper = new MultipleObjectMapperBuilder()
            .registerClass(ContinuationTokenRequest.UNIQUE_JSON_PATH, ContinuationTokenRequest.class)
            .registerClass(AccessTokenRequest.UNIQUE_JSON_PATH, AccessTokenRequest.class)
            .build();
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
                .continuationToken(continuationTokenManager.generateToken(request.getUsername()))
                .methods(ContinuationTokenResponse.AuthenticationMethod.PASSWORD)
                .build();
            mapper.writeValue(resp.getOutputStream(), continuationTokenResponse);
        } catch (Exception e) {
            throw new InternalErrorException("Error while responding to continuation token", e);
        }
    }

    private void handleAccessTokenRequest(AccessTokenRequest request, HttpServletResponse resp) throws IOException {
        try {
            if (!continuationTokenManager.isValid(request.getToken())) {
                LOG.warn("Use of an invalid ContinuationToken : " + request.getToken().serialize());
                returnUnauthorizedResponse(resp);
            } else {
                manageAuthenticationResponse(request, resp);
            }
        } catch(Exception e) {
            throw new InternalErrorException("Internal error while managing access token request", e);
        }
    }

    private void manageAuthenticationResponse(AccessTokenRequest request, HttpServletResponse resp) throws IOException {
        String username = request.getToken().getUsername();
        if (authenticate(request, username)) {
            returnAccessTokenResponse(resp, username);
        } else {
            LOG.info("Authentication failure for " + username);
            returnUnauthorizedResponse(resp);
        }
    }

    private boolean authenticate(AccessTokenRequest request, String username) {
        boolean authenticated = false;
        try {
            authenticated = usersRepository.test(username, request.getPassword());
        } catch (UsersRepositoryException e) {
            LOG.error("Error while trying to validate authentication for user '{}'", username, e);
        }
        return authenticated;
    }

    private void returnAccessTokenResponse(HttpServletResponse resp, String username) throws IOException {
        resp.setContentType(JSON_CONTENT_TYPE_UTF8);
        resp.setStatus(HttpServletResponse.SC_CREATED);
        AccessTokenResponse response = AccessTokenResponse
            .builder()
            .accessToken(accessTokenManager.grantAccessToken(username))
            // TODO Send API endpoints
            .build();
        mapper.writeValue(resp.getOutputStream(), response);
    }

    private void returnEndPointsResponse(HttpServletResponse resp) throws IOException {
        resp.setContentType(JSON_CONTENT_TYPE_UTF8);
        resp.setStatus(HttpServletResponse.SC_OK);
        EndPointsResponse response = EndPointsResponse
            .builder()
            .api("/api")
            // TODO Send other API endpoints
            .build();
        mapper.writeValue(resp.getOutputStream(), response);
    }

    private void returnUnauthorizedResponse(HttpServletResponse resp) throws IOException {
        resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

}
