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

import java.util.Optional;

import org.apache.james.util.streams.Limit;
import org.apache.james.util.streams.Offset;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Strings;

import spark.Request;

public class ParametersExtractor {

    public static final String LIMIT_PARAMETER_NAME = "limit";
    public static final String OFFSET_PARAMETER_NAME = "offset";

    public static Limit extractLimit(Request request) {
        return Limit.from(assertPositiveInteger(request, LIMIT_PARAMETER_NAME)
                .map(value -> assertNotZero(value, LIMIT_PARAMETER_NAME)));
    }

    public static Offset extractOffset(Request request) {
        return Offset.from(assertPositiveInteger(request, OFFSET_PARAMETER_NAME));
    }

    public static Optional<Double> extractPositiveDouble(Request request, String parameterName) {
        return extractPositiveNumber(request, parameterName)
                .map(Number::doubleValue);
    }

    private static Optional<Integer> assertPositiveInteger(Request request, String parameterName) {
        return extractPositiveNumber(request, parameterName)
                .map(Number::intValue);
    }

    private static Optional<Number> extractPositiveNumber(Request request, String parameterName) {
        try {
            return Optional.ofNullable(request.queryParams(parameterName))
                .filter(s -> !Strings.isNullOrEmpty(s))
                .map(Double::valueOf)
                .map(value -> assertPositive(value, parameterName));
        } catch (NumberFormatException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .cause(e)
                .message("Can not parse " + parameterName)
                .haltError();
        }
    }

    private static Number assertPositive(Number value, String parameterName) {
        if (value.doubleValue() < 0) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(parameterName + " can not be negative")
                .haltError();
        }
        return value;
    }

    private static int assertNotZero(int value, String parameterName) {
        if (value == 0) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(parameterName + " can not be equal to zero")
                .haltError();
        }
        return value;
    }
}
