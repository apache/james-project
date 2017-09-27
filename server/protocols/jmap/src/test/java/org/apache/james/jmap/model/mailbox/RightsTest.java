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

package org.apache.james.jmap.model.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;

public class RightsTest {

    public static final boolean NEGATIVE = true;

    @Test
    public void rightsShouldMatchBeanContract() {
        EqualsVerifier.forClass(Rights.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void usernameShouldMatchBeanContract() {
        EqualsVerifier.forClass(Rights.Username.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void forCharShouldReturnRightWhenA() {
        assertThat(Rights.Right.forChar('a'))
            .isEqualTo(Rights.Right.Administer);
    }

    @Test
    public void forCharShouldReturnRightWhenE() {
        assertThat(Rights.Right.forChar('e'))
            .isEqualTo(Rights.Right.Expunge);
    }

    @Test
    public void forCharShouldReturnRightWhenI() {
        assertThat(Rights.Right.forChar('i'))
            .isEqualTo(Rights.Right.Insert);
    }

    @Test
    public void forCharShouldReturnRightWhenL() {
        assertThat(Rights.Right.forChar('l'))
            .isEqualTo(Rights.Right.Lookup);
    }

    @Test
    public void forCharShouldReturnRightWhenR() {
        assertThat(Rights.Right.forChar('r'))
            .isEqualTo(Rights.Right.Read);
    }

    @Test
    public void forCharShouldReturnRightWhenW() {
        assertThat(Rights.Right.forChar('w'))
            .isEqualTo(Rights.Right.Write);
    }

    @Test
    public void forCharShouldReturnRightWhenT() {
        assertThat(Rights.Right.forChar('t'))
            .isEqualTo(Rights.Right.DeleteMessages);
    }

    @Test
    public void forCharShouldThrowOnUnsupportedRight() {
        assertThatThrownBy(() -> Rights.Right.forChar('k'))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromACLShouldFilterOutGroups() throws Exception {
        MailboxACL acl = new MailboxACL(ImmutableMap.of(
            EntryKey.createGroup("group"), Rfc4314Rights.fromSerializedRfc4314Rights("aet")));

        assertThat(Rights.fromACL(acl))
            .isEqualTo(Rights.EMPTY);
    }

    @Test
    public void fromACLShouldFilterNegatedUsers() throws Exception {
        MailboxACL acl = new MailboxACL(ImmutableMap.of(
            EntryKey.createUser("user", NEGATIVE), Rfc4314Rights.fromSerializedRfc4314Rights("aet")));

        assertThat(Rights.fromACL(acl))
            .isEqualTo(Rights.EMPTY);
    }

    @Test
    public void fromACLShouldAcceptUsers() throws Exception {
        MailboxACL acl = new MailboxACL(ImmutableMap.of(
            EntryKey.createUser("user"), Rfc4314Rights.fromSerializedRfc4314Rights("aet")));

        assertThat(Rights.fromACL(acl))
            .isEqualTo(Rights.builder()
                .delegateTo(new Rights.Username("user"), Rights.Right.Administer, Rights.Right.Expunge, Rights.Right.DeleteMessages)
                .build());
    }

    @Test
    public void fromACLShouldFilterOutUnknownRights() throws Exception {
        MailboxACL acl = new MailboxACL(ImmutableMap.of(
            EntryKey.createUser("user"), Rfc4314Rights.fromSerializedRfc4314Rights("aetpk")));

        assertThat(Rights.fromACL(acl))
            .isEqualTo(Rights.builder()
                .delegateTo(new Rights.Username("user"), Rights.Right.Administer, Rights.Right.Expunge, Rights.Right.DeleteMessages)
                .build());
    }

    @Test
    public void toMailboxAclShouldReturnEmptyAclWhenEmpty() {
        Rights rights = Rights.EMPTY;

        assertThat(rights.toMailboxAcl())
            .isEqualTo(new MailboxACL());
    }

    @Test
    public void toMailboxAclShouldReturnAclConversion() throws Exception {
        String user1 = "user1";
        String user2 = "user2";
        Rights rights = Rights.builder()
            .delegateTo(new Rights.Username(user1), Rights.Right.Administer, Rights.Right.DeleteMessages)
            .delegateTo(new Rights.Username(user2), Rights.Right.Expunge, Rights.Right.Lookup)
            .build();

        assertThat(rights.toMailboxAcl())
            .isEqualTo(new MailboxACL(
                new Entry(user1, MailboxACL.Right.Administer, MailboxACL.Right.DeleteMessages),
                new Entry(user2, MailboxACL.Right.PerformExpunge, MailboxACL.Right.Lookup)));
    }

}