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

package org.apache.james.jmap.http;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class AuthenticationChallenge {
    public static AuthenticationChallenge of(AuthenticationScheme scheme, Map<String, String> parameters) {
        Preconditions.checkNotNull(scheme);
        Preconditions.checkNotNull(parameters);

        return new AuthenticationChallenge(scheme, ImmutableMap.copyOf(parameters));
    }

    private final AuthenticationScheme scheme;
    private final ImmutableMap<String, String> parameters;

    public AuthenticationChallenge(AuthenticationScheme scheme, ImmutableMap<String, String> parameters) {
        this.scheme = scheme;
        this.parameters = parameters;
    }

    public String asString() {
        return String.format("%s %s",
            scheme.asString(),
            parameters.entrySet().stream()
                .map(entry -> String.format("%s=\"%s\"", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", ")));
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof AuthenticationChallenge) {
            AuthenticationChallenge that = (AuthenticationChallenge) o;

            return Objects.equals(this.scheme, that.scheme)
                && Objects.equals(this.parameters, that.parameters);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(scheme, parameters);
    }
}
