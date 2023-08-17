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

public class QuotaComponent {

    public static final QuotaComponent MAILBOX = of("mailbox");
    public static final QuotaComponent SIEVE = of("sieve");
    public static final QuotaComponent JMAP_UPLOADS = of("jmapUploads");

    public static QuotaComponent of(String value) {
        Preconditions.checkArgument(StringUtils.isNotBlank(value), "`value` is mandatory");
        return new QuotaComponent(value);
    }

    private final String value;

    private QuotaComponent(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaComponent) {
            QuotaComponent other = (QuotaComponent) o;
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
