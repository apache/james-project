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

import org.junit.Test;

public class MailboxAnnotationKeyTest {
    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowsExceptionWhenKeyDoesNotStartWithSlash() throws Exception {
        new MailboxAnnotationKey("shared");
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowsExceptionWhenKeyContainsAsterisk() throws Exception {
        new MailboxAnnotationKey("/private/key*comment");
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowsExceptionWhenKeyContainsPercent() throws Exception {
        new MailboxAnnotationKey("/private/key%comment");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validKeyShouldThrowsExceptionWhenKeyContainsTwoConsecutiveSlash() throws Exception {
        new MailboxAnnotationKey("/private//keycomment");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validKeyShouldThrowsExceptionWhenKeyEndsWithSlash() throws Exception {
        new MailboxAnnotationKey("/private/keycomment/");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validKeyShouldThrowsExceptionWhenKeyContainsNonASCII() throws Exception {
        new MailboxAnnotationKey("/private/key┬ácomment");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validKeyShouldThrowsExceptionWhenKeyContainsTabCharacter() throws Exception {
        new MailboxAnnotationKey("/private/key\tcomment");
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowsExceptionWithEmptyKey() throws Exception {
        new MailboxAnnotationKey("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowsExceptionWithBlankKey() throws Exception {
        new MailboxAnnotationKey("    ");
    }

    @Test
    public void newInstanceShouldReturnRightKeyValue() throws Exception {
        MailboxAnnotationKey annotationKey = new MailboxAnnotationKey("/private/comment");
        assertThat(annotationKey.asString()).isEqualTo("/private/comment");
    }

    @Test
    public void keyValueShouldBeCaseInsensitive() throws Exception {
        MailboxAnnotationKey annotationKey = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey anotherAnnotationKey = new MailboxAnnotationKey("/PRIVATE/COMMENT");

        assertThat(annotationKey.equals(anotherAnnotationKey)).isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowsExceptionWhenKeyContainsPunctuationCharacters() throws Exception {
        new MailboxAnnotationKey("/private/+comment");
    }

    @Test
    public void countSlashShouldReturnRightNumberOfSlash() throws Exception {
        MailboxAnnotationKey annotationKey = new MailboxAnnotationKey("/private/comment/user/name");
        assertThat(annotationKey.countComponents()).isEqualTo(4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void keyMustContainAtLeastTwoComponents() throws Exception {
        new MailboxAnnotationKey("/private");
    }

    @Test(expected = IllegalArgumentException.class)
    public void keyVendorShouldThrowsExceptionWithTwoComponents() throws Exception {
        new MailboxAnnotationKey("/private/vendor");
    }

    @Test(expected = IllegalArgumentException.class)
    public void keyVendorShouldThrowsExceptionWithThreeComponents() throws Exception {
        new MailboxAnnotationKey("/shared/vendor/token");
    }

    @Test
    public void keyVendorShouldOKWithFourComponents() throws Exception {
        new MailboxAnnotationKey("/shared/vendor/token/comment");
    }

    @Test
    public void isParentOrIsEqualShouldReturnTrueWhenSameKey() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");

        assertThat(key1.isParentOrIsEqual(key1)).isTrue();
    }

    @Test
    public void isParentOrIsEqualShouldReturnTrueWhenParent() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto");

        assertThat(key1.isParentOrIsEqual(key2)).isTrue();
    }

    @Test
    public void isParentOrIsEqualShouldReturnFalseWhenChild() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto");

        assertThat(key2.isParentOrIsEqual(key1)).isFalse();
    }

    @Test
    public void isParentOrIsEqualShouldReturnFalseWhenGrandParent() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto/tata");

        assertThat(key1.isParentOrIsEqual(key2)).isFalse();
    }

    @Test
    public void isParentOrIsEqualShouldReturnFalseWhenCousin() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment/tutu");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto/tata");

        assertThat(key1.isParentOrIsEqual(key2)).isFalse();
    }


    @Test
    public void isAncestorOrIsEqualShouldReturnTrueWhenSameKey() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");

        assertThat(key1.isAncestorOrIsEqual(key1)).isTrue();
    }

    @Test
    public void isAncestorOrIsEqualShouldReturnTrueWhenParent() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto");

        assertThat(key1.isAncestorOrIsEqual(key2)).isTrue();
    }

    @Test
    public void isAncestorOrIsEqualShouldReturnFalseWhenChild() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto");

        assertThat(key2.isAncestorOrIsEqual(key1)).isFalse();
    }

    @Test
    public void isAncestorOrIsEqualShouldReturnTrueWhenGrandParent() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto/tata");

        assertThat(key1.isAncestorOrIsEqual(key2)).isTrue();
    }

    @Test
    public void isAncestorOrIsEqualShouldReturnFalseWhenCousin() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment/tutu");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/toto/tata");

        assertThat(key1.isAncestorOrIsEqual(key2)).isFalse();
    }

    @Test
    public void isAncestorOrIsEqualShouldWorkOnCousinKeyUsingKeyAsAPrefix() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment/tutu");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/tututata");

        assertThat(key1.isAncestorOrIsEqual(key2)).isFalse();
    }

    @Test
    public void isParentOrIsEqualShouldWorkOnCousinKeyUsingKeyAsAPrefix() {
        MailboxAnnotationKey key1 = new MailboxAnnotationKey("/private/comment/tutu");
        MailboxAnnotationKey key2 = new MailboxAnnotationKey("/private/comment/tututata");

        assertThat(key1.isParentOrIsEqual(key2)).isFalse();
    }
}