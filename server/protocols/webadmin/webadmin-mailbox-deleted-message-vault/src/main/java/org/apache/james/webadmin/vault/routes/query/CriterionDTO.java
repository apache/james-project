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

package org.apache.james.webadmin.vault.routes.query;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

@JsonDeserialize(as = CriterionDTO.class)
public class CriterionDTO implements QueryElement {

    @VisibleForTesting
    static CriterionDTO from(QueryTranslator.FieldName fieldName, QueryTranslator.Operator operator, String value) {
        return new CriterionDTO(fieldName.getValue(), operator.getValue(), value);
    }

    private final String fieldName;
    private final String operator;
    private final String value;

    @JsonCreator
    public CriterionDTO(@JsonProperty("fieldName") String fieldName,
                        @JsonProperty("operator") String operator,
                        @JsonProperty("value") String value) {
        this.fieldName = fieldName;
        this.operator = operator;
        this.value = value;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOperator() {
        return operator;
    }

    public String getValue() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CriterionDTO) {
            CriterionDTO that = (CriterionDTO) o;

            return Objects.equals(this.fieldName, that.getFieldName())
                && Objects.equals(this.operator, that.getOperator())
                && Objects.equals(this.value, that.getValue());
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(fieldName, operator, value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("fieldName", fieldName)
            .add("operator", operator)
            .add("value", value)
            .toString();
    }
}