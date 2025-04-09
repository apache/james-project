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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.core.Username;
import org.apache.james.jwt.JwtTokenVerifier;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;
import spark.Response;

public class JwtFilter implements AuthenticationFilter {
    public static final String AUTHORIZATION_HEADER_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    public static final String OPTIONS = "OPTIONS";

    private final UsersRepository usersRepository;
    private final JwtTokenVerifier jwtTokenVerifier;

    @Inject
    public JwtFilter(@Named("webadmin") JwtTokenVerifier.Factory jwtTokenVerifierFactory, UsersRepository usersRepository) {
        this.jwtTokenVerifier = jwtTokenVerifierFactory.create();
        this.usersRepository = usersRepository;
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        if (!request.requestMethod().equals(OPTIONS)) {
            Optional<String> bearer = Optional.ofNullable(request.headers(AUTHORIZATION_HEADER_NAME))
                .filter(value -> value.startsWith(AUTHORIZATION_HEADER_PREFIX))
                .map(value -> value.substring(AUTHORIZATION_HEADER_PREFIX.length()));

            checkHeaderPresent(bearer);
            String login = retrieveUser(bearer);

            Optional<String> userType = jwtTokenVerifier.verifyAndExtractClaim(bearer.get(), "type", String.class);
            if (userType.isEmpty()) {
                halt(HttpStatus.UNAUTHORIZED_401, "Missing user type in payload");
            }

            switch (userType.get()) {
                case "agent":
                    Optional<String> userName = jwtTokenVerifier.verifyAndExtractClaim(bearer.get(), "sub", String.class);
                    if (userName.isEmpty()) {
                        halt(HttpStatus.UNAUTHORIZED_401, "Expected 'sub' claim to contain username");
                    }
                    
                    try {
                        if (!usersRepository.contains(Username.of(userName.get()))) {
                            halt(HttpStatus.UNAUTHORIZED_401, "User not found");
                        }
                    } catch (UsersRepositoryException e) {
                        throw new RuntimeException(e);
                    }

                    Optional<LinkedHashMap> permissionObject = jwtTokenVerifier.verifyAndExtractClaim(bearer.get(), "permissions", LinkedHashMap.class);

                    if (permissionObject.isEmpty()) {
                        halt(HttpStatus.UNAUTHORIZED_401, "Permissions claim not found for agent.");
                    }

                    LinkedHashMap<String, List<String>> permissionClaims = new LinkedHashMap<>();
                    permissionObject.get().forEach((key, value) -> {
                        if (!(key instanceof String)) {
                            throw new IllegalArgumentException("Invalid key type: " + key);
                        }
                        if (!(value instanceof List<?>)) {
                            throw new IllegalArgumentException("Invalid value type for key '" + key + "': " + value);
                        }
                        List<?> valueList = (List<?>)value;
                        for (Object item : valueList) {
                            if (!(item instanceof String)) {
                                throw new IllegalArgumentException("Invalid value type for List value for key '" + key + "' " + item);
                            }
                        }
                        permissionClaims.put((String) key, (List<String>) value);
                    });

                    verifyAgentAuthorization(permissionClaims, request);
                    break;
                case "admin":
                    break;
                default:
                    halt(HttpStatus.UNAUTHORIZED_401, "Non authorized user. Unknown user type : '" + userType.get() + "'");
            }

            request.attribute(LOGIN, login);
        }
    }

    private void verifyAgentAuthorization(LinkedHashMap<String, List<String>> permissionClaims, Request request) {
        String requestMethod =  request.requestMethod();
        String requestPath = "perm" + request.servletPath();

        AtomicBoolean authorized = new AtomicBoolean(false);

        permissionClaims.forEach((authorizedPath, permissions) -> {
            if (!authorizedPathMatchesRequestPath(authorizedPath, requestPath)) {
                return;
            }

            for (String permission: permissions) {
                if (requestMethod.equals(permission)) {
                    authorized.set(true);
                    return;
                }
            }
        });

        if (authorized.get()) {
            return;
        }

        halt(HttpStatus.UNAUTHORIZED_401, "Non authorized user. Do not have permission.");
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

    private boolean authorizedPathMatchesRequestPath(String authorizedPath, String requestPath) {
        String[] authorizedPathArr = authorizedPath.split("\\.");
        String[] requestPathArr = requestPath.split("/");

        return authorizedPathArr.length == requestPathArr.length &&
                IntStream.range(0, authorizedPathArr.length)
                        .allMatch(i -> authorizedPathArr[i].equals("*") || authorizedPathArr[i].equals(requestPathArr[i]));
    }
}
