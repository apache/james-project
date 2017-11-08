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

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MailboxPathTest {

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxPath.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void getHierarchyLevelsShouldBeOrdered() {
        assertThat(MailboxPath.forUser("user", "inbox.folder.subfolder")
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser("user", "inbox"),
                MailboxPath.forUser("user", "inbox.folder"),
                MailboxPath.forUser("user", "inbox.folder.subfolder"));
    }

    @Test
    public void getHierarchyLevelsShouldReturnPathWhenOneLevel() {
        assertThat(MailboxPath.forUser("user", "inbox")
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser("user", "inbox"));
    }

    @Test
    public void getHierarchyLevelsShouldReturnPathWhenEmptyName() {
        assertThat(MailboxPath.forUser("user", "")
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser("user", ""));
    }

    @Test
    public void getHierarchyLevelsShouldReturnPathWhenNullName() {
        assertThat(MailboxPath.forUser("user", null)
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser("user", null));
    }
}
