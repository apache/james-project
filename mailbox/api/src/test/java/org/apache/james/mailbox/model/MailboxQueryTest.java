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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxQuery.Builder;
import org.junit.Before;
import org.junit.Test;

public class MailboxQueryTest {
    private static final String CURRENT_USER = "user";

    private MailboxPath mailboxPath;
    private MailboxSession mailboxSession;

    @Before
    public void setUp() {
        mailboxPath = new MailboxPath("namespace", "user", "name");
        mailboxSession = new MockMailboxSession("user");
    }

    @Test
    public void buildShouldMatchAllValuesWhenMatchesAll() throws Exception {
        //When
        MailboxQuery actual = MailboxQuery.builder()
                .base(mailboxPath)
                .matchesAll()
                .mailboxSession(mailboxSession)
                .build();
        //Then
        assertThat(actual.isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void buildShouldConstructMailboxPathWhenPrivateUserMailboxes() throws Exception {
        //Given
        MailboxPath expected = MailboxPath.forUser("user", "");
        //When
        MailboxQuery actual = MailboxQuery.builder()
                .username("user")
                .privateMailboxes()
                .mailboxSession(mailboxSession)
                .build();
        //Then
        assertThat(actual.getNamespace()).contains(expected.getNamespace());
        assertThat(actual.getUser()).contains(expected.getUser());
        assertThat(actual.getBaseName()).contains(expected.getName());
    }

    @Test
    public void buildShouldMatchAllValuesWhenPrivateUserMailboxes() throws Exception {
        //Given
        Builder testee = MailboxQuery.builder()
                .username("user")
                .privateMailboxes()
            .mailboxSession(mailboxSession);
        //When
        MailboxQuery actual = testee.build();
        //Then
        assertThat(actual.isExpressionMatch("folder")).isTrue();
    }

    @Test
    public void builderShouldInitFromSessionWhenGiven() throws Exception {
        //Given
        MailboxSession mailboxSession = mock(MailboxSession.class);
        when(mailboxSession.getPathDelimiter()).thenReturn('#');
        User user = mock(User.class);
        when(user.getUserName()).thenReturn("little bobby table");
        when(mailboxSession.getUser()).thenReturn(user);
        // When
        Builder query = MailboxQuery.privateMailboxesBuilder(mailboxSession);
        //Then
        assertThat(query.pathDelimiter).contains('#');
        assertThat(query.username).contains("little bobby table");
    }

    @Test
    public void builderShouldNotThrowWhenNoBaseDefined() throws Exception {
        //Given
        Builder testee = MailboxQuery.builder()
                .expression("abc")
                .mailboxSession(mailboxSession);
        //When
        testee.build();
    }

    @Test(expected=IllegalStateException.class)
    public void builderShouldThrowWhenBaseAndUsernameGiven() throws Exception {
        //Given
        Builder testee = MailboxQuery.builder()
                .base(mailboxPath)
                .username("user");
        //When
        testee.build();
    }

    @Test(expected=IllegalStateException.class)
    public void builderShouldThrowWhenBaseGiven() throws Exception {
        //Given
        Builder testee = MailboxQuery.builder()
                .base(mailboxPath)
                .privateMailboxes();
        //When
        testee.build();
    } 

    @Test(expected=IllegalStateException.class)
    public void builderShouldThrowWhenMissingUsername() throws Exception {
        //Given
        Builder testee = MailboxQuery.builder()
                .privateMailboxes();
        //When
        testee.build();
    }

    @Test
    public void builderShouldUseBaseWhenGiven() throws Exception {
        //When
        MailboxQuery actual = MailboxQuery.builder()
                .base(mailboxPath)
                .mailboxSession(mailboxSession)
                .build();
        //Then
        assertThat(actual.getNamespace()).contains(mailboxPath.getNamespace());
        assertThat(actual.getUser()).contains(mailboxPath.getUser());
        assertThat(actual.getBaseName()).contains(mailboxPath.getName());
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithIdenticalMailboxes() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .base(mailboxPath)
            .mailboxSession(mailboxSession)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(mailboxPath))
            .isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithIdenticalMailboxesWithNullUser() {
        MailboxPath base = new MailboxPath("namespace", null, "name");

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .base(base)
            .mailboxSession(mailboxSession)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(mailboxPath))
            .isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithIdenticalMailboxesWithNullNamespace() {
        MailboxPath mailboxPath = new MailboxPath(null, "user", "name");

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .base(mailboxPath)
            .mailboxSession(mailboxSession)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(mailboxPath))
            .isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithMailboxWithSameNamespaceAndUserWithNullUser() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .base(new MailboxPath("namespace", null, "name"))
            .mailboxSession(mailboxSession)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace", null, "name")))
            .isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithMailboxWithSameNamespaceAndUser() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .base(new MailboxPath("namespace", CURRENT_USER, "name"))
            .mailboxSession(mailboxSession)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace", CURRENT_USER, "name2")))
            .isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseWithDifferentNamespace() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .base(new MailboxPath("namespace", CURRENT_USER, "name"))
            .mailboxSession(mailboxSession)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace2", CURRENT_USER, "name")))
            .isFalse();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseWithDifferentUser() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .base(new MailboxPath("namespace", CURRENT_USER, "name"))
            .mailboxSession(mailboxSession)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace", CURRENT_USER + "2", "name")))
            .isFalse();
    }
    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseWithOneOfTheUserNull() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .base(new MailboxPath("namespace", CURRENT_USER, "name"))
            .mailboxSession(mailboxSession)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace", null, "name")))
            .isFalse();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseWhenDifferentUser() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .base(new MailboxPath("namespace", CURRENT_USER, "name"))
            .mailboxSession(mailboxSession)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace", "other", "name")))
            .isFalse();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseIfNamespaceAreDifferentWithNullUser() {
        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .base(new MailboxPath("namespace", null, "name"))
            .mailboxSession(mailboxSession)
            .build();

        assertThat(mailboxQuery.belongsToRequestedNamespaceAndUser(new MailboxPath("namespace2", null, "name")))
            .isFalse();
    }
}
