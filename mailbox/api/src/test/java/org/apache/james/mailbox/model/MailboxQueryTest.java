/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.mailbox.model;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MailboxQueryTest {

    MailboxPath path;

    @Before
    public void setUp() {
        path = new MailboxPath("namespace", "user", "name");
    }

    @Test
    public void simpleMailboxQueryShouldMatchItsValue() {
        assertThat(new MailboxQuery(path, "folder", ':').isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void simpleMailboxQueryShouldNotMatchChildFolder() {
        assertThat(new MailboxQuery(path, "folder", ':').isExpressionMatch("folder:123")).isFalse();
    }

    @Test
    public void simpleMailboxQueryShouldNotMatchFolderWithAnExpendedName() {
        assertThat(new MailboxQuery(path, "folder", ':').isExpressionMatch("folder123")).isFalse();
    }

    @Test
    public void freeWildcardQueryShouldMatchItsValue() {
        assertThat(new MailboxQuery(path, "folder*", ':').isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void freeWildcardQueryShouldMatchChildFolder() {
        assertThat(new MailboxQuery(path, "folder*", ':').isExpressionMatch("folder:123")).isTrue();
    }

    @Test
    public void freeWildcardQueryShouldMatchFolderWithAnExpendedName() {
        assertThat(new MailboxQuery(path, "folder*", ':').isExpressionMatch("folder123")).isTrue();
    }

    @Test
    public void localWildcardWithOtherCharactersQueryShouldNotMatchItsValue() {
        assertThat(new MailboxQuery(path, "folder%3", ':').isExpressionMatch("folder")).isFalse();
    }

    @Test
    public void localWildcardWithOtherCharactersQueryShouldNotMatchChildFolder() {
        assertThat(new MailboxQuery(path, "folder%3", ':').isExpressionMatch("folder:123")).isFalse();
    }

    @Test
    public void localWildcardWithOtherCharactersQueryShouldMatchFolderWithAnExpendedName() {
        assertThat(new MailboxQuery(path, "folder%3", ':').isExpressionMatch("folder123")).isTrue();
    }

    @Test
    public void localWildcardWithOtherCharactersQueryShouldMatchFolderWithRegexSpecialCharacter() {
        assertThat(new MailboxQuery(path, "folder^$!)(%3", ':').isExpressionMatch("folder^$!)(123")).isTrue();
    }

    @Test
    public void emptyMailboxQueryShouldMatchItsValue() {
        assertThat(new MailboxQuery(path, "", ':').isExpressionMatch("")).isTrue();
    }

    @Test
    public void emptyMailboxQueryShouldNotMatchChildFolder() {
        assertThat(new MailboxQuery(path, "", ':').isExpressionMatch(":123")).isFalse();
    }

    @Test
    public void emptyMailboxQueryShouldNotMatchFolderWithAnOtherName() {
        assertThat(new MailboxQuery(path, "", ':').isExpressionMatch("folder")).isFalse();
    }

    @Test
    public void freeWildcardAloneMailboxQueryShouldMatchAnyValue() {
        assertThat(new MailboxQuery(path, "*", ':').isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void freeWildcardAloneMailboxQueryShouldMatchChild() {
        assertThat(new MailboxQuery(path, "*", ':').isExpressionMatch("folder:123")).isTrue();
    }

    @Test
    public void localWildcardAloneMailboxQueryShouldMatchAnyValue() {
        assertThat(new MailboxQuery(path, "%", ':').isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void localWildcardAloneMailboxQueryShouldNotMatchChild() {
        assertThat(new MailboxQuery(path, "%", ':').isExpressionMatch("folder:123")).isFalse();
    }

    @Test
    public void regexInjectionShouldNotBePossibleUsingEndOfQuote() {
        assertThat(new MailboxQuery(path, "\\Efo.", ':').isExpressionMatch("\\Efol")).isFalse();
        assertThat(new MailboxQuery(path, "\\Efo.", ':').isExpressionMatch("\\Efo.")).isTrue();
    }

    @Test
    public void regexInjectionShouldNotBePossibleUsingBeginOfQuote() {
        assertThat(new MailboxQuery(path, "\\Qfo.", ':').isExpressionMatch("\\Qfol")).isFalse();
        assertThat(new MailboxQuery(path, "\\Qfo.", ':').isExpressionMatch("\\Qfo.")).isTrue();
    }

    @Test
    public void nullMailboxQueryShouldNotMathAnything() {
        assertThat(new MailboxQuery(path, null, ':').isExpressionMatch("folder")).isFalse();
    }

    @Test
    public void freeWildcardAreNotEscaped() {
        assertThat(new MailboxQuery(path, "folder\\*", ':').isExpressionMatch("folder\\123")).isTrue();
    }

    @Test
    public void localWildcardAreNotEscaped() {
        assertThat(new MailboxQuery(path, "folder\\%", ':').isExpressionMatch("folder\\123")).isTrue();
    }

    @Test
    public void simpleMailboxQueryShouldMatchItsValueWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder", '.').isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void simpleMailboxQueryShouldNotMatchChildFolderWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder", '.').isExpressionMatch("folder.123")).isFalse();
    }

    @Test
    public void simpleMailboxQueryShouldNotMatchFolderWithAnExpendedNameWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder", '.').isExpressionMatch("folder123")).isFalse();
    }

    @Test
    public void freeWildcardQueryShouldMatchItsValueWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder*", '.').isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void freeWildcardQueryShouldMatchChildFolderWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder*", '.').isExpressionMatch("folder.123")).isTrue();
    }

    @Test
    public void freeWildcardQueryShouldMatchFolderWithAnExpendedNameWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder*", '.').isExpressionMatch("folder123")).isTrue();
    }

    @Test
    public void localWildcardWithOtherCharactersQueryShouldNotMatchItsValueWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder%3", '.').isExpressionMatch("folder")).isFalse();
    }

    @Test
    public void localWildcardWithOtherCharactersQueryShouldNotMatchChildFolderWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder%3", '.').isExpressionMatch("folder.123")).isFalse();
    }

    @Test
    public void localWildcardWithOtherCharactersQueryShouldMatchFolderWithAnExpendedNameWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder%3", '.').isExpressionMatch("folder123")).isTrue();
    }

    @Test
    public void localWildcardWithOtherCharactersQueryShouldMatchFolderWithRegexSpecialCharacterWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder^$!)(%3", '.').isExpressionMatch("folder^$!)(123")).isTrue();
    }

    @Test
    public void emptyMailboxQueryShouldMatchItsValueWithDotSeparator() {
        assertThat(new MailboxQuery(path, "", '.').isExpressionMatch("")).isTrue();
    }

    @Test
    public void emptyMailboxQueryShouldNotMatchChildFolderWithDotSeparator() {
        assertThat(new MailboxQuery(path, "", '.').isExpressionMatch(".123")).isFalse();
    }

    @Test
    public void emptyMailboxQueryShouldNotMatchFolderWithAnOtherNameWithDotSeparator() {
        assertThat(new MailboxQuery(path, "", '.').isExpressionMatch("folder")).isFalse();
    }

    @Test
    public void freeWildcardAloneMailboxQueryShouldMatchAnyValueWithDotSeparator() {
        assertThat(new MailboxQuery(path, "*", '.').isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void freeWildcardAloneMailboxQueryShouldMatchChildWithDotSeparator() {
        assertThat(new MailboxQuery(path, "*", '.').isExpressionMatch("folder.123")).isTrue();
    }

    @Test
    public void localWildcardAloneMailboxQueryShouldMatchAnyValueWithDotSeparator() {
        assertThat(new MailboxQuery(path, "%", '.').isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void localWildcardAloneMailboxQueryShouldNotMatchChildWithDotSeparator() {
        assertThat(new MailboxQuery(path, "%", '.').isExpressionMatch("folder.123")).isFalse();
    }

    @Test
    public void regexInjectionShouldNotBePossibleUsingEndOfQuoteWithDotSeparator() {
        assertThat(new MailboxQuery(path, "\\Efo?", '.').isExpressionMatch("\\Efol")).isFalse();
        assertThat(new MailboxQuery(path, "\\Efo?", '.').isExpressionMatch("\\Efo?")).isTrue();
    }

    @Test
    public void regexInjectionShouldNotBePossibleUsingBeginOfQuoteWithDotSeparator() {
        assertThat(new MailboxQuery(path, "\\Qfo?", '.').isExpressionMatch("\\Qfol")).isFalse();
        assertThat(new MailboxQuery(path, "\\Qfo?", '.').isExpressionMatch("\\Qfo?")).isTrue();
    }

    @Test
    public void nullMailboxQueryShouldNotMathAnythingWithDotSeparator() {
        assertThat(new MailboxQuery(path, null, '.').isExpressionMatch("folder")).isFalse();
    }

    @Test
    public void freeWildcardAreNotEscapedWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder\\*", '.').isExpressionMatch("folder\\123")).isTrue();
    }

    @Test
    public void localWildcardAreNotEscapedWithDotSeparator() {
        assertThat(new MailboxQuery(path, "folder\\%", '.').isExpressionMatch("folder\\123")).isTrue();
    }

}
