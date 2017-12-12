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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.math.LongMath;
import com.google.common.primitives.Ints;

public class Number {

    public static final Logger LOGGER = LoggerFactory.getLogger(Number.class);

    public static Number fromInt(int value) {
        Preconditions.checkState(value >= ZERO_VALUE,
            "value should be positive and less than 2^31 or empty");
        return new Number(value);
    }

    public static Number fromLong(long value) {
        Preconditions.checkState(value >= ZERO_VALUE && value <= MAX_VALUE,
            "value should be positive and less than 2^53 or empty");
        return new Number(value);
    }

    public static Number fromOutboundInt(int value) {
        if (value < ZERO_VALUE) {
            LOGGER.warn("Received a negative Number");
            return new Number(ZERO_VALUE);
        }
        return new Number(value);
    }

    public static Number fromOutboundLong(long value) {
        if (value < ZERO_VALUE) {
            LOGGER.warn("Received a negative Number");
            return new Number(ZERO_VALUE);
        }
        if (value > MAX_VALUE) {
            LOGGER.warn("Received a too big Number");
            return new Number(MAX_VALUE);
        }
        return new Number(value);
    }

    private static final int ZERO_VALUE = 0;
    public static final long MAX_VALUE = LongMath.pow(2, 53);
    public static final Number ZERO = new Number(ZERO_VALUE);

    private final long value;

    private Number(long value) {
        this.value = value;
    }

    public Number ensureLessThan(long maxValue) {
        Preconditions.checkState(value >= ZERO_VALUE && value <= maxValue,
            "value should be positive and less than " + maxValue + " or empty");
        return new Number(value);
    }

    @JsonValue
    public long asLong() {
        return value;
    }

    @JsonIgnore
    public int asInt() {
        return Ints.checkedCast(value);
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
