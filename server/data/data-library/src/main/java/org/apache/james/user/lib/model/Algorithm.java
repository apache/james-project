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
import java.util.Objects;

import com.google.common.collect.ImmutableList;

public class Algorithm {

    public enum HashingMode {
        LEGACY(LEGACY_FACTORY),
        DEFAULT(DEFAULT_FACTORY);

        public static HashingMode parse(String value) {
            return Arrays.stream(values())
                .filter(aValue -> value.equalsIgnoreCase(aValue.toString()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsuported value for HashingMode: " + value + ". Should be one of " + ImmutableList.copyOf(values())));
        }

        private final Factory factory;

        HashingMode(Factory factory) {
            this.factory = factory;
        }

        public Factory getFactory() {
            return factory;
        }
    }

    public interface Factory {
        Algorithm of(String rawValue);
    }

    public static class LegacyFactory implements Factory {
        @Override
        public Algorithm of(String rawValue) {
            return new Algorithm(rawValue, LEGACY);
        }
    }

    public static class DefaultFactory implements Factory {
        @Override
        public Algorithm of(String rawValue) {
            return new Algorithm(rawValue, !LEGACY);
        }
    }

    private static final boolean LEGACY = true;
    public static final Factory LEGACY_FACTORY = new LegacyFactory();
    public static final Factory DEFAULT_FACTORY = new DefaultFactory();

    private final String rawValue;
    private final boolean legacy;

    private Algorithm(String rawValue, boolean legacy) {
        this.rawValue = rawValue;
        this.legacy = legacy;
    }

    public String asString() {
        return rawValue;
    }

    public boolean isLegacy() {
        return legacy;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Algorithm) {
            Algorithm that = (Algorithm) o;

            return Objects.equals(this.rawValue, that.rawValue)
                && Objects.equals(this.legacy, that.legacy);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(rawValue, legacy);
    }
}
