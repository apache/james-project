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

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import spark.Request;
import spark.Response;

public class PasswordFilter implements AuthenticationFilter {
    public static final String PASSWORD = "Password";
    public static final String OPTIONS = "OPTIONS";
    public static final String AUTHORIZATION_HEADER_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";

    private static final String GET_METHOD = "GET";
    private static final String HEAD_METHOD = "HEAD";
    private static final String DELETE_METHOD = "DELETE";

    private final Optional<List<String>> passwords;
    private final Optional<List<String>> readOnlyPasswords;
    private final Optional<List<String>> noDeletePasswords;

    /**
     * @param passwordString optional comma-separated list of full-access passwords
     * @param readOnlyPasswordString optional comma-separated list of read-only passwords
     * @param noDeletePasswordString optional comma-separated list of no-delete passwords
     */
    @Inject
    public PasswordFilter(Optional<String> passwordString, Optional<String> readOnlyPasswordString, Optional<String> noDeletePasswordString) {
        this.passwords = splitOptionalPasswords(passwordString);
        this.readOnlyPasswords = splitOptionalPasswords(readOnlyPasswordString);
        this.noDeletePasswords = splitOptionalPasswords(noDeletePasswordString);
    }

    private Optional<List<String>> splitOptionalPasswords(Optional<String> optionalPasswordString) {
        return optionalPasswordString.map(this::splitPasswords);
    }

    private List<String> splitPasswords(String passwordString) {
        if (passwordString == null || passwordString.isEmpty()) {
            return ImmutableList.of();
        }
        return Splitter.on(',').splitToList(passwordString);
    }

    private enum AccessLevel {
        FULL,
        NO_DELETE,
        READ_ONLY,
        NONE
    }

    private AccessLevel getAccessLevel(String password) {
        if (passwords.isPresent() && passwords.get().contains(password)) {
            return AccessLevel.FULL;
        }
        if (noDeletePasswords.isPresent() && noDeletePasswords.get().contains(password)) {
            return AccessLevel.NO_DELETE;
        }
        if (readOnlyPasswords.isPresent() && readOnlyPasswords.get().contains(password)) {
            return AccessLevel.READ_ONLY;
        }
        return AccessLevel.NONE;
    }

    private boolean isAccessAllowed(AccessLevel accessLevel, String httpMethod) {
        switch (accessLevel) {
            case FULL:
                return true;
            case NO_DELETE:
                return !httpMethod.equals(DELETE_METHOD);
            case READ_ONLY:
                return httpMethod.equals(GET_METHOD) || httpMethod.equals(HEAD_METHOD);
            case NONE:
            default:
                return false;
        }
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        if (!request.requestMethod().equals(OPTIONS)) {
            Optional<String> password = Optional.ofNullable(request.headers(PASSWORD));
            Optional<String> authorization = Optional.ofNullable(request.headers(AUTHORIZATION_HEADER_NAME));

            Optional<String> providedPassword = password
                .or(() -> authorization
                    .filter(value -> value.startsWith(AUTHORIZATION_HEADER_PREFIX))
                    .map(value -> value.substring(AUTHORIZATION_HEADER_PREFIX.length())));

            if (providedPassword.isEmpty()) {
                halt(HttpStatus.UNAUTHORIZED_401, "No Password in header.");
                return;
            }

            AccessLevel accessLevel = getAccessLevel(providedPassword.get());
            if (accessLevel == AccessLevel.NONE) {
                halt(HttpStatus.UNAUTHORIZED_401, "Wrong password.");
                return;
            }

            if (!isAccessAllowed(accessLevel, request.requestMethod())) {
                halt(HttpStatus.FORBIDDEN_403, "Insufficient permissions for this operation.");
            }
        }
    }

}
