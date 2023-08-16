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

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class QuotaScope {

    public static final QuotaScope GLOBAL = of("global");
    public static final QuotaScope DOMAIN = of("domain");
    public static final QuotaScope USER = of("user");

    public static QuotaScope of(String value) {
        Preconditions.checkArgument(StringUtils.isNotBlank(value), "`value` is mandatory");
        return new QuotaScope(value);
    }

    private final String value;

    private QuotaScope(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof QuotaScope) {
            QuotaScope other = (QuotaScope) o;
            return Objects.equals(value, other.value);
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", value)
            .toString();
    }
}
