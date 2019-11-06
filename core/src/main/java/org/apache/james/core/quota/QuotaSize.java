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

import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class QuotaSize implements QuotaValue<QuotaSize> {

    public static final QuotaSize QUOTA_SIZE = new QuotaSize(Optional.empty());

    public static QuotaSize unlimited() {
        return QUOTA_SIZE;
    }

    public static QuotaSize size(long value) {
        return size(Optional.of(value));
    }

    public static QuotaSize size(Optional<Long> value) {
        return new QuotaSize(value);
    }

    private final Optional<Long> value;

    private QuotaSize(Optional<Long> value) {
        Preconditions.checkArgument(QuotaValue.isValidValue(value), "Quota limit should be positive");
        this.value = value;
    }

    @Override
    public long asLong() {
        return value.orElseThrow(IllegalStateException::new);
    }

    @Override
    public boolean isLimited() {
        return value.isPresent();
    }

    @Override
    public QuotaSize add(long additionalValue) {
        return new QuotaSize(value.map(x -> x + additionalValue));
    }

    @Override
    public QuotaSize add(QuotaSize additionalValue) {
        if (additionalValue.isUnlimited()) {
            return unlimited();
        }
        return new QuotaSize(value.map(x -> x + additionalValue.asLong()));
    }

    @Override
    public boolean isGreaterThan(QuotaSize other) {
        return value.orElse(Long.MAX_VALUE) > other.value.orElse(Long.MAX_VALUE);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", value.map(String::valueOf).orElse("unlimited"))
            .toString();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaSize) {
            QuotaSize that = (QuotaSize) o;
            return Objects.equal(this.value, that.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(value);
    }
}
