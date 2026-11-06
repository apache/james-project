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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableList;

class UnsafeRegexEngine implements RegexEngine {
    @Override
    public void checkMapping(String mapping) {

    }

    @Override
    public void validate(String mapping) {
        Pattern.compile(mapping);
    }

    @Override
    public Optional<List<String>> match(String regex, String input) {
        Matcher matcher = Pattern.compile(regex).matcher(input);
        if (matcher.matches()) {
            return Optional.of(IntStream.rangeClosed(1, matcher.groupCount())
                .mapToObj(matcher::group)
                .collect(ImmutableList.toImmutableList()));
        }
        return Optional.empty();
    }
}
