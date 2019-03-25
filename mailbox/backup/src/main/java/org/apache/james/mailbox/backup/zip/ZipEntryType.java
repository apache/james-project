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
package org.apache.james.mailbox.backup.zip;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.Streams;

public enum ZipEntryType {
    MAILBOX,
    MAILBOX_ANNOTATION_DIR,
    MAILBOX_ANNOTATION,
    MESSAGE;

    private static final Map<Integer, ZipEntryType> entryByOrdinal;

    static {
        Stream<ZipEntryType> valuesAsStream = Arrays.stream(values());
        Stream<Integer> indices = IntStream.range(0, values().length).boxed();

        entryByOrdinal = Streams.zip(indices, valuesAsStream, ImmutablePair::of)
            .collect(Guavate.entriesToImmutableMap());
    }

    public static Optional<ZipEntryType> zipEntryType(int ordinal) {
        return Optional.ofNullable(entryByOrdinal.get(ordinal));
    }
}
