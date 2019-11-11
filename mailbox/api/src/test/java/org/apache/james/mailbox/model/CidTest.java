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

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class CidTest {

    @Test
    void fromShouldThrowWhenNull() {
        assertThatThrownBy(() -> Cid.from(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenEmpty() {
        assertThatThrownBy(() -> Cid.from(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenBlank() {
        assertThatThrownBy(() -> Cid.from("    "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenEmptyAfterRemoveTags() {
        assertThatThrownBy(() -> Cid.from("<>"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenBlankAfterRemoveTags() {
        assertThatThrownBy(() -> Cid.from("<   >"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldRemoveTagsWhenExists() {
        Cid cid = Cid.from("<123>");
        assertThat(cid.getValue()).isEqualTo("123");
    }

    @Test
    void fromShouldNotRemoveTagsWhenNone() {
        Cid cid = Cid.from("123");
        assertThat(cid.getValue()).isEqualTo("123");
    }

    @Test
    void fromShouldNotRemoveTagsWhenNotEndTag() {
        Cid cid = Cid.from("<123");
        assertThat(cid.getValue()).isEqualTo("<123");
    }

    @Test
    void fromShouldNotRemoveTagsWhenNotStartTag() {
        Cid cid = Cid.from("123>");
        assertThat(cid.getValue()).isEqualTo("123>");
    }

    @Test
    void fromRelaxedNoUnwrapShouldReturnAbsentWhenNull() {
        assertThat(Cid.parser()
            .relaxed()
            .parse(null))
            .isEmpty();
    }

    @Test
    void fromRelaxedNoUnwrapShouldReturnAbsentWhenEmpty() {
        assertThat(Cid.parser()
            .relaxed()
            .parse(""))
            .isEmpty();
    }

    @Test
    void fromRelaxedNoUnwrapShouldReturnAbsentWhenBlank() {
        assertThat(Cid.parser()
            .relaxed()
            .parse("     "))
            .isEmpty();
    }

    @Test
    void fromRelaxedNoUnwrapShouldReturnCidWhenEmptyAfterRemoveTags() {
        Optional<Cid> actual = Cid.parser()
            .relaxed()
            .parse("<>");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<>");
    }

    @Test
    void fromRelaxedNoUnwrapShouldReturnCidWhenBlankAfterRemoveTags() {
        Optional<Cid> actual = Cid.parser()
            .relaxed()
            .parse("<   >");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<   >");
    }

    @Test
    void fromRelaxedNoUnwrapShouldNotRemoveTagsWhenExists() {
        Optional<Cid> actual = Cid.parser()
            .relaxed()
            .parse("<123>");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<123>");
    }

    @Test
    void fromRelaxedNoUnwrapShouldNotRemoveTagsWhenNone() {
        assertThat(Cid.parser()
            .relaxed()
            .parse("123"))
            .contains(Cid.from("123"));
    }

    @Test
    void fromRelaxedNoUnwrapShouldNotRemoveTagsWhenNotEndTag() {
        assertThat(Cid.parser()
            .relaxed()
            .parse("<123"))
            .contains(Cid.from("<123"));
    }

    @Test
    void fromRelaxedNoUnwrapShouldNotRemoveTagsWhenNotStartTag() {
        assertThat(Cid.parser()
            .relaxed()
            .parse("123>"))
            .contains(Cid.from("123>"));
    }


    @Test
    void fromRelaxedUnwrapShouldReturnAbsentWhenNull() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse(null))
            .isEmpty();
    }

    @Test
    void fromRelaxedUnwrapShouldReturnAbsentWhenEmpty() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse(""))
            .isEmpty();
    }

    @Test
    void fromRelaxedUnwrapShouldReturnAbsentWhenBlank() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("     "))
            .isEmpty();
    }

    @Test
    void fromRelaxedUnwrapShouldReturnAbsentWhenEmptyAfterRemoveTags() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("<>"))
            .isEmpty();
    }

    @Test
    void fromRelaxedUnwrapShouldReturnAbsentWhenBlankAfterRemoveTags() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("<   >"))
            .isEmpty();
    }

    @Test
    void fromRelaxedUnwrapShouldRemoveTagsWhenExists() {
        Optional<Cid> actual = Cid.parser()
            .relaxed()
            .unwrap()
            .parse("<123>");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("123");
    }

    @Test
    void fromRelaxedUnwrapShouldNotRemoveTagsWhenNone() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("123"))
            .contains(Cid.from("123"));
    }

    @Test
    void fromRelaxedUnwrapShouldNotRemoveTagsWhenNotEndTag() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("<123"))
            .contains(Cid.from("<123"));
    }

    @Test
    void fromRelaxedUnwrapShouldNotRemoveTagsWhenNotStartTag() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("123>"))
            .contains(Cid.from("123>"));
    }

    @Test
    void fromStrictNoUnwrapShouldThrowWhenNull() {
        assertThatThrownBy(() -> Cid.parser()
                .strict()
                .parse(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromStrictNoUnwrapShouldThrowWhenEmpty() {
        assertThatThrownBy(() -> Cid.parser()
                .strict()
                .parse(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromStrinctNoUnwrapShouldThrowWhenBlank() {
        assertThatThrownBy(() -> Cid.parser()
                .strict()
                .parse("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromStrictNoUnwrapShouldNotRemoveTagWhenEmptyAfterRemoveTags() {
        Optional<Cid> actual = Cid.parser()
            .strict()
            .parse("<>");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<>");
    }

    @Test
    void fromStrictNoUnwrapShouldNotRemoveTagWhenBlankAfterRemoveTags() {
        Optional<Cid> actual = Cid.parser()
            .strict()
            .parse("<   >");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<   >");
    }

    @Test
    void fromStrictNoUnwrapShouldNotRemoveTagsWhenExists() {
        Optional<Cid> actual = Cid.parser()
            .strict()
            .parse("<123>");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<123>");
    }

    @Test
    void fromStrictNoUnwrapShouldNotRemoveTagsWhenNone() {
        assertThat(Cid.parser()
            .strict()
            .parse("123"))
            .contains(Cid.from("123"));
    }

    @Test
    void fromStrictNoUnwrapShouldNotRemoveTagsWhenNotEndTag() {
        assertThat(Cid.parser()
            .strict()
            .parse("<123"))
            .contains(Cid.from("<123"));
    }

    @Test
    void fromStrictNoUnwrapShouldNotRemoveTagsWhenNotStartTag() {
        assertThat(Cid.parser()
            .strict()
            .parse("123>"))
            .contains(Cid.from("123>"));
    }

    @Test
    void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(Cid.class).verify();
    }
}
