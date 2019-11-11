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

import static org.apache.james.mailbox.model.MailboxACL.Right.Administer;
import static org.apache.james.mailbox.model.MailboxACL.Right.CreateMailbox;
import static org.apache.james.mailbox.model.MailboxACL.Right.DeleteMailbox;
import static org.apache.james.mailbox.model.MailboxACL.Right.DeleteMessages;
import static org.apache.james.mailbox.model.MailboxACL.Right.Insert;
import static org.apache.james.mailbox.model.MailboxACL.Right.Lookup;
import static org.apache.james.mailbox.model.MailboxACL.Right.PerformExpunge;
import static org.apache.james.mailbox.model.MailboxACL.Right.Post;
import static org.apache.james.mailbox.model.MailboxACL.Right.Read;
import static org.apache.james.mailbox.model.MailboxACL.Right.Write;
import static org.apache.james.mailbox.model.MailboxACL.Right.WriteSeenFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Rfc4314RightsTest {
    
    private Rfc4314Rights aeik;
    private Rfc4314Rights lprs;
    private Rfc4314Rights twx;
    private Rfc4314Rights full;
    private Rfc4314Rights none;
    
    @BeforeEach
    void setUp() throws Exception {
        aeik = Rfc4314Rights.fromSerializedRfc4314Rights("aeik");
        lprs = Rfc4314Rights.fromSerializedRfc4314Rights("lprs");
        twx = Rfc4314Rights.fromSerializedRfc4314Rights("twx");
        full = MailboxACL.FULL_RIGHTS;
        none = MailboxACL.NO_RIGHTS;
    }
    
    @Test
    void newInstanceShouldThrowWhenNullString() throws UnsupportedRightException {
        assertThatThrownBy(() -> Rfc4314Rights.fromSerializedRfc4314Rights((String) null))
            .isInstanceOf(NullPointerException.class);
    }
    
    @Test
    void newInstanceShouldHaveNoRightsWhenEmptyString() throws UnsupportedRightException {
        Rfc4314Rights rights = Rfc4314Rights.fromSerializedRfc4314Rights("");
        assertThat(rights.list()).isEmpty();
    }
    
    @Test
    void containsShouldReturnFalseWhenNotMatching() throws UnsupportedRightException {
        assertThat(aeik.contains('x')).isFalse();
    }
    
    @Test
    void containsShouldReturnTrueWhenMatching() throws UnsupportedRightException {
        assertThat(aeik.contains('e')).isTrue();
    }
    
    @Test
    void exceptShouldRemoveAllWhenChaining() throws UnsupportedRightException {
        assertThat(full.except(aeik).except(lprs).except(twx)).isEqualTo(none);
    }
    
    @Test
    void exceptShouldReturnOriginWhenExceptingNull() throws UnsupportedRightException {
        assertThat(aeik.except(null)).isEqualTo(aeik);
    }
    
    @Test
    void exceptShouldReturnOriginWhenExceptingNonExistent() throws UnsupportedRightException {
        assertThat(aeik.except(lprs)).isEqualTo(aeik);
    }

    @Test
    void rfc4314RightsShouldThrowWhenUnknownFlag() {
        assertThatThrownBy(() -> Rfc4314Rights.fromSerializedRfc4314Rights("z"))
            .isInstanceOf(UnsupportedRightException.class);
    }
    
    @Test
    void exceptShouldReturnOriginWhenExceptingEmpty() throws UnsupportedRightException {
        assertThat(aeik.except(none)).isEqualTo(aeik);
    }
    
    @Test
    void fullRightsShouldContainsAllRights() {
        assertThat(full.list()).containsOnly(
            Administer,
            PerformExpunge,
            Insert,
            CreateMailbox,
            Lookup,
            Post,
            Read,
            WriteSeenFlag,
            DeleteMessages,
            Write,
            DeleteMailbox);
    }
    
    @Test
    void noneRightsShouldContainsNoRights() {
        assertThat(none.list()).isEmpty();
    }
    
    @Test
    void rightsShouldContainsSpecificRightsWhenAEIK() {
        assertThat(aeik.list()).containsOnly(
            Administer,
            PerformExpunge,
            Insert,
            CreateMailbox);
    }
    
    @Test
    void rightsShouldContainsSpecificRightsWhenLPRS() {
        assertThat(lprs.list()).containsOnly(
            Lookup,
            Post,
            Read,
            WriteSeenFlag);
    }
    
    @Test
    void rightsShouldContainsSpecificRightsWhenTWX() {
        assertThat(twx.list()).containsOnly(
            DeleteMessages,
            Write,
            DeleteMailbox);
    }

    @Test
    void getValueShouldReturnSigmaWhenAeik() {
        assertThat(aeik.list()).containsExactly(Administer, PerformExpunge, Insert, CreateMailbox);
    }

    @Test
    void getValueShouldReturnSigmaWhenLprs() {
        assertThat(lprs.list()).containsExactly(Lookup, Post, Read, WriteSeenFlag);
    }

    @Test
    void getValueShouldReturnSigmaWhenTwx() {
        assertThat(twx.list()).containsExactly(DeleteMessages, Write, DeleteMailbox);
    }

    @Test
    void getValueShouldReturnEmptyWhenNone() throws UnsupportedRightException {
        assertThat(Rfc4314Rights.fromSerializedRfc4314Rights("").list()).isEmpty();
    }

    @Test
    void serializeShouldReturnStringWhenAeik() {
        assertThat(aeik.serialize()).isEqualTo("aeik");
    }

    @Test
    void serializeShouldReturnStringWhenLprs() {
        assertThat(lprs.serialize()).isEqualTo("lprs");
    }

    @Test
    void serializeShouldReturnStringWhenTwx() {
        assertThat(twx.serialize()).isEqualTo("twx");
    }
    
    @Test
    void serializeShouldReturnStringWhenAeiklprstwx() {
        assertThat(full.serialize()).isEqualTo("aeiklprstwx");
    }

    @Test
    void serializeShouldReturnEmptyStringWhenEmpty() {
        assertThat(none.serialize()).isEmpty();
    }
    
    @Test
    void unionShouldReturnFullWhenChaining() throws UnsupportedRightException {
        assertThat(aeik.union(lprs).union(twx)).isEqualTo(full);
    }
    
    @Test
    void unionShouldReturnOriginWhenAppliedWithEmpty() throws UnsupportedRightException {
        assertThat(lprs.union(none)).isEqualTo(lprs);
    }
    
    @Test
    void unionShouldThrowWhenAppliedWithNull() {
        assertThatThrownBy(() -> lprs.union(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void containsShouldReturnFalseWhenRightNotPresent() {
        assertThat(lprs.contains(Write)).isFalse();
    }

    @Test
    void containsShouldReturnFalseWhenAtLeastOneRightNotPresent() {
        assertThat(lprs.contains(Lookup, Write)).isFalse();
    }

    @Test
    void containsShouldReturnTrueWhenAllRightsPresent() {
        assertThat(lprs.contains(Lookup, Post)).isTrue();
    }

    @Test
    void containsShouldReturnTrueWhenNonRightsPresent() {
        assertThat(lprs.contains()).isTrue();
    }

    @Test
    void allExceptShouldReturnFullWhenProvidedEmpty() throws UnsupportedRightException {
        assertThat(Rfc4314Rights.allExcept()).isEqualTo(MailboxACL.FULL_RIGHTS);
    }

    @Test
    void allExceptShouldReturnAllButProvidedRight() throws UnsupportedRightException {
        assertThat(Rfc4314Rights.allExcept(Lookup))
            .isEqualTo(new Rfc4314Rights(
                DeleteMessages,
                Insert,
                Read,
                Administer,
                Write,
                WriteSeenFlag,
                PerformExpunge,
                CreateMailbox,
                Post,
                DeleteMailbox));
    }

    @Test
    void allExceptShouldReturnAllButProvidedRights() throws UnsupportedRightException {
        assertThat(Rfc4314Rights.allExcept(Lookup, Read))
            .isEqualTo(new Rfc4314Rights(
                DeleteMessages,
                Insert,
                Administer,
                Write,
                WriteSeenFlag,
                PerformExpunge,
                CreateMailbox,
                Post,
                DeleteMailbox));
    }

    @Test
    void allExceptShouldReturnEmptyWhenProvidedAllRights() throws UnsupportedRightException {
        assertThat(
            Rfc4314Rights.allExcept(
                Lookup,
                Read,
                DeleteMessages,
                Insert,
                Administer,
                Write,
                WriteSeenFlag,
                PerformExpunge,
                CreateMailbox,
                Post,
                DeleteMailbox))
            .isEqualTo(new Rfc4314Rights());
    }
}
