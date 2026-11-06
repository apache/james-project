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

package org.apache.james.rrt.lib;

import java.util.List;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

class SafeRegexEngine implements RegexEngine {
    @VisibleForTesting
    static final int MAX_REGEX_LENGTH = 500;
    private static final CharMatcher BRACES = CharMatcher.anyOf("{}");

    @Override
    public void checkMapping(String mapping) {
        if (BRACES.matchesAnyOf(mapping)) {
            throw new PatternSyntaxException("Regex mapping should not contain '{' nor '}'", mapping, -1);
        }
        if (CharMatcher.javaIsoControl().matchesAnyOf(mapping)) {
            throw new PatternSyntaxException("Regex mapping should not contain control characters", CharMatcher.javaIsoControl().removeFrom(mapping), -1);
        }
    }

    @Override
    public void validate(String mapping) {
        checkMapping(mapping);
        compile(Splitter.on(':').split(mapping).iterator().next());
    }

    @Override
    public Optional<List<String>> match(String regex, String input) {
        Matcher matcher = compile(regex).matcher(input);
        if (matcher.matches()) {
            return Optional.of(IntStream.rangeClosed(1, matcher.groupCount())
                .mapToObj(matcher::group)
                .collect(ImmutableList.toImmutableList()));
        }
        return Optional.empty();
    }

    private Pattern compile(String regex) {
        if (regex.length() > MAX_REGEX_LENGTH) {
            throw new PatternSyntaxException("Regex exceeds " + MAX_REGEX_LENGTH + " characters", regex, -1);
        }
        checkMapping(regex);
        try {
            return Pattern.compile(regex);
        } catch (com.google.re2j.PatternSyntaxException e) {
            throw new PatternSyntaxException(e.getDescription(), regex, -1);
        }
    }
}
