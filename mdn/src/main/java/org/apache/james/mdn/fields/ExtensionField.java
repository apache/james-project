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

package org.apache.james.mdn.fields;

import java.util.Objects;

import com.google.common.base.Preconditions;

/**
 * Implements extension fields allowed by RFC-8098
 *
 * https://tools.ietf.org/html/rfc8098#section-3.3
 */
public class ExtensionField implements Field {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String fieldName;
        private String rawValue;

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder rawValue(String rawValue) {
            this.rawValue = rawValue;
            return this;
        }

        public ExtensionField build() {
            Preconditions.checkNotNull(fieldName);
            Preconditions.checkNotNull(rawValue);
            Preconditions.checkState(!fieldName.contains("\n"), "Field name can not be multiline");

            return new ExtensionField(fieldName, rawValue);
        }
    }

    private final String fieldName;
    private final String rawValue;

    private ExtensionField(String fieldName, String rawValue) {
        this.fieldName = fieldName;
        this.rawValue = rawValue;
    }

    @Override
    public String formattedValue() {
        return fieldName + ": " + rawValue;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ExtensionField) {
            ExtensionField that = (ExtensionField) o;

            return Objects.equals(fieldName, that.fieldName)
                && Objects.equals(rawValue, that.rawValue);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(fieldName, rawValue);
    }

    @Override
    public String toString() {
        return formattedValue();
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getRawValue() {
        return rawValue;
    }
}
