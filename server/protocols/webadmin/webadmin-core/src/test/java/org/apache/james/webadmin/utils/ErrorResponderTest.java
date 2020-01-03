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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import spark.HaltException;

class ErrorResponderTest {
    static final Optional<String> NO_CAUSE = Optional.empty();

    @Test
    void haltErrorShouldThrowWhenNoStatusCode() {
        assertThatThrownBy(() -> ErrorResponder.builder()
            .haltError())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void haltErrorShouldThrowWhenNoType() {
        assertThatThrownBy(() -> ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .haltError())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void haltErrorShouldThrowWhenNoMessage() {
        assertThatThrownBy(() -> ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .haltError())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void haltErrorShouldReturnBodyWithStatusCodeWhenSetting() {
        assertThatThrownBy(() -> ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Error")
                .haltError())
            .isInstanceOf(HaltException.class)
            .matches(e -> hasStatus(e, HttpStatus.NOT_FOUND_404))
            .matches(e -> bodyHasErrorDetail(e, new ErrorResponder.ErrorDetail(HttpStatus.NOT_FOUND_404, "InvalidArgument", "Error", NO_CAUSE)));
    }

    @Test
    void haltErrorShouldReturnBodyWithErrorTypeWhenSetting() {
        assertThatThrownBy(() -> ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.WRONG_STATE)
                .message("Error")
                .haltError())
            .isInstanceOf(HaltException.class)
            .matches(e -> hasStatus(e, HttpStatus.BAD_REQUEST_400))
            .matches(e -> bodyHasErrorDetail(e, new ErrorResponder.ErrorDetail(HttpStatus.BAD_REQUEST_400, "WrongState", "Error", NO_CAUSE)));
    }

    @Test
    void haltErrorShouldReturnBodyWithErrorMessageWhenSetting() {
        assertThatThrownBy(() -> ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("It has error")
                .haltError())
            .isInstanceOf(HaltException.class)
            .matches(e -> hasStatus(e, HttpStatus.BAD_REQUEST_400))
            .matches(e -> bodyHasErrorDetail(e, new ErrorResponder.ErrorDetail(HttpStatus.BAD_REQUEST_400, "InvalidArgument", "It has error", NO_CAUSE)));
    }

    @Test
    void haltErrorShouldReturnBodyWithCauseTypeWhenSetting() {
        assertThatThrownBy(() -> ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Error")
                .cause(new IllegalArgumentException("The input data is invalid"))
                .haltError())
            .isInstanceOf(HaltException.class)
            .matches(e -> hasStatus(e, HttpStatus.BAD_REQUEST_400))
            .matches(e -> bodyHasErrorDetail(e, new ErrorResponder.ErrorDetail(HttpStatus.BAD_REQUEST_400, "InvalidArgument", "Error", Optional.of("The input data is invalid"))));
    }

    @Test
    void haltErrorShouldReturnBodyWithErrorDetail() {
        assertThatThrownBy(() -> ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Error")
                .cause(new IllegalArgumentException("The input data is invalid"))
            .haltError())
            .isInstanceOf(HaltException.class)
            .matches(e -> hasStatus(e, HttpStatus.BAD_REQUEST_400))
            .matches(e -> bodyHasErrorDetail(e, new ErrorResponder.ErrorDetail(HttpStatus.BAD_REQUEST_400, "InvalidArgument", "Error", Optional.of("The input data is invalid"))));
    }

    @Test
    void haltShouldFormatMessage() {
        assertThatThrownBy(() -> ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Error %s", "bip")
                .cause(new IllegalArgumentException("The input data is invalid"))
            .haltError())
            .isInstanceOf(HaltException.class)
            .matches(e -> hasStatus(e, HttpStatus.BAD_REQUEST_400))
            .matches(e -> bodyHasErrorDetail(e, new ErrorResponder.ErrorDetail(HttpStatus.BAD_REQUEST_400, "InvalidArgument", "Error bip", Optional.of("The input data is invalid"))));
    }

    private boolean hasStatus(Throwable throwable, int status) {
        HaltException haltException = (HaltException) throwable;
        return Objects.equals(haltException.statusCode(), status);
    }

    private boolean bodyHasErrorDetail(Throwable throwable, ErrorResponder.ErrorDetail errorDetail) {
        HaltException haltException = (HaltException) throwable;
        DocumentContext jsonPath = JsonPath.parse(haltException.body());
        return errorDetail.equals(new ErrorResponder.ErrorDetail(
            jsonPath.read("$.statusCode"),
            jsonPath.read("$.type"),
            jsonPath.read("$.message"),
            Optional.ofNullable(jsonPath.read("$.details"))));
    }

}