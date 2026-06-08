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

package org.apache.james.webadmin.utils;

import java.util.function.Supplier;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.HaltException;

public class Parsers {

    private static final Logger LOGGER = LoggerFactory.getLogger(Parsers.class);

    public static Domain parseDomain(String domain) {
        try {
            return Domain.of(domain);
        } catch (IllegalArgumentException e) {
            throw invalidArgument("Invalid arguments supplied in the user request", e);
        }
    }

    public static Username parseUsername(String username) {
        try {
            return Username.of(username);
        } catch (IllegalArgumentException e) {
            throw invalidArgument("Invalid arguments supplied in the user request", e);
        }
    }

    public static <T> T parseId(String label, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw invalidArgument("Error while parsing '" + label + "'", e);
        }
    }

    private static HaltException invalidArgument(String message, Exception cause) {
        LOGGER.info(message, cause);
        return ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorType.INVALID_ARGUMENT)
            .message(message)
            .cause(cause)
            .haltError();
    }
}
