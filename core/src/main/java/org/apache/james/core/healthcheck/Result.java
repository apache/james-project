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
package org.apache.james.core.healthcheck;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;

public class Result {

    public static Result healthy(ComponentName componentName) {
        return Builder.builder().componentName(componentName).status(ResultStatus.HEALTHY).build();
    }

    public static Result unhealthy(ComponentName componentName, String cause) {
        return Builder.builder().componentName(componentName).status(ResultStatus.UNHEALTHY).cause(cause).build();
    }

    public static Result unhealthy(ComponentName componentName, String cause, Throwable error) {
        return Builder.builder().componentName(componentName).status(ResultStatus.UNHEALTHY).cause(cause).error(error).build();
    }

    public static Result degraded(ComponentName componentName, String cause) {
        return Builder.builder().componentName(componentName).status(ResultStatus.DEGRADED).cause(cause).build();
    }

    public static class Builder {
        private ComponentName componentName;
        private ResultStatus status;
        private String cause;
        private Optional<Throwable> error = Optional.empty();

        public Builder componentName(ComponentName componentName) {
            this.componentName = componentName;
            return this;
        }

        public Builder status(ResultStatus status) {
            this.status = status;
            return this;
        }

        public Builder cause(String cause) {
            this.cause = cause;
            return this;
        }

        public Builder error(Throwable error) {
            this.error = Optional.of(error);
            return this;
        }

        public Result build() {
            return new Result(componentName, status, cause, error);
        }

        public static Builder builder() {
            return new Builder();
        }
    }

    private final ComponentName componentName;
    private final ResultStatus status;
    private final String cause;
    private final Optional<Throwable> error;

    public Result(ComponentName componentName, ResultStatus status, String cause, Optional<Throwable> error) {
        this.componentName = componentName;
        this.status = status;
        this.cause = cause;
        this.error = error;
    }

    public ComponentName getComponentName() {
        return componentName;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public String getCause() {
        return cause;
    }

    public Optional<Throwable> getError() {
        return error;
    }

    public boolean isHealthy() {
        return status == ResultStatus.HEALTHY;
    }

    public boolean isDegraded() {
        return status == ResultStatus.DEGRADED;
    }

    public boolean isUnHealthy() {
        return status == ResultStatus.UNHEALTHY;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Result) {
            Result result = (Result) o;

            return Objects.equals(this.componentName, result.componentName)
                && Objects.equals(this.status, result.status)
                && Objects.equals(this.error, result.error);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(componentName, status, error);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("componentName", componentName)
            .add("status", status)
            .add("error", error)
            .toString();
    }
}