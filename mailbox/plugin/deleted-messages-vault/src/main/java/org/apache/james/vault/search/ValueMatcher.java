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

package org.apache.james.vault.search;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

public interface ValueMatcher<T> {

    boolean matches(T referenceValue);

    interface SingleValueMatcher<T> extends ValueMatcher<T> {
        T testedValue();
    }

    interface ListMatcher<T> extends ValueMatcher<List<T>> {
        T testedValue();
    }

    interface Equals<T> extends SingleValueMatcher<T> {
        @Override
        default boolean matches(T referenceValue) {
            return referenceValue.equals(testedValue());
        }
    }

    interface StringContains extends SingleValueMatcher<String> {
        @Override
        default boolean matches(String referenceValue) {
            return referenceValue.contains(testedValue());
        }
    }

    interface StringContainsIgnoreCase extends SingleValueMatcher<String> {
        @Override
        default boolean matches(String referenceValue) {
            return referenceValue.toLowerCase(Locale.US).contains(testedValue().toLowerCase(Locale.US));
        }
    }

    interface ListContains<T> extends ListMatcher<T> {
        @Override
        default boolean matches(List<T> referenceValue) {
            return referenceValue.contains(testedValue());
        }
    }

    interface ZonedDateTimeBeforeOrEquals extends SingleValueMatcher<ZonedDateTime> {
        @Override
        default boolean matches(ZonedDateTime referenceValue) {
            return referenceValue.isBefore(testedValue()) || referenceValue.isEqual(testedValue());
        }
    }

    interface ZonedDateTimeAfterOrEquals extends SingleValueMatcher<ZonedDateTime> {
        @Override
        default boolean matches(ZonedDateTime referenceValue) {
            return referenceValue.isAfter(testedValue()) || referenceValue.isEqual(testedValue());
        }
    }
}
