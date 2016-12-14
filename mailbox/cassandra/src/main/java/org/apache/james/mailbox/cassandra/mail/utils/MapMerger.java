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

package org.apache.james.mailbox.cassandra.mail.utils;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import org.apache.james.mailbox.cassandra.mail.AttachmentLoader;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class MapMerger {

    public static <K, U, V, T> Map<K, T> merge(Map<K, U> lhs, Map<K, V> rhs, BiFunction<U, V, T> merge) {
        return lhs.entrySet().stream()
                .flatMap(x -> entry(x.getKey(), x.getValue(), rhs.get(x.getKey())))
                .collect(Guavate.toImmutableMap(Entry::getKey, x -> merge.apply(x.value1, x.value2)));
    }

    private static <K, U, V> Stream<Entry<K, U, V>> entry(K k, U u, V v) {
        if (v == null) {
            return Stream.of();
        } else {
            return Stream.of(new Entry(k, u ,v));
        }
    }

    private static class Entry<K, U, V> {
        private final K key;
        private final U value1;
        private final V value2;

        Entry(K key, U value1, V value2) {
            this.key = key;
            this.value1 = value1;
            this.value2 = value2;
        }

        public K getKey() {
            return key;
        }
    }
}
