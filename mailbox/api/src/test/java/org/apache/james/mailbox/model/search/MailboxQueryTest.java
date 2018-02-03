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

package org.apache.james.mailbox.model.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery.Builder;
import org.junit.Before;
import org.junit.Test;

public class MailboxQueryTest {
    private static final String CURRENT_USER = "user";

    private MailboxPath mailboxPath;

    @Before
    public void setUp() {
        mailboxPath = new MailboxPath("namespace", "user", "name");
    }

    @Test
    public void buildShouldMatchAllValuesWhenMatchesAll() throws Exception {

        MailboxQuery actual = MailboxQuery.builder()
                .userAndNamespaceFrom(mailboxPath)
                .matchesAllMailboxNames()
                .build();

        assertThat(actual.isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void buildShouldConstructMailboxPathWhenPrivateUserMailboxes() throws Exception {
        MailboxPath expected = MailboxPath.forUser("user", "");

        MailboxQuery actual = MailboxQuery.builder()
                .username("user")
                .privateNamespace()
                .build();

        assertThat(actual.getNamespace()).contains(expected.getNamespace());
        assertThat(actual.getUser()).contains(expected.getUser());
        assertThat(actual.getMailboxNameExpression()).isEqualTo(Wildcard.INSTANCE);
    }

    @Test
    public void buildShouldMatchAllValuesWhenPrivateUserMailboxes() throws Exception {
        Builder testee = MailboxQuery.builder()
                .username("user")
                .privateNamespace();

        MailboxQuery actual = testee.build();

        assertThat(actual.isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void builderShouldNotThrowWhenNoBaseDefined() throws Exception {
        Builder testee = MailboxQuery.builder()
                .expression(new ExactName("abc"));

        testee.build();
    }

    @Test(expected = IllegalStateException.class)
    public void builderShouldThrowWhenBaseAndUsernameGiven() throws Exception {
        Builder testee = MailboxQuery.builder()
                .userAndNamespaceFrom(mailboxPath)
                .username("user");

        testee.build();
    }

    @Test(expected = IllegalStateException.class)
    public void builderShouldThrowWhenBaseGiven() throws Exception {
        Builder testee = MailboxQuery.builder()
                .userAndNamespaceFrom(mailboxPath)
                .privateNamespace();

        testee.build();
    } 

    @Test
    public void builderShouldNotThrowWhenMissingUsername() throws Exception {
        Builder testee = MailboxQuery.builder()
                .privateNamespace();

        testee.build();
    }

    @Test
    public void builderShouldUseBaseWhenGiven() throws Exception {

        MailboxQuery actual = MailboxQuery.builder()
                .userAndNamespaceFrom(mailboxPath)
                .build();

        assertThat(actual.getNamespace()).contains(mailboxPath.getNamespace());
        assertThat(actual.getUser()).contains(mailboxPath.getUser());
        assertThat(actual.getMailboxNameExpression()).isEqualTo(Wildcard.INSTANCE);
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithIdenticalMailboxes() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(mailboxPath)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(mailboxPath))
            .isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithIdenticalMailboxesWithNullUser() {
        MailboxPath base = new MailboxPath("namespace", null, "name");

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(base)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(mailboxPath))
            .isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithIdenticalMailboxesWithNullNamespace() {
        MailboxPath mailboxPath = new MailboxPath(null, "user", "name");

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(mailboxPath)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(mailboxPath))
            .isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithMailboxWithSameNamespaceAndUserWithNullUser() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(new MailboxPath("namespace", null, "name"))
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace", null, "name")))
            .isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithMailboxWithSameNamespaceAndUser() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(new MailboxPath("namespace", CURRENT_USER, "name"))
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace", CURRENT_USER, "name2")))
            .isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseWithDifferentNamespace() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(new MailboxPath("namespace", CURRENT_USER, "name"))
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace2", CURRENT_USER, "name")))
            .isFalse();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseWithDifferentUser() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(new MailboxPath("namespace", CURRENT_USER, "name"))
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace", CURRENT_USER + "2", "name")))
            .isFalse();
    }
    
    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseWithOneOfTheUserNull() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(new MailboxPath("namespace", CURRENT_USER, "name"))
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace", null, "name")))
            .isFalse();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseWhenDifferentUser() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(new MailboxPath("namespace", CURRENT_USER, "name"))
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace", "other", "name")))
            .isFalse();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseIfNamespaceAreDifferentWithNullUser() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(new MailboxPath("namespace", null, "name"))
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace2", null, "name")))
            .isFalse();
    }
}
