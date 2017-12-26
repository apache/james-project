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

package org.apache.james.transport.mailets.remote.delivery;

import java.util.Optional;

import com.google.common.base.Objects;

public class ExecutionResult {

    public enum ExecutionState {
        SUCCESS,
        PERMANENT_FAILURE,
        TEMPORARY_FAILURE
    }

    public static ExecutionResult success() {
        return new ExecutionResult(ExecutionState.SUCCESS, Optional.empty());
    }

    public static ExecutionResult temporaryFailure(Exception e) {
        return new ExecutionResult(ExecutionState.TEMPORARY_FAILURE, Optional.of(e));
    }

    public static ExecutionResult permanentFailure(Exception e) {
        return new ExecutionResult(ExecutionState.PERMANENT_FAILURE, Optional.of(e));
    }

    public static ExecutionResult temporaryFailure() {
        return new ExecutionResult(ExecutionState.TEMPORARY_FAILURE, Optional.empty());
    }

    public static ExecutionResult onFailure(boolean permanent, Exception exeption) {
        if (permanent) {
            return permanentFailure(exeption);
        } else {
            return temporaryFailure(exeption);
        }
    }

    private final ExecutionState executionState;
    private final Optional<Exception> exception;

    public ExecutionResult(ExecutionState executionState, Optional<Exception> exception) {
        this.executionState = executionState;
        this.exception = exception;
    }

    public ExecutionState getExecutionState() {
        return executionState;
    }

    public Optional<Exception> getException() {
        return exception;
    }

    public boolean isPermanent() {
        return executionState == ExecutionState.PERMANENT_FAILURE;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ExecutionResult) {
            ExecutionResult that = (ExecutionResult) o;
            return Objects.equal(this.executionState, that.executionState)
                && Objects.equal(this.exception, that.exception);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(executionState, exception);
    }
}

