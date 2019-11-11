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

import org.junit.jupiter.api.Test;

class MailboxAnnotationKeyTest {
    @Test
    void newInstanceShouldThrowsExceptionWhenKeyDoesNotStartWithSlash() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("shared"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newInstanceShouldThrowsExceptionWhenKeyContainsAsterisk() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("/private/key*comment"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newInstanceShouldThrowsExceptionWhenKeyContainsPercent() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("/private/key%comment"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validKeyShouldThrowsExceptionWhenKeyContainsTwoConsecutiveSlash() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("/private//keycomment"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validKeyShouldThrowsExceptionWhenKeyEndsWithSlash() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("/private/keycomment/"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validKeyShouldThrowsExceptionWhenKeyContainsNonASCII() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("/private/key┬ácomment"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validKeyShouldThrowsExceptionWhenKeyContainsTabCharacter() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("/private/key\tcomment"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newInstanceShouldThrowsExceptionWithEmptyKey() {
        assertThatThrownBy(() -> new MailboxAnnotationKey(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newInstanceShouldThrowsExceptionWithBlankKey() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("    "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newInstanceShouldReturnRightKeyValue() {
        MailboxAnnotationKey annotationKey = new MailboxAnnotationKey("/private/comment");
        assertThat(annotationKey.asString()).isEqualTo("/private/comment");
    }

    @Test
    void keyValueShouldBeCaseInsensitive() {
        MailboxAnnotationKey annotationKey = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey anotherAnnotationKey = new MailboxAnnotationKey("/PRIVATE/COMMENT");

        assertThat(annotationKey).isEqualTo(anotherAnnotationKey);
    }

    @Test
    void newInstanceShouldThrowsExceptionWhenKeyContainsPunctuationCharacters() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("/private/+comment"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countSlashShouldReturnRightNumberOfSlash() {
        MailboxAnnotationKey annotationKey = new MailboxAnnotationKey("/private/comment/user/name");
        assertThat(annotationKey.countComponents()).isEqualTo(4);
    }

    @Test
    void keyMustContainAtLeastTwoComponents() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("/private"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keyVendorShouldThrowsExceptionWithTwoComponents() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("/private/vendor"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keyVendorShouldThrowsExceptionWithThreeComponents() {
        assertThatThrownBy(() -> new MailboxAnnotationKey("/shared/vendor/token"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keyVendorShouldOKWithFourComponents() {
        new MailboxAnnotationKey("/shared/vendor/token/comment");
    }

    @Test
    void isParentOrIsEqualShouldReturnTrueWhenSameKey() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");

        assertThat(key1.isParentOrIsEqual(key1)).isTrue();
    }

    @Test
    void isParentOrIsEqualShouldReturnTrueWhenParent() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto");

        assertThat(key1.isParentOrIsEqual(key2)).isTrue();
    }

    @Test
    void isParentOrIsEqualShouldReturnFalseWhenChild() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto");

        assertThat(key2.isParentOrIsEqual(key1)).isFalse();
    }

    @Test
    void isParentOrIsEqualShouldReturnFalseWhenGrandParent() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto/tata");

        assertThat(key1.isParentOrIsEqual(key2)).isFalse();
    }

    @Test
    void isParentOrIsEqualShouldReturnFalseWhenCousin() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment/tutu");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto/tata");

        assertThat(key1.isParentOrIsEqual(key2)).isFalse();
    }


    @Test
    void isAncestorOrIsEqualShouldReturnTrueWhenSameKey() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");

        assertThat(key1.isAncestorOrIsEqual(key1)).isTrue();
    }

    @Test
    void isAncestorOrIsEqualShouldReturnTrueWhenParent() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto");

        assertThat(key1.isAncestorOrIsEqual(key2)).isTrue();
    }

    @Test
    void isAncestorOrIsEqualShouldReturnFalseWhenChild() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto");

        assertThat(key2.isAncestorOrIsEqual(key1)).isFalse();
    }

    @Test
    void isAncestorOrIsEqualShouldReturnTrueWhenGrandParent() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto/tata");

        assertThat(key1.isAncestorOrIsEqual(key2)).isTrue();
    }

    @Test
    void isAncestorOrIsEqualShouldReturnFalseWhenCousin() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment/tutu");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto/tata");

        assertThat(key1.isAncestorOrIsEqual(key2)).isFalse();
    }

    @Test
    void isAncestorOrIsEqualShouldWorkOnCousinKeyUsingKeyAsAPrefix() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment/tutu");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/tututata");

        assertThat(key1.isAncestorOrIsEqual(key2)).isFalse();
    }

    @Test
    void isParentOrIsEqualShouldWorkOnCousinKeyUsingKeyAsAPrefix() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment/tutu");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/tututata");

        assertThat(key1.isParentOrIsEqual(key2)).isFalse();
    }
}