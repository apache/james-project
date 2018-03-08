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

package org.apache.james.mailbox.store.mail.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaValue;

import com.google.common.base.MoreObjects;

public class SerializableQuota<T extends QuotaValue<T>> implements Serializable {

    public static final long UNLIMITED = -1;

    public static <U extends QuotaValue<U>> SerializableQuota<U> newInstance(Quota<U> quota) {
        return newInstance(quota.getUsed(), quota.getLimit());
    }

    public static <U extends QuotaValue<U>> SerializableQuota<U> newInstance(U used, U max) {
        return new SerializableQuota<>(
            new SerializableQuotaValue<>(used),
            new SerializableQuotaValue<>(max)
        );
    }

    private static <U extends QuotaValue<U>> SerializableQuotaValue<U> getUsed(Optional<U> quota, Function<U, SerializableQuotaValue<U>> factory) {
        return quota.map(factory).orElse(null);
    }

    private final SerializableQuotaValue<T> max;
    private final SerializableQuotaValue<T> used;

    private SerializableQuota(SerializableQuotaValue<T> used, SerializableQuotaValue<T> max) {
        this.max = max;
        this.used = used;
    }

    public Long encodeAsLong() {
        return max.encodeAsLong();
    }

    public Long getUsed() {
        return Optional.ofNullable(used).map(SerializableQuotaValue::encodeAsLong).orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SerializableQuota<?>) {
            SerializableQuota<?> that = (SerializableQuota<?>) o;
            return Objects.equals(max, that.max) &&
                Objects.equals(used, that.used);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(max, used);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("max", max)
            .add("used", used)
            .toString();
    }
}
