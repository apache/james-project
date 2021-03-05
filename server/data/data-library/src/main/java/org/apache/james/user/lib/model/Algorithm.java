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

import java.util.Objects;

public class Algorithm {
    private static final boolean LEGACY = true;
    public static final String LEGACY_SUFFIX = "-legacy";

    public static Algorithm of(String rawValue) {
        if (rawValue.endsWith(LEGACY_SUFFIX)) {
            return new Algorithm(rawValue, rawValue.substring(0, rawValue.length() - LEGACY_SUFFIX.length()), LEGACY);
        }
        return new Algorithm(rawValue, rawValue, false);
    }

    private final String rawValue;
    private final String algorithmName;
    private final boolean legacy;

    private Algorithm(String rawValue, String algorithmName, boolean legacy) {
        this.rawValue = rawValue;
        this.algorithmName = algorithmName;
        this.legacy = legacy;
    }

    public String algorithmName() {
        return algorithmName;
    }

    public String rawValue() {
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
                && Objects.equals(this.algorithmName, that.algorithmName)
                && Objects.equals(this.legacy, that.legacy);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(rawValue, algorithmName, legacy);
    }
}
