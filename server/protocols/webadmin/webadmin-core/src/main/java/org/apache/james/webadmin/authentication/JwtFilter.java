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

            checkHeaderPresent(bearer);
            String login = retrieveUser(bearer);
            checkIsAdmin(bearer);
            checkIfNotAdmin(bearer);

            request.attribute(LOGIN, login);
        }
    }

    private void checkIfNotAdmin(Optional<String> bearer) throws JsonProcessingException {
        String token = bearer.get();
        DecodedJWT jwt = JWT.decode(token);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(jwt.getPayload()));
        System.out.println("Decoded Payload: " + payload);

        // Parse payload JSON
        ObjectMapper mapper = new ObjectMapper();
        JsonNode payloadNode = mapper.readTree(payload);

        // Extract and print fields with null checks
        JsonNode subNode = payloadNode.get("sub");
        JsonNode adminNode = payloadNode.get("admin");
        JsonNode expNode = payloadNode.get("exp");

        String sub = subNode != null ? subNode.asText() : "N/A";
        boolean admin = adminNode != null && adminNode.asBoolean();
        long exp = expNode != null ? expNode.asLong() : -1;

        System.out.println("Sub: " + sub);
        System.out.println("Admin: " + admin);
        System.out.println("Exp: " + exp);

        // Extract and print permissions with null check
        JsonNode permissions = payloadNode.get("permission");
        if (permissions != null && permissions.isArray()) {
            for (JsonNode permissionNode : permissions) {
                permissionNode.fields().forEachRemaining(entry ->
                        System.out.println("Permission: " + entry.getKey() + " = " + entry.getValue().asText()));
            }
        } else {
            System.out.println("Permissions: None");
        }
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
