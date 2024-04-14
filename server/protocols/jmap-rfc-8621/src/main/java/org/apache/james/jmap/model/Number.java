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

package org.apache.james.jmap.model;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.math.LongMath;

public class Number {

    public static final Logger LOGGER = LoggerFactory.getLogger(Number.class);
    public static final String VALIDATION_MESSAGE = "value should be positive and less than 2^53";

    public interface Factory<T> {
        T from(long value);
    }


    private static final int ZERO_VALUE = 0;
    public static final long MAX_VALUE = LongMath.pow(2, 53);
    public static final Number ZERO = new Number(ZERO_VALUE);

    public static final Factory<Optional<Number>> DEFAULT_FACTORY = value -> Optional.of(value)
        .filter(Number::isValid)
        .map(Number::new);

    public static final Factory<Number> BOUND_SANITIZING_FACTORY = value -> {
        if (value < ZERO_VALUE) {
            LOGGER.warn("Received a negative Number");
            return new Number(ZERO_VALUE);
        }
        if (value > MAX_VALUE) {
            LOGGER.warn("Received a too big Number");
            return new Number(MAX_VALUE);
        }
        return new Number(value);
    };

    public static Number fromLong(long value) {
        return new Number(value);
    }

    private static boolean isValid(long value) {
        return value >= ZERO_VALUE && value <= MAX_VALUE;
    }

    private final long value;

    private Number(long value) {
        Preconditions.checkArgument(isValid(value), VALIDATION_MESSAGE);
        this.value = value;
    }

    @JsonValue
    public long asLong() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Number) {
            Number number = (Number) o;

            return Objects.equals(this.value, number.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", value)
            .toString();
    }
}
