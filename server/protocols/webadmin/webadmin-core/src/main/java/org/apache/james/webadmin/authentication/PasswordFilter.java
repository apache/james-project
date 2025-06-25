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

import spark.Request;
import spark.Response;

public class PasswordFilter implements AuthenticationFilter {
    public static final String PASSWORD = "Password";
    public static final String OPTIONS = "OPTIONS";

    private final List<String> passwords;

    @Inject
    public PasswordFilter(String passwordString) {
        this.passwords = Splitter.on(',')
            .splitToList(passwordString);
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        if (!request.requestMethod().equals(OPTIONS)) {
            Optional<String> password = Optional.ofNullable(request.headers(PASSWORD));

            if (!password.isPresent()) {
                halt(HttpStatus.UNAUTHORIZED_401, "No Password header.");
            }
            if (!passwords.contains(password.get())) {
                halt(HttpStatus.UNAUTHORIZED_401, "Wrong Password header.");
            }
        }
    }

}
