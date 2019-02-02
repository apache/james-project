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
package org.apache.james.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Optional;

import org.apache.james.mailbox.Role;
import org.junit.Test;

public class RoleTest {

    @Test
    public void fromShouldReturnEmptyWhenUnknownValue() {
        assertThat(Role.from("jjjj")).isEqualTo(Optional.empty());
    }

    @Test
    public void fromShouldReturnSomethingWhenXPrefixedRole() {
        assertThat(Role.from("x-client-specific-role")).isEqualTo(Optional.of(new Role("x-client-specific-role")));
    }

    @Test
    public void isSystemRoleShouldReturnFalseWhenXPrefixedRole() {
        Role role = Role.from("x-client-specific-role").get();
        assertThat(role.isSystemRole()).isFalse();
    }

    @Test
    public void fromShouldReturnInboxWhenContainsUppercaseValueInTurkish() {
        Locale previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            assertThat(Role.from("InBox")).isEqualTo(Optional.of(Role.INBOX));
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    public void isSystemRoleShouldBeTrueWhenInbox() {
        assertThat(Role.INBOX.isSystemRole()).isTrue();
    }

    @Test
    public void isSystemRoleShouldBeTrueWhenArchive() {
        assertThat(Role.ARCHIVE.isSystemRole()).isTrue();
    }

    @Test
    public void isSystemRoleShouldBeTrueWhenDrafts() {
        assertThat(Role.DRAFTS.isSystemRole()).isTrue();
    }

    @Test
    public void isSystemRoleShouldBeTrueWhenOutbox() {
        assertThat(Role.OUTBOX.isSystemRole()).isTrue();
    }

    @Test
    public void isSystemRoleShouldBeTrueWhenSent() {
        assertThat(Role.SENT.isSystemRole()).isTrue();
    }

    @Test
    public void isSystemRoleShouldBeTrueWhenTrash() {
        assertThat(Role.TRASH.isSystemRole()).isTrue();
    }

    @Test
    public void isSystemRoleShouldBeTrueWhenSpam() {
        assertThat(Role.SPAM.isSystemRole()).isTrue();
    }

    @Test
    public void isSystemRoleShouldBeTrueWhenTemplates() {
        assertThat(Role.TEMPLATES.isSystemRole()).isTrue();
    }

    @Test
    public void isSystemRoleShouldBeFalseWhenUserDefinedRole() {
        Role userRole = Role.from(Role.USER_DEFINED_ROLE_PREFIX + "myRole").get();
        assertThat(userRole.isSystemRole()).isFalse();
    }

    @Test
    public void theINBOXMailboxNameShouldBeASystemMailbox() {
        Role role = Role.from("INBOX").get();
        assertThat(role.isSystemRole()).isTrue();
    }

    @Test
    public void theInBoXMailboxNameShouldBeASystemMailbox() {
        Role role = Role.from("InBoX").get();
        assertThat(role.isSystemRole()).isTrue();
    }

    @Test
    public void theDraftsMailboxNameShouldBeASystemMailbox() {
        Role role = Role.from("Drafts").get();
        assertThat(role.isSystemRole()).isTrue();
    }

    @Test
    public void theDrAfTsMailboxNameShouldNotBeASystemMailbox() {
        Optional<Role> role = Role.from("DrAfTs");
        assertThat(role).isEmpty();
    }

    @Test
    public void theOutboxMailboxNameShouldBeASystemMailbox() {
        Role role = Role.from("Outbox").get();
        assertThat(role.isSystemRole()).isTrue();
    }

    @Test
    public void theOuTbOxMailboxNameShouldNotBeASystemMailbox() {
        Optional<Role> role = Role.from("OuTbOx");
        assertThat(role).isEmpty();
    }

    @Test
    public void theSentMailboxNameShouldBeASystemMailbox() {
        Role role = Role.from("Sent").get();
        assertThat(role.isSystemRole()).isTrue();
    }

    @Test
    public void theSeNtMailboxNameShouldNotBeASystemMailbox() {
        Optional<Role> role = Role.from("SeNt");
        assertThat(role).isEmpty();
    }

    @Test
    public void theTrashMailboxNameShouldBeASystemMailbox() {
        Role role = Role.from("Trash").get();
        assertThat(role.isSystemRole()).isTrue();
    }

    @Test
    public void theTrAsHMailboxNameShouldNotBeASystemMailbox() {
        Optional<Role> role = Role.from("TrAsH");
        assertThat(role).isEmpty();
    }
}