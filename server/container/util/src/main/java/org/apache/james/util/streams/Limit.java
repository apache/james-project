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

package org.apache.james.util.streams;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;

public class Limit {

    public static Limit from(int limit) {
        if (limit > 0) {
            return new Limit(Optional.of(limit));
        } else {
            return unlimited();
        }
    }

    public static Limit from(Optional<Integer> limit) {
        return limit.map(Limit::from)
            .orElse(unlimited());
    }

    public static Limit unlimited() {
        return new Limit(Optional.empty());
    }

    public static Limit limit(int limit) {
        Preconditions.checkArgument(limit > 0, "limit should be positive");
        return new Limit(Optional.of(limit));
    }

    private final Optional<Integer> limit;

    private Limit(Optional<Integer> limit) {
        this.limit = limit;
    }

    public Optional<Integer> getLimit() {
        return limit;
    }

    public boolean isUnlimited() {
        return !limit.isPresent();
    }

    public <T> Stream<T> applyOnStream(Stream<T> stream) {
        return limit
            .map(stream::limit)
            .orElse(stream);
    }

    public <T> Flux<T> applyOnFlux(Flux<T> flux) {
        return limit
            .map(flux::take)
            .orElse(flux);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Limit) {
            Limit other = (Limit) o;
            return Objects.equals(limit, other.limit);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(limit);
    }
}
