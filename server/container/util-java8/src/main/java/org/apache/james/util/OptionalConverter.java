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
package org.apache.james.util;

import java.util.Optional;
import java.util.stream.Stream;

public class OptionalConverter {

    @FunctionalInterface
    public interface Operation {
        void perform();
    }

    public static <T> Optional<T> ifEmpty(Optional<T> optional, Operation operation) {
        if (!optional.isPresent()) {
            operation.perform();
        }
        return optional;
    }

    public static <T> Optional<T> fromGuava(com.google.common.base.Optional<T> guava) {
        return Optional.ofNullable(guava.orNull());
    }

    public static <T> com.google.common.base.Optional<T> toGuava(Optional<T> java) {
        return com.google.common.base.Optional.fromNullable(java.orElse(null));
    }

    public static <T> Stream<T> toStream(Optional<T> optional) {
        return optional.map(Stream::of)
            .orElse(Stream.of());
    }
}
