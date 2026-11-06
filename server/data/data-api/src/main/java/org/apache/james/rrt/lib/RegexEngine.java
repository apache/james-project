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

/**
 * Evaluates the regular expressions of regex mappings.
 *
 * Regex mappings are evaluated against the recipients of incoming mails. The default engine relies on RE2J, whose
 * matching time is linear in the size of the input and of the compiled program. RE2J expands counted repetitions at
 * compile time, which can exhaust the heap for a short regex with nested repetitions. They are therefore rejected,
 * and the length limit then bounds the size of the compiled program. For simplicity, braces are rejected in the whole
 * mapping, including the ${1} parameters of the parameterized string.
 *
 * Setting the <code>james.rrt.regex.safe</code> system property to <code>false</code> selects java.util.regex
 * without any of these limits. This restores the syntax RE2J does not support (lookaround, backreferences, ...).
 */
public interface RegexEngine {
    RegexEngine SAFE = new Safe();
    RegexEngine UNSAFE = new Unsafe();

    static RegexEngine configured() {
        return Holder.CONFIGURED;
    }

    /**
     * Checks the mapping without compiling it.
     *
     * @param mapping formatted as &lt;regular-expression&gt;:&lt;parameterized-string&gt;
     * @throws PatternSyntaxException when the engine refuses this mapping
     */
    void checkMapping(String mapping);

    /**
     * Checks the mapping and compiles its regular expression.
     *
     * @param mapping formatted as &lt;regular-expression&gt;:&lt;parameterized-string&gt;
     * @throws PatternSyntaxException when the engine refuses this mapping
     */
    void validate(String mapping);

    /**
     * @return the capturing groups when the regex matches the entire input, empty otherwise
     * @throws PatternSyntaxException when the engine refuses this regex
     */
    Optional<List<String>> match(String regex, String input);

    class Holder {
        private static final RegexEngine CONFIGURED = Boolean.parseBoolean(System.getProperty("james.rrt.regex.safe", "true"))
            ? SAFE
            : UNSAFE;
    }

    class Safe implements RegexEngine {
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
            com.google.re2j.Matcher matcher = compile(regex).matcher(input);
            if (matcher.matches()) {
                return Optional.of(IntStream.rangeClosed(1, matcher.groupCount())
                    .mapToObj(matcher::group)
                    .collect(ImmutableList.toImmutableList()));
            }
            return Optional.empty();
        }

        private com.google.re2j.Pattern compile(String regex) {
            if (regex.length() > MAX_REGEX_LENGTH) {
                throw new PatternSyntaxException("Regex exceeds " + MAX_REGEX_LENGTH + " characters", regex, -1);
            }
            checkMapping(regex);
            try {
                return com.google.re2j.Pattern.compile(regex);
            } catch (com.google.re2j.PatternSyntaxException e) {
                throw new PatternSyntaxException(e.getDescription(), regex, -1);
            }
        }
    }

    class Unsafe implements RegexEngine {
        @Override
        public void checkMapping(String mapping) {

        }

        @Override
        public void validate(String mapping) {
            java.util.regex.Pattern.compile(mapping);
        }

        @Override
        public Optional<List<String>> match(String regex, String input) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(input);
            if (matcher.matches()) {
                return Optional.of(IntStream.rangeClosed(1, matcher.groupCount())
                    .mapToObj(matcher::group)
                    .collect(ImmutableList.toImmutableList()));
            }
            return Optional.empty();
        }
    }
}
