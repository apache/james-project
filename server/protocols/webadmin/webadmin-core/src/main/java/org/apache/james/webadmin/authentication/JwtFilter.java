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

package org.apache.james.webadmin.authentication;

import static spark.Spark.halt;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.jwt.JwtTokenVerifier;
import org.eclipse.jetty.http.HttpStatus;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import spark.Request;
import spark.Response;

public class JwtFilter implements AuthenticationFilter {
    public static final String AUTHORIZATION_HEADER_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    public static final String OPTIONS = "OPTIONS";

    private final JwtTokenVerifier jwtTokenVerifier;

    @Inject
    public JwtFilter(@Named("webadmin") JwtTokenVerifier.Factory jwtTokenVerifierFactory) {
        this.jwtTokenVerifier = jwtTokenVerifierFactory.create();
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        if (!request.requestMethod().equals(OPTIONS)) {
            Optional<String> bearer = Optional.ofNullable(request.headers(AUTHORIZATION_HEADER_NAME))
                .filter(value -> value.startsWith(AUTHORIZATION_HEADER_PREFIX))
                .map(value -> value.substring(AUTHORIZATION_HEADER_PREFIX.length()));

            System.out.println("halaaaaaaaa     " + request.pathInfo() + "      " + request.requestMethod());

            checkHeaderPresent(bearer);
            String login = retrieveUser(bearer); // subject field can't be null
            checkIsAdmin(bearer); // admin field should be true
            checkIfNotAdmin(bearer,request);
            request.attribute(LOGIN, login);
        }
    }

    private void checkIfNotAdmin(Optional<String> bearer, Request request) throws JsonProcessingException {
        String token = bearer.get();
        DecodedJWT jwt = JWT.decode(token);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(jwt.getPayload()));
        // Parse payload JSON
        ObjectMapper mapper = new ObjectMapper();
        JsonNode payloadNode = mapper.readTree(payload);

        JsonNode subNode = payloadNode.get("sub");
        String sub = subNode != null ? subNode.asText() : "N/A";
        if (sub == "admin") {
            return;
        } else if (!sub.equals("agent")) {
            halt(HttpStatus.UNAUTHORIZED_401, "Non authorized user.Subject is not agent");
        }
        String pathValue = getProcessPath(request.pathInfo());
        JsonNode permissionsNode = payloadNode.get("permissions");
        boolean hasGetPermission = false;
        if (permissionsNode != null && permissionsNode.isArray()) {
            for (JsonNode permissionNode : permissionsNode) {
                JsonNode valueNode = permissionNode.get(pathValue);

                if (valueNode != null && request.requestMethod().equals(valueNode.asText())) {
                    hasGetPermission = true;
                    break;
                }
            }
        }
        if (!hasGetPermission) {
            halt(HttpStatus.UNAUTHORIZED_401, "Non authorized user.Do not have permission.");
        }
    }

    private String getProcessPath(String path) {
        String first15Chars = path.substring(0, 15);
        String result = first15Chars.replace('/', '.');
        return "perm" + result;
    }

    private void checkHeaderPresent(Optional<String> bearer) {
        if (!bearer.isPresent()) {
            halt(HttpStatus.UNAUTHORIZED_401, "No Bearer header.");
        }
    }

    private String retrieveUser(Optional<String> bearer) {
        return jwtTokenVerifier.verifyAndExtractLogin(bearer.get())
            .orElseThrow(() -> halt(HttpStatus.UNAUTHORIZED_401, "Invalid Bearer header."));
    }

    private void checkIsAdmin(Optional<String> bearer) {
        if (!jwtTokenVerifier.hasAttribute("admin", true, bearer.get())) {
            halt(HttpStatus.UNAUTHORIZED_401, "Non authorized user.");
        }
    }
}
