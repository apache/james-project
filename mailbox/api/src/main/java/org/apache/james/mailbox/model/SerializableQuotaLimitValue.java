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

import com.google.common.base.MoreObjects;

public class SerializableQuotaLimitValue<T extends QuotaLimitValue<T>> implements Serializable {

    public static <U extends QuotaLimitValue<U>> SerializableQuotaLimitValue<U> valueOf(Optional<U> input) {
        return new SerializableQuotaLimitValue<>(input.orElse(null));
    }

    public static final long UNLIMITED = -1;

    private static <U extends QuotaLimitValue<U>> Long encodeAsLong(U quota) {
        if (quota.isLimited()) {
            return quota.asLong();
        }
        return UNLIMITED;
    }

    private final Long value;

    public SerializableQuotaLimitValue(T value) {
        this(encodeAsLong(value));
    }

    SerializableQuotaLimitValue(Long value) {
        this.value = value;
    }

    public Long encodeAsLong() {
        return value;
    }

    public Optional<T> toValue(Function<Long, T> factory, T unlimited) {
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
        if (o instanceof SerializableQuotaLimitValue<?>) {
            SerializableQuotaLimitValue<?> that = (SerializableQuotaLimitValue<?>) o;
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
