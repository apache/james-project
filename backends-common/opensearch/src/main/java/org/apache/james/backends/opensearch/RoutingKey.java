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

package org.apache.james.backends.opensearch;

import java.util.Objects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class RoutingKey {
    public interface Factory<T> {
        RoutingKey from(T t);
    }

    public static RoutingKey fromString(String value) {
        return new RoutingKey(value);
    }


    private final String value;

    private RoutingKey(String value) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(value), "RoutingKey must be specified");
        this.value = value;
    }

    public String asString() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RoutingKey) {
            RoutingKey that = (RoutingKey) o;

            return Objects.equals(this.value, that.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value);
    }
}
