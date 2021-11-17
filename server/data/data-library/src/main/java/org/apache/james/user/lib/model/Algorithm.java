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

package org.apache.james.user.lib.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class Algorithm {

    public enum HashingMode {
        PLAIN,
        SALTED,
        LEGACY,
        LEGACY_SALTED;

        public static HashingMode parse(String value) {
            return Arrays.stream(values())
                .filter(aValue -> value.equalsIgnoreCase(aValue.toString()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported value for HashingMode: " + value + ". Should be one of " + ImmutableList.copyOf(values())));
        }
    }

    public static Algorithm of(String algorithmName) {
        return Algorithm.of(algorithmName, HashingMode.PLAIN);
    }

    public static Algorithm of(String algorithmName, String fallbackHashingMode) {
        return Algorithm.of(algorithmName, HashingMode.parse(fallbackHashingMode));
    }

    public static Algorithm of(String algorithmName, HashingMode fallbackHashingMode) {
        List<String> spec = Splitter.on('/').splitToList(algorithmName);
        if (spec.size() == 1) {
            return new Algorithm(algorithmName, fallbackHashingMode);
        } else {
            return new Algorithm(spec.get(0), HashingMode.parse(spec.get(1)));
        }
    }

    private final String rawValue;
    private final HashingMode hashingMode;

    private Algorithm(String rawValue, HashingMode hashingMode) {
        this.rawValue = rawValue;
        this.hashingMode = hashingMode;
    }

    public String asString() {
        return rawValue + "/" + hashingMode.name().toLowerCase();
    }

    public String getName() {
        return rawValue;
    }

    public String getHashingMode() {
        return hashingMode.name();
    }

    public boolean isLegacy() {
        return hashingMode == HashingMode.LEGACY || hashingMode == HashingMode.LEGACY_SALTED;
    }

    public boolean isSalted() {
        return hashingMode == HashingMode.SALTED || hashingMode == HashingMode.LEGACY_SALTED;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Algorithm) {
            Algorithm that = (Algorithm) o;

            return Objects.equals(this.rawValue, that.rawValue)
                && Objects.equals(this.hashingMode, that.hashingMode);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(rawValue, hashingMode);
    }
}
