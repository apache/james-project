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
import java.util.concurrent.atomic.AtomicBoolean;

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
        if (sub.equals("admin")) {
            return;
        } else if (!sub.equals("agent")) {
            halt(HttpStatus.UNAUTHORIZED_401, "Non authorized user.Subject is not agent");
        }
        String pathProcessedValue = getProcessPath(request.pathInfo());
        JsonNode permissionsNode = payloadNode.get("permissions");

        AtomicBoolean flag = new AtomicBoolean(false);
        //Checking full match
        if (permissionsNode != null && permissionsNode.isArray()) {
            for (JsonNode permissionNode : permissionsNode) {
                JsonNode groupNode = permissionNode.get(pathProcessedValue);
                if (groupNode != null && groupNode.isArray()) {
                    for (JsonNode valueNode : groupNode) {
                        if (request.requestMethod().equals(valueNode.asText())) {
                            flag.set(true);
                            return;
                        }
                    }
                }
            }
        }
        if (flag.get()) {
            return;
        }

        String requestMethod = request.requestMethod().toString();
        //Dynamic Check
        JsonNode permissions = payloadNode.get("permissions");
        if (permissions != null && permissions.isArray()) {
            for (JsonNode permissionNode : permissions) {
                permissionNode.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    if (match(key, "perm" + request.pathInfo())) {
                        JsonNode groupValueNode = permissionNode.get(key);
                        for (JsonNode valueNode : groupValueNode) {
                            if (requestMethod.equals(valueNode.asText())) {
                                flag.set(true);
                                return;
                            }
                        }
                    }
                });
            }
        }
        if (flag.get()) {
            return;
        }
        halt(HttpStatus.UNAUTHORIZED_401, "Non authorized user.Do not have permission.");
    }

    private String getProcessPath(String path) {
        String result = path.replace('/', '.');
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

    private boolean match(String key, String path) {
        String key1 = key.replace('.', '@');
        String[] keyArr = key1.split("@");
        String[] pathArr = path.split("/");

        if (pathArr.length < keyArr.length) {
            return false;
        }
        Integer it = 0;
        Integer it1 = 0;
        Integer lenth = keyArr.length;
        Integer lenth1 = pathArr.length;

        while (it1 < lenth1) {
            if (keyArr[it].equals(pathArr[it1])) {
                it++; it1++;
                if (it == lenth) {
                    return true;
                }
            } else {
                it1++;
            }
        }
        return false;
    }
}
