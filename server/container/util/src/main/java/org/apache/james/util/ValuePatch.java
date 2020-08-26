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

package org.apache.james.util;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.base.Preconditions;

public class ValuePatch<T> {

    private enum State {
        KEEP,
        REMOVE,
        MODIFY
    }

    public static <T> ValuePatch<T> modifyTo(T value) {
        Preconditions.checkNotNull(value);
        return new ValuePatch<>(value, State.MODIFY);
    }

    public static <T> ValuePatch<T> ofNullable(T value) {
        return ofOptional(Optional.ofNullable(value));
    }

    public static <T> ValuePatch<T> ofOptional(Optional<T> value) {
        Preconditions.checkNotNull(value);
        return value.map(ValuePatch::modifyTo)
            .orElse(ValuePatch.remove());
    }

    public static <T> ValuePatch<T> remove() {
        return new ValuePatch<>(null, State.REMOVE);
    }

    public static <T> ValuePatch<T> keep() {
        return new ValuePatch<>(null, State.KEEP);
    }

    private final T value;
    private final State state;

    private ValuePatch(T value, State state) {
        this.value = value;
        this.state = state;
    }

    public boolean isRemoved() {
        return state == State.REMOVE;
    }

    public boolean isModified() {
        return state == State.MODIFY;
    }

    public boolean isKept() {
        return state == State.KEEP;
    }

    public <S> Optional<S> mapNotKeptToOptional(Function<Optional<T>, S> updateTransformation) {
        if (isKept()) {
            return Optional.empty();
        }
        return Optional.of(updateTransformation.apply(Optional.ofNullable(value)));
    }

    public T get() {
        if (isModified()) {
            return value;
        } else {
            throw new NoSuchElementException();
        }
    }

    public Optional<T> notKeptOrElse(Optional<T> replacement) {
        if (isKept()) {
            return replacement;
        }
        return Optional.ofNullable(value);
    }

    public Optional<T> toOptional() {
        return Optional.ofNullable(value);
    }

    public T getOrElse(T replacement) {
        return toOptional().orElse(replacement);
    }

    public ValuePatch<T> merge(ValuePatch<T> other) {
        if (isKept()) {
            return other;
        }

        if (other.isKept()) {
            return this;
        }

        throw new IllegalStateException("the two merged ValuePatch represent a mutation");
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ValuePatch) {
            ValuePatch<?> that = (ValuePatch<?>) o;
            return Objects.equals(this.value, that.value) &&
                Objects.equals(this.state, that.state);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, state);
    }
}
