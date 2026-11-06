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
    RegexEngine SAFE = new SafeRegexEngine();
    RegexEngine UNSAFE = new UnsafeRegexEngine();

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
}
