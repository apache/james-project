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

package org.apache.james.backends.rabbitmq;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.core.healthcheck.ResultStatus;

final class MergedResults {
    private static final String CAUSE_SEPARATOR = "; ";

    static Result merge(ComponentName componentName, List<Result> results) {
        ResultStatus status = results.stream()
            .map(Result::getStatus)
            .reduce(ResultStatus.HEALTHY, ResultStatus::merge);
        String cause = results.stream()
            .map(Result::getCause)
            .flatMap(Optional::stream)
            .collect(Collectors.joining(CAUSE_SEPARATOR));

        return switch (status) {
            case HEALTHY -> Result.healthy(componentName);
            case DEGRADED -> Result.degraded(componentName, cause);
            case UNHEALTHY -> mergedError(results)
                .map(error -> Result.unhealthy(componentName, cause, error))
                .orElseGet(() -> Result.unhealthy(componentName, cause));
        };
    }

    private static Optional<Throwable> mergedError(List<Result> results) {
        List<Throwable> errors = results.stream()
            .map(Result::getError)
            .flatMap(Optional::stream)
            .toList();
        if (errors.size() <= 1) {
            return errors.stream().findFirst();
        }
        RuntimeException mergedError = new RuntimeException(errors.size() + " errors, see the suppressed exceptions");
        errors.forEach(mergedError::addSuppressed);
        return Optional.of(mergedError);
    }

    private MergedResults() {
    }
}
