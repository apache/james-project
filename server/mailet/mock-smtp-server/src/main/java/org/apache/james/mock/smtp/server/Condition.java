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

package org.apache.james.mock.smtp.server;

import java.util.Objects;

import com.google.common.base.Preconditions;

class Condition {
    private final Operator operator;
    private final String matchingValue;

    Condition(Operator operator, String matchingValue) {
        Preconditions.checkNotNull(operator);
        Preconditions.checkNotNull(matchingValue);

        this.operator = operator;
        this.matchingValue = matchingValue;
    }

    boolean matches(String line) {
        return operator.matches(line, matchingValue);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Condition) {
            Condition condition = (Condition) o;

            return Objects.equals(this.operator, condition.operator)
                && Objects.equals(this.matchingValue, condition.matchingValue);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(operator, matchingValue);
    }
}
