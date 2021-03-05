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
    public static Algorithm of(String rawValue) {
        return new Algorithm(rawValue);
    }

    private final String rawValue;

    public Algorithm(String rawValue) {
        this.rawValue = rawValue;
    }

    public String algorithmName() {
        return rawValue;
    }

    public String rawValue() {
        return rawValue;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Algorithm) {
            Algorithm that = (Algorithm) o;

            return Objects.equals(this.rawValue, that.rawValue);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(rawValue);
    }
}
