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

package org.apache.james.core.quota;

import java.util.Objects;

public class QuotaCurrentValue {

    private final String identifier;
    private final QuotaComponent quotaComponent;
    private final QuotaType quotaType;
    private final Long currentValue;

    public QuotaCurrentValue(String identifier, QuotaComponent quotaComponent, QuotaType quotaType, Long currentValue) {
        this.identifier = identifier;
        this.quotaComponent = quotaComponent;
        this.quotaType = quotaType;
        this.currentValue = currentValue;
    }

    public static QuotaCurrentValue of(String identifier, QuotaComponent component, QuotaType quotaType, Long currentValue) {
        return new QuotaCurrentValue(identifier, component, quotaType, currentValue);
    }

    public String getIdentifier() {
        return identifier;
    }

    public QuotaComponent getQuotaComponent() {
        return quotaComponent;
    }

    public QuotaType getQuotaType() {
        return quotaType;
    }

    public Long getCurrentValue() {
        return currentValue;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(identifier, quotaComponent, quotaType, currentValue);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaCurrentValue) {
            QuotaCurrentValue other = (QuotaCurrentValue) o;
            return Objects.equals(identifier, other.identifier)
                && Objects.equals(quotaComponent, other.quotaComponent)
                && Objects.equals(quotaType, other.quotaType)
                && Objects.equals(currentValue, other.currentValue);
        }
        return false;
    }
}
