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

package org.apache.james.jmap.draft.methods;

import java.util.Objects;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

public class ValidationResult {

    public static final String UNDEFINED_PROPERTY = "__UNDEFINED__";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String property;
        private String errorMessage;

        public Builder property(String property) {
            this.property = property;
            return this;
        }

        public Builder message(String message) {
            this.errorMessage = message;
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(property, errorMessage);
        }

    }

    private final String property;
    private final String errorMessage;

    @VisibleForTesting
    ValidationResult(String property, String errorMessage) {
        this.property = property;
        this.errorMessage = errorMessage;
    }

    public String getProperty() {
        return property;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ValidationResult) {
            ValidationResult otherEMailer = (ValidationResult) o;
            return Objects.equals(property, otherEMailer.property)
                    && Objects.equals(errorMessage, otherEMailer.errorMessage);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(property, errorMessage);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("property", property)
                .add("errorMessage", errorMessage)
                .toString();
    }
}
