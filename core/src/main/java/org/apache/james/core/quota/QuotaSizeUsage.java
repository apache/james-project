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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class QuotaSizeUsage implements QuotaUsageValue<QuotaSizeUsage, QuotaSizeLimit> {

    public static final QuotaSizeUsage ZERO = new QuotaSizeUsage(0L);
    private static final Logger LOGGER = LoggerFactory.getLogger(QuotaSizeUsage.class);

    public static class Sanitized extends QuotaSizeUsage {
        private static Sanitized of(long value) {
            Preconditions.checkArgument(value >= 0, "Sanitized quota shall be positive");
            return new Sanitized(value);
        }

        private Sanitized(Long value) {
            super(value);
        }
    }

    public static QuotaSizeUsage size(long value) {
        return new QuotaSizeUsage(value);
    }

    private final long value;

    private QuotaSizeUsage(long value) {
        this.value = value;
    }

    @Override
    public long asLong() {
        return value;
    }

    @Override
    public QuotaSizeUsage add(long additionalValue) {
        return new QuotaSizeUsage(value + additionalValue);
    }

    @Override
    public QuotaSizeUsage add(QuotaSizeUsage additionalValue) {
        return new QuotaSizeUsage(value + additionalValue.asLong());
    }

    @Override
    public boolean exceedLimit(QuotaSizeLimit limit) {
        if (limit.isLimited()) {
            return value > limit.asLong();
        } else {
            return false;
        }
    }

    public boolean isValid() {
        return value >= 0;
    }

    public Sanitized sanitize() {
        if (!isValid()) {
            LOGGER.warn("Invalid quota count usage : {}", value);
        }

        return Sanitized.of(Math.max(value, 0));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", value)
            .toString();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaSizeUsage) {
            QuotaSizeUsage that = (QuotaSizeUsage) o;
            return Objects.equal(this.value, that.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(value);
    }
}
