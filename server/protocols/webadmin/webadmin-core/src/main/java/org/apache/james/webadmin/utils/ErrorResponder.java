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

import static spark.Spark.halt;

import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import spark.HaltException;

public class ErrorResponder {
    public enum ErrorType {
        INVALID_ARGUMENT("InvalidArgument"),
        NOT_FOUND("notFound"),
        WRONG_STATE("WrongState"),
        SERVER_ERROR("ServerError");

        private final String type;

        ErrorType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    private Integer statusCode;
    private ErrorType type;
    private String message;
    private Optional<Exception> cause;

    public ErrorResponder() {
        cause = Optional.empty();
    }

    public static ErrorResponder builder() {
        return new ErrorResponder();
    }

    public ErrorResponder statusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public ErrorResponder type(ErrorType type) {
        this.type = type;
        return this;
    }

    public ErrorResponder message(String message) {
        this.message = message;
        return this;
    }

    public ErrorResponder cause(Exception cause) {
        this.cause = Optional.of(cause);
        return this;
    }

    public HaltException haltError() {
        Preconditions.checkNotNull(statusCode, "statusCode must not be null in case of error");
        Preconditions.checkNotNull(type, "type must not be null in case of error");
        Preconditions.checkNotNull(message, "message must not be null in case of error");
        try {
            return halt(statusCode, new JsonTransformer().render(new ErrorDetail(statusCode,
                type.getType(),
                message,
                cause.map(e -> Optional.ofNullable(e.getMessage())).orElse(Optional.empty()))));
        } catch (JsonProcessingException e) {
            return halt(statusCode);
        }
    }

    static class ErrorDetail {
        private final int statusCode;
        private final String type;
        private final String message;
        private final Optional<String> cause;

        @VisibleForTesting
        ErrorDetail(int statusCode, String type, String message, Optional<String> cause) {
            this.statusCode = statusCode;
            this.type = type;
            this.message = message;
            this.cause = cause;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public Optional<String> getCause() {
            return cause;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof ErrorDetail) {
                ErrorDetail that = (ErrorDetail) o;

                return Objects.equals(this.statusCode, that.statusCode)
                    && Objects.equals(this.type, that.type)
                    && Objects.equals(this.message, that.message)
                    && Objects.equals(this.cause, that.cause);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(statusCode, type, message, cause);
        }
    }
}
