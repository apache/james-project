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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class FunctionalUtils {
    public static <T> UnaryOperator<T> toFunction(Consumer<T> consumer) {
        return argument -> {
            consumer.accept(argument);
            return argument;
        };
    }

    public static <T> UnaryOperator<T> identityWithSideEffect(Runnable runnable) {
        return argument -> {
            runnable.run();
            return argument;
        };
    }

    public static Function<Boolean, Boolean> negate() {
        return b -> !b;
    }

    public static Predicate<Boolean> identityPredicate() {
        return b -> b;
    }
}
