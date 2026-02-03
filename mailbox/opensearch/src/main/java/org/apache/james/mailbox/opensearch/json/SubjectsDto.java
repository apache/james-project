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

package org.apache.james.mailbox.opensearch.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.james.mailbox.store.search.SearchUtil;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

public record SubjectsDto(@JsonValue Set<String> subjects) {
    public static SubjectsDto from(Set<String> subjects) {
        return new SubjectsDto(subjects.stream()
            .flatMap(SubjectsDto::withSubjectQualifiers)
            .distinct()
            .collect(ImmutableSet.toImmutableSet()));
    }

    public static Stream<String> withSubjectQualifiers(String subject) {
        String baseSubject = SearchUtil.getBaseSubject(subject);

        return ImmutableSet.<String>builder()
            .add(baseSubject)
            .addAll(extractQualifiers(subject))
            .build()
            .stream();
    }

    public static List<String> extractQualifiers(String subject) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inBrackets = false;

        for (char c : subject.toCharArray()) {
            if (c == '[') {
                inBrackets = true;
                current.setLength(0); // reset
            } else if (c == ']') {
                if (inBrackets) {
                    result.add(current.toString());
                    result.addAll(domainTokens(current.toString()));
                    inBrackets = false;
                }
            } else if (inBrackets) {
                current.append(c);
            }
        }
        return result;
    }

    public static List<String> domainTokens(String domain) {
        List<String> parts = Splitter.on('.')
            .omitEmptyStrings()
            .splitToList(domain);

        return IntStream.range(0, parts.size() - 1)
            .boxed()
            .flatMap(i -> Stream.of(
                Joiner.on('.').join(parts.subList(i, parts.size())),
                parts.get(i)))
            .distinct()
            .toList();
    }
}
