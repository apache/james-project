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
import static org.assertj.guava.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.base.Optional;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CidTest {

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void fromShouldThrowWhenNull() {
        expectedException.expect(IllegalArgumentException.class);
        Cid.from(null);
    }

    @Test
    public void fromShouldThrowWhenEmpty() {
        expectedException.expect(IllegalArgumentException.class);
        Cid.from("");
    }

    @Test
    public void fromShouldThrowWhenBlank() {
        expectedException.expect(IllegalArgumentException.class);
        Cid.from("    ");
    }

    @Test
    public void fromShouldThrowWhenEmptyAfterRemoveTags() {
        expectedException.expect(IllegalArgumentException.class);
        Cid.from("<>");
    }

    @Test
    public void fromShouldThrowWhenBlankAfterRemoveTags() {
        expectedException.expect(IllegalArgumentException.class);
        Cid.from("<   >");
    }

    @Test
    public void fromShouldRemoveTagsWhenExists() {
        Cid cid = Cid.from("<123>");
        assertThat(cid.getValue()).isEqualTo("123");
    }

    @Test
    public void fromShouldNotRemoveTagsWhenNone() {
        Cid cid = Cid.from("123");
        assertThat(cid.getValue()).isEqualTo("123");
    }

    @Test
    public void fromShouldNotRemoveTagsWhenNotEndTag() {
        Cid cid = Cid.from("<123");
        assertThat(cid.getValue()).isEqualTo("<123");
    }

    @Test
    public void fromShouldNotRemoveTagsWhenNotStartTag() {
        Cid cid = Cid.from("123>");
        assertThat(cid.getValue()).isEqualTo("123>");
    }

    @Test
    public void fromRelaxedNoUnwrapShouldReturnAbsentWhenNull() {
        assertThat(Cid.parser()
            .relaxed()
            .parse(null))
            .isAbsent();
    }

    @Test
    public void fromRelaxedNoUnwrapShouldReturnAbsentWhenEmpty() {
        assertThat(Cid.parser()
            .relaxed()
            .parse(""))
            .isAbsent();
    }

    @Test
    public void fromRelaxedNoUnwrapShouldReturnAbsentWhenBlank() {
        assertThat(Cid.parser()
            .relaxed()
            .parse("     "))
            .isAbsent();
    }

    @Test
    public void fromRelaxedNoUnwrapShouldReturnCidWhenEmptyAfterRemoveTags() {
        Optional<Cid> actual = Cid.parser()
            .relaxed()
            .parse("<>");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<>");
    }

    @Test
    public void fromRelaxedNoUnwrapShouldReturnCidWhenBlankAfterRemoveTags() {
        Optional<Cid> actual = Cid.parser()
            .relaxed()
            .parse("<   >");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<   >");
    }

    @Test
    public void fromRelaxedNoUnwrapShouldNotRemoveTagsWhenExists() {
        Optional<Cid> actual = Cid.parser()
            .relaxed()
            .parse("<123>");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<123>");
    }

    @Test
    public void fromRelaxedNoUnwrapShouldNotRemoveTagsWhenNone() {
        assertThat(Cid.parser()
            .relaxed()
            .parse("123"))
            .contains(Cid.from("123"));
    }

    @Test
    public void fromRelaxedNoUnwrapShouldNotRemoveTagsWhenNotEndTag() {
        assertThat(Cid.parser()
            .relaxed()
            .parse("<123"))
            .contains(Cid.from("<123"));
    }

    @Test
    public void fromRelaxedNoUnwrapShouldNotRemoveTagsWhenNotStartTag() {
        assertThat(Cid.parser()
            .relaxed()
            .parse("123>"))
            .contains(Cid.from("123>"));
    }


    @Test
    public void fromRelaxedUnwrapShouldReturnAbsentWhenNull() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse(null))
            .isAbsent();
    }

    @Test
    public void fromRelaxedUnwrapShouldReturnAbsentWhenEmpty() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse(""))
            .isAbsent();
    }

    @Test
    public void fromRelaxedUnwrapShouldReturnAbsentWhenBlank() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("     "))
            .isAbsent();
    }

    @Test
    public void fromRelaxedUnwrapShouldReturnAbsentWhenEmptyAfterRemoveTags() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("<>"))
            .isAbsent();
    }

    @Test
    public void fromRelaxedUnwrapShouldReturnAbsentWhenBlankAfterRemoveTags() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("<   >"))
            .isAbsent();
    }

    @Test
    public void fromRelaxedUnwrapShouldRemoveTagsWhenExists() {
        Optional<Cid> actual = Cid.parser()
            .relaxed()
            .unwrap()
            .parse("<123>");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("123");
    }

    @Test
    public void fromRelaxedUnwrapShouldNotRemoveTagsWhenNone() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("123"))
            .contains(Cid.from("123"));
    }

    @Test
    public void fromRelaxedUnwrapShouldNotRemoveTagsWhenNotEndTag() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("<123"))
            .contains(Cid.from("<123"));
    }

    @Test
    public void fromRelaxedUnwrapShouldNotRemoveTagsWhenNotStartTag() {
        assertThat(Cid.parser()
            .relaxed()
            .unwrap()
            .parse("123>"))
            .contains(Cid.from("123>"));
    }

    @Test
    public void fromStrictNoUnwrapShouldThrowWhenNull() {
        expectedException.expect(IllegalArgumentException.class);

        Cid.parser()
            .strict()
            .parse(null);
    }

    @Test
    public void fromStrictNoUnwrapShouldThrowWhenEmpty() {
        expectedException.expect(IllegalArgumentException.class);

        Cid.parser()
            .strict()
            .parse("");
    }

    @Test
    public void fromStrinctNoUnwrapShouldThrowWhenBlank() {
        expectedException.expect(IllegalArgumentException.class);

        Cid.parser()
            .strict()
            .parse("   ");
    }

    @Test
    public void fromStrictNoUnwrapShouldNotRemoveTagWhenEmptyAfterRemoveTags() {
        Optional<Cid> actual = Cid.parser()
            .strict()
            .parse("<>");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<>");
    }

    @Test
    public void fromStrictNoUnwrapShouldNotRemoveTagWhenBlankAfterRemoveTags() {
        Optional<Cid> actual = Cid.parser()
            .strict()
            .parse("<   >");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<   >");
    }

    @Test
    public void fromStrictNoUnwrapShouldNotRemoveTagsWhenExists() {
        Optional<Cid> actual = Cid.parser()
            .strict()
            .parse("<123>");
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo("<123>");
    }

    @Test
    public void fromStrictNoUnwrapShouldNotRemoveTagsWhenNone() {
        assertThat(Cid.parser()
            .strict()
            .parse("123"))
            .contains(Cid.from("123"));
    }

    @Test
    public void fromStrictNoUnwrapShouldNotRemoveTagsWhenNotEndTag() {
        assertThat(Cid.parser()
            .strict()
            .parse("<123"))
            .contains(Cid.from("<123"));
    }

    @Test
    public void fromStrictNoUnwrapShouldNotRemoveTagsWhenNotStartTag() {
        assertThat(Cid.parser()
            .strict()
            .parse("123>"))
            .contains(Cid.from("123>"));
    }

    @Test
    public void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(Cid.class).verify();
    }
}
