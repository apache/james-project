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

package org.apache.james.mailbox.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.core.quota.QuotaLimitValue;
import org.apache.james.core.quota.QuotaUsageValue;

import com.google.common.base.MoreObjects;

public class SerializableQuotaUsageValue<T extends QuotaLimitValue<T>, U extends QuotaUsageValue<U, T>> implements Serializable {

    public static <T extends QuotaLimitValue<T>, U extends QuotaUsageValue<U, T>> SerializableQuotaUsageValue<T, U> valueOf(Optional<U> input) {
        return new SerializableQuotaUsageValue<T, U>(input.orElse(null));
    }

    public static final long UNLIMITED = -1;

    private static <T extends QuotaLimitValue<T>, U extends QuotaUsageValue<U, T>> Long encodeAsLong(U quota) {
        if (quota.isLimited()) {
            return quota.asLong();
        }
        return UNLIMITED;
    }

    private final Long value;

    public SerializableQuotaUsageValue(U value) {
        this(encodeAsLong(value));
    }

    SerializableQuotaUsageValue(Long value) {
        this.value = value;
    }

    public Long encodeAsLong() {
        return value;
    }

    public Optional<U> toValue(Function<Long, U> factory, U unlimited) {
        Long longValue = encodeAsLong();
        if (longValue == null) {
            return Optional.empty();
        }
        if (longValue == UNLIMITED) {
            return Optional.of(unlimited);
        }
        return Optional.of(factory.apply(longValue));
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SerializableQuotaUsageValue<?, ?>) {
            SerializableQuotaUsageValue<?, ?> that = (SerializableQuotaUsageValue<?,?>) o;
            return Objects.equals(value, that.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", value)
            .toString();
    }
}
