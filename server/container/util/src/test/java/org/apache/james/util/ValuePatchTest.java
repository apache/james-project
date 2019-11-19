/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy modifyTo the License at   *
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ValuePatchTest {

    private static final int REPLACEMENT_VALUE = 24;
    private static final Optional<Integer> REPLACEMENT = Optional.of(REPLACEMENT_VALUE);
    private static final int VALUE = 12;
    private static final Optional<Integer> OPTIONAL_OF_VALUE = Optional.of(VALUE);

    @Test
    void keepShouldProduceKeptValues() {
        assertThat(ValuePatch.<Integer>keep().isKept()).isTrue();
    }

    @Test
    void keepShouldThrowOnGet() {
        assertThatThrownBy(() -> ValuePatch.<Integer>keep().get()).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void keepShouldNotBeModified() {
        assertThat(ValuePatch.<Integer>keep().isModified()).isFalse();
    }

    @Test
    void keepShouldNotBeRemoved() {
        assertThat(ValuePatch.<Integer>keep().isRemoved()).isFalse();
    }

    @Test
    void removeShouldNotBeKept() {
        assertThat(ValuePatch.<Integer>remove().isKept()).isFalse();
    }

    @Test
    void removeShouldBeRemoved() {
        assertThat(ValuePatch.<Integer>remove().isRemoved()).isTrue();
    }

    @Test
    void removedShouldNotBeModified() {
        assertThat(ValuePatch.<Integer>remove().isModified()).isFalse();
    }

    @Test
    void removeShouldThrowOnGet() {
        assertThatThrownBy(() -> ValuePatch.<Integer>remove().get()).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void ofNullableShouldBeEquivalentToRemoveWhenNullParameter() {
        assertThat(ValuePatch.<Integer>ofNullable(null)).isEqualTo(ValuePatch.<Integer>remove());
    }

    @Test
    void ofNullableShouldBeEquivalentToModifyWhenNonNullParameter() {
        assertThat(ValuePatch.ofNullable(VALUE)).isEqualTo(ValuePatch.modifyTo(VALUE));
    }

    @Test
    void modifyToShouldNotBeKept() {
        assertThat(ValuePatch.modifyTo(VALUE).isKept()).isFalse();
    }

    @Test
    void modifyToShouldNotBeRemoved() {
        assertThat(ValuePatch.modifyTo(VALUE).isRemoved()).isFalse();
    }

    @Test
    void modifyToShouldBeModified() {
        assertThat(ValuePatch.modifyTo(VALUE).isModified()).isTrue();
    }

    @Test
    void modifyToShouldThrowOnNullValue() {
        assertThatThrownBy(() -> ValuePatch.modifyTo(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void modifyToShouldBeRetrievedByGet() {
        assertThat(ValuePatch.modifyTo(VALUE).get()).isEqualTo(VALUE);
    }

    @Test
    void ofOptionalShouldThrowOnNullValue() {
        assertThatThrownBy(() -> ValuePatch.ofOptional(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void ofOptionalShouldBeEquivalentToModifyToWhenPresent() {
        assertThat(ValuePatch.ofOptional(OPTIONAL_OF_VALUE)).isEqualTo(ValuePatch.modifyTo(VALUE));
    }

    @Test
    void ofOptionalShouldBeEquivalentToRemoveWhenEmpty() {
        assertThat(ValuePatch.ofOptional(Optional.empty())).isEqualTo(ValuePatch.remove());
    }

    @Test
    void notKeptOrElseShouldReturnElseWhenKept() {
        assertThat(ValuePatch.<Integer>keep().notKeptOrElse(REPLACEMENT)).isEqualTo(REPLACEMENT);
    }

    @Test
    void notKeptOrElseShouldReturnEmptyWhenRemoved() {
        assertThat(ValuePatch.<Integer>remove().notKeptOrElse(REPLACEMENT)).isEqualTo(Optional.empty());
    }

    @Test
    void notKeptOrElseShouldReturnOptionalWhenModified() {
        assertThat(ValuePatch.modifyTo(VALUE).notKeptOrElse(REPLACEMENT)).isEqualTo(OPTIONAL_OF_VALUE);
    }

    @Test
    void toOptionalShouldReturnElseWhenKept() {
        assertThat(ValuePatch.<Integer>keep().toOptional()).isEqualTo(Optional.empty());
    }

    @Test
    void toOptionalShouldReturnEmptyWhenRemoved() {
        assertThat(ValuePatch.<Integer>remove().toOptional()).isEqualTo(Optional.empty());
    }

    @Test
    void toOptionalShouldReturnOptionalWhenModified() {
        assertThat(ValuePatch.modifyTo(VALUE).toOptional()).isEqualTo(OPTIONAL_OF_VALUE);
    }

    @Test
    void getOrElseShouldReturnReplacementWhenKept() {
        assertThat(ValuePatch.<Integer>keep().getOrElse(REPLACEMENT_VALUE)).isEqualTo(REPLACEMENT_VALUE);
    }

    @Test
    void getOrElseShouldReturnReplacementWhenRemoved() {
        assertThat(ValuePatch.<Integer>remove().getOrElse(REPLACEMENT_VALUE)).isEqualTo(REPLACEMENT_VALUE);
    }

    @Test
    void getOrElseShouldReturnValueWhenPresent() {
        assertThat(ValuePatch.modifyTo(VALUE).getOrElse(REPLACEMENT_VALUE)).isEqualTo(VALUE);
    }

    @Test
    void getOrElseShouldReturnNullWhenKeptAndNullSpecified() {
        assertThat(ValuePatch.<Integer>keep().getOrElse(null)).isNull();
    }

    @Test
    void getOrElseShouldReturnNullWhenRemovedAndNullSpecified() {
        assertThat(ValuePatch.<Integer>remove().getOrElse(null)).isNull();
    }

    @Test
    void getOrElseShouldReturnValueWhenPresentAndNullSpecified() {
        assertThat(ValuePatch.modifyTo(VALUE).getOrElse(null)).isEqualTo(VALUE);
    }

    @Test
    void mapNotKeptToValueShouldPreserveKept() {
        assertThat(
            ValuePatch.<Integer>keep()
                .mapNotKeptToOptional(optional -> optional.map(i -> i + 1).orElse(REPLACEMENT_VALUE)))
            .isEmpty();
    }

    @Test
    void mapNotKeptToValueShouldTransformOf() {
        assertThat(
            ValuePatch.modifyTo(VALUE)
                .mapNotKeptToOptional(optional -> optional.map(i -> i + 1).orElse(REPLACEMENT_VALUE)))
            .contains(VALUE + 1);
    }

    @Test
    void mapNotKeptToValueShouldTransformRemoved() {
        assertThat(
            ValuePatch.<Integer>remove()
                .mapNotKeptToOptional(optional -> optional.map(i -> i + 1).orElse(REPLACEMENT_VALUE)))
            .contains(REPLACEMENT_VALUE);
    }

    @Test
    void mapNotKeptToValueShouldThrowWhenNull() {
        assertThatThrownBy(
            () -> ValuePatch.modifyTo(12)
                .mapNotKeptToOptional(any -> null)
                .isPresent())
            .isInstanceOf(NullPointerException.class);
    }

}
