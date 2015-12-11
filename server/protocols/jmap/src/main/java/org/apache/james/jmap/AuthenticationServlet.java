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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.james.jmap.json.MultipleObjectMapperBuilder;
import org.apache.james.jmap.model.AccessTokenRequest;
import org.apache.james.jmap.model.ContinuationTokenRequest;
import org.apache.james.jmap.model.ContinuationTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AuthenticationServlet extends HttpServlet {

    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String JSON_CONTENT_TYPE_UTF8 = "application/json; charset=UTF-8";

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationServlet.class);

    private ObjectMapper mapper = new MultipleObjectMapperBuilder()
            .registerClass(ContinuationTokenRequest.UNIQUE_JSON_PATH, ContinuationTokenRequest.class)
            .registerClass(AccessTokenRequest.UNIQUE_JSON_PATH, AccessTokenRequest.class)
            .build();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!checkJsonContentType(req)) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!checkAcceptJsonOnly(req)) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Object request;
        try {
            request = mapper.readValue(req.getReader(), Object.class);
        } catch (Exception e) {
            LOG.warn("Invalid authentication request received.", e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (request instanceof ContinuationTokenRequest) {
            handleContinuationTokenRequest((ContinuationTokenRequest)request, resp);
        } else if (request instanceof AccessTokenRequest) {
            handleAccessTokenRequest((AccessTokenRequest)request, resp);
        }
    }

    private boolean checkJsonContentType(HttpServletRequest req) {
        return req.getContentType().equals(JSON_CONTENT_TYPE_UTF8);
    }

    private boolean checkAcceptJsonOnly(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        return accept != null && accept.contains(JSON_CONTENT_TYPE);
    }


    private void handleContinuationTokenRequest(ContinuationTokenRequest request, HttpServletResponse resp) throws IOException {
        resp.setContentType(JSON_CONTENT_TYPE_UTF8);

        ContinuationTokenResponse continuationTokenResponse = ContinuationTokenResponse
                .builder()
                // TODO Answer a real token
                .continuationToken("token")
                .methods(ContinuationTokenResponse.AuthenticationMethod.PASSWORD)
                .build();

        mapper.writeValue(resp.getOutputStream(), continuationTokenResponse);
    }

    private void handleAccessTokenRequest(AccessTokenRequest request, HttpServletResponse resp) throws IOException {
        resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

}
