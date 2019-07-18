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

import java.util.function.Predicate;

import org.apache.james.vault.DeletedMessage;

public class Criterion<T> {

    public static class ValueMatcher<V, T> {

        private V expectedValue;
        private Operator operator;
        private Predicate<T> matchPredicate;

        public ValueMatcher(V expectedValue, Operator operator, Predicate<T> matchPredicate) {
            this.expectedValue = expectedValue;
            this.operator = operator;
            this.matchPredicate = matchPredicate;
        }

        public V expectedValue() {
            return expectedValue;
        }

        public Operator operator() {
            return operator;
        }

        public boolean matches(T value) {
            return matchPredicate.test(value);
        }
    }

    public interface ExpectMatcher<T> {
        Criterion<T> withMatcher(ValueMatcher<?, T> valueMatcher);
    }

    public interface Builder {
        static <U> ExpectMatcher<U> forField(DeletedMessageField<U> field) {
            return matcher -> new Criterion<>(field, matcher);
        }
    }

    private static final boolean DEFAULT_TO_NON_MATCHED_IF_NON_EXIST = false;

    private final DeletedMessageField<T> field;
    private final ValueMatcher<?, T> valueMatcher;

    private Criterion(DeletedMessageField<T> field, ValueMatcher<?, T> valueMatcher) {
        this.field = field;
        this.valueMatcher = valueMatcher;
    }

    Predicate<DeletedMessage> toPredicate() {
        return deletedMessage -> field.valueExtractor().extract(deletedMessage)
            .map(valueMatcher::matches)
            .orElse(DEFAULT_TO_NON_MATCHED_IF_NON_EXIST);
    }
}
