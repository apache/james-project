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

package org.apache.james.utils;

import java.util.List;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class AllMatching<T> extends BaseMatcher<List<T>> {
    private final Matcher<T> matcher;

    public static <U> AllMatching<U> matcher(Matcher<U> matcher) {
        return new AllMatching<>(matcher);
    }

    private AllMatching(Matcher<T> matcher) {
        this.matcher = matcher;
    }

    @Override
    public boolean matches(Object item) {
        @SuppressWarnings("unchecked")
        Iterable<Object> list = (Iterable<Object>) (item);
        for (Object element: list) {
            if (!matcher.matches(element)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("All elements of the iterable should match: ")
            .appendDescriptionOf(matcher);
    }
}
