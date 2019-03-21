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

package org.apache.james.mailrepository.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailRepositoryPathTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MailRepositoryPath.class)
            .verify();
    }

    @Test
    void fromShouldRemoveTrailingSlash() {
        assertThat(MailRepositoryPath.from("abc/def/"))
            .isEqualTo(MailRepositoryPath.from("abc/def"));
    }

    @Test
    void fromShouldRemoveTrailingSlashes() {
        assertThat(MailRepositoryPath.from("abc/def//"))
            .isEqualTo(MailRepositoryPath.from("abc/def"));
    }

    @Test
    void fromShouldAcceptEmptyString() {
        assertThat(MailRepositoryPath.from("").asString())
            .isEqualTo("");
    }

    @Test
    void fromShouldAcceptRemoveTrailingSlashesWhenOnlySlashes() {
        assertThat(MailRepositoryPath.from("//"))
            .isEqualTo(MailRepositoryPath.from(""));
    }

    @Test
    void subPathShouldAppendSuffix() {
        assertThat(MailRepositoryPath.from("abc/def").subPath("ghi"))
            .isEqualTo(MailRepositoryPath.from("abc/def/ghi"));
    }

    @Test
    void subPathShouldBeANoopWhenEmptySuffix() {
        assertThat(MailRepositoryPath.from("abc/def").subPath(""))
            .isEqualTo(MailRepositoryPath.from("abc/def"));
    }

    @Test
    void fromEncodedShouldDecodeInput() throws Exception {
        assertThat(MailRepositoryPath.fromEncoded("abc%2Fdef"))
            .isEqualTo(MailRepositoryPath.from("abc/def"));
    }

    @Test
    void fromShouldRejectNull() {
        assertThatThrownBy(() -> MailRepositoryPath.from(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromEncodedShouldRejectNull() {
        assertThatThrownBy(() -> MailRepositoryPath.fromEncoded(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void subPathShouldRejectSlashPrefixedSuffixes() {
        assertThatThrownBy(() -> MailRepositoryPath.from("abc").subPath("/def"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasPrefixShouldReturnFalseWhenEquals() {
        assertThat(MailRepositoryPath.from("abc/def").hasPrefix(MailRepositoryPath.from("abc/def")))
            .isFalse();
    }

    @Test
    void hasPrefixShouldReturnFalseWhenSiblings() {
        assertThat(MailRepositoryPath.from("abc/def").hasPrefix(MailRepositoryPath.from("abc/ghi")))
            .isFalse();
    }

    @Test
    void hasPrefixShouldReturnFalseWhenAncestor() {
        assertThat(MailRepositoryPath.from("abc").hasPrefix(MailRepositoryPath.from("abc/ghi")))
            .isFalse();
    }

    @Test
    void hasPrefixShouldReturnTrueWhenDescendant() {
        assertThat(MailRepositoryPath.from("abc/ghi").hasPrefix(MailRepositoryPath.from("abc")))
            .isTrue();
    }

    @Test
    void hasPrefixShouldReturnTrueWhenDescendantStartingWithSlash() {
        assertThat(MailRepositoryPath.from("/abc/ghi").hasPrefix(MailRepositoryPath.from("/abc")))
            .isTrue();
    }

    @Test
    void hasPrefixShouldReturnFalseWhenDescendantAdditionalFirstSlash() {
        assertThat(MailRepositoryPath.from("abc/ghi").hasPrefix(MailRepositoryPath.from("/abc")))
            .isFalse();
    }

    @Test
    void hasPrefixShouldReturnFalseWhenDescendantMissingFirstSlash() {
        assertThat(MailRepositoryPath.from("/abc/ghi").hasPrefix(MailRepositoryPath.from("abc")))
            .isFalse();
    }

    @Test
    void hasPrefixShouldReturnTrueWhenFarDescendant() {
        assertThat(MailRepositoryPath.from("abc/ghi/klm").hasPrefix(MailRepositoryPath.from("abc")))
            .isTrue();
    }

    @Test
    void hasPrefixShouldReturnTrueWhenEmpty() {
        assertThat(MailRepositoryPath.from("abc").hasPrefix(MailRepositoryPath.from("")))
            .isTrue();
    }

    @Test
    void hasPrefixShouldReturnFalseWhenBothEmpty() {
        assertThat(MailRepositoryPath.from("").hasPrefix(MailRepositoryPath.from("")))
            .isFalse();
    }

    @Test
    void hasPrefixShouldReturnFalseWhenMissingSlah() {
        assertThat(MailRepositoryPath.from("abcghi").hasPrefix(MailRepositoryPath.from("abc")))
            .isFalse();
    }
}