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

import java.util.Optional;

public class Result {

    public static Result healthy(ComponentName componentName) {
        return new Result(componentName, ResultStatus.HEALTHY, Optional.empty());
    }

    public static Result unhealthy(ComponentName componentName, String cause) {
        return new Result(componentName, ResultStatus.UNHEALTHY, Optional.of(cause));
    }

    public static Result unhealthy(ComponentName componentName) {
        return new Result(componentName, ResultStatus.UNHEALTHY, Optional.empty());
    }

    public static Result degraded(ComponentName componentName, String cause) {
        return new Result(componentName, ResultStatus.DEGRADED, Optional.of(cause));
    }

    public static Result degraded(ComponentName componentName) {
        return new Result(componentName, ResultStatus.DEGRADED, Optional.empty());
    }

    private final ComponentName componentName;
    private final ResultStatus status;
    private final Optional<String> cause;

    private Result(ComponentName componentName, ResultStatus status, Optional<String> cause) {
        this.componentName = componentName;
        this.status = status;
        this.cause = cause;
    }

    public ComponentName getComponentName() {
        return componentName;
    }

    public ResultStatus getStatus() {
        return status;
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

    public Optional<String> getCause() {
        return cause;
    }
}