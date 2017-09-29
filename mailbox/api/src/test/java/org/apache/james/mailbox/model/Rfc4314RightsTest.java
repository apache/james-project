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
import org.junit.Before;
import org.junit.Test;

/**
 * @author Peter Palaga
 */
public class Rfc4314RightsTest {
    
    private Rfc4314Rights aeik;
    private Rfc4314Rights lprs;
    private Rfc4314Rights twx;
    private Rfc4314Rights full;
    private Rfc4314Rights none;
    
    @Before
    public void setUp() throws Exception {
        aeik = Rfc4314Rights.fromSerializedRfc4314Rights("aeik");
        lprs = Rfc4314Rights.fromSerializedRfc4314Rights("lprs");
        twx = Rfc4314Rights.fromSerializedRfc4314Rights("twx");
        full = MailboxACL.FULL_RIGHTS;
        none = MailboxACL.NO_RIGHTS;
    }
    
    @Test(expected=NullPointerException.class)
    public void newInstanceShouldThrowWhenNullString() throws UnsupportedRightException {
        Rfc4314Rights.fromSerializedRfc4314Rights((String) null);
    }
    
    @Test
    public void newInstanceShouldHaveNoRightsWhenEmptyString() throws UnsupportedRightException {
        Rfc4314Rights rights = Rfc4314Rights.fromSerializedRfc4314Rights("");
        assertThat(rights.list()).isEmpty();
    }
    
    @Test
    public void containsShouldReturnFalseWhenNotMatching() throws UnsupportedRightException {
        assertThat(aeik.contains('x')).isFalse();
    }
    
    @Test
    public void containsShouldReturnTrueWhenMatching() throws UnsupportedRightException {
        assertThat(aeik.contains('e')).isTrue();
    }
    
    @Test
    public void exceptShouldRemoveAllWhenChaining() throws UnsupportedRightException {
        assertThat(full.except(aeik).except(lprs).except(twx)).isEqualTo(none);
    }
    
    @Test
    public void exceptShouldReturnOriginWhenExceptingNull() throws UnsupportedRightException {
        assertThat(aeik.except(null)).isEqualTo(aeik);
    }
    
    @Test
    public void exceptShouldReturnOriginWhenExceptingNonExistent() throws UnsupportedRightException {
        assertThat(aeik.except(lprs)).isEqualTo(aeik);
    }

    @Test
    public void rfc4314RightsShouldThrowWhenUnknownFlag() throws UnsupportedRightException {
        assertThatThrownBy(() -> Rfc4314Rights.fromSerializedRfc4314Rights("z"))
            .isInstanceOf(UnsupportedRightException.class);
    }
    
    @Test
    public void exceptShouldReturnOriginWhenExceptingEmpty() throws UnsupportedRightException {
        assertThat(aeik.except(none)).isEqualTo(aeik);
    }
    
    @Test
    public void fullRightsShouldContainsAllRights() {
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
    public void noneRightsShouldContainsNoRights() {
        assertThat(none.list()).isEmpty();
    }
    
    @Test
    public void rightsShouldContainsSpecificRightsWhenAEIK() {
        assertThat(aeik.list()).containsOnly(
            Administer,
            PerformExpunge,
            Insert,
            CreateMailbox);
    }
    
    @Test
    public void rightsShouldContainsSpecificRightsWhenLPRS() {
        assertThat(lprs.list()).containsOnly(
            Lookup,
            Post,
            Read,
            WriteSeenFlag);
    }
    
    @Test
    public void rightsShouldContainsSpecificRightsWhenTWX() {
        assertThat(twx.list()).containsOnly(
            DeleteMessages,
            Write,
            DeleteMailbox);
    }

    @Test
    public void getValueShouldReturnSigmaWhenAeik() throws UnsupportedRightException {
        assertThat(aeik.list()).containsExactly(Administer, PerformExpunge, Insert, CreateMailbox);
    }

    @Test
    public void getValueShouldReturnSigmaWhenLprs() throws UnsupportedRightException {
        assertThat(lprs.list()).containsExactly(Lookup, Post, Read, WriteSeenFlag);
    }

    @Test
    public void getValueShouldReturnSigmaWhenTwx() throws UnsupportedRightException {
        assertThat(twx.list()).containsExactly(DeleteMessages, Write, DeleteMailbox);
    }

    @Test
    public void getValueShouldReturnEmptyWhenNone() throws UnsupportedRightException {
        assertThat(Rfc4314Rights.fromSerializedRfc4314Rights("").list()).isEmpty();
    }

    @Test
    public void serializeShouldReturnStringWhenAeik() throws UnsupportedRightException {
        assertThat(aeik.serialize()).isEqualTo("aeik");
    }

    @Test
    public void serializeShouldReturnStringWhenLprs() throws UnsupportedRightException {
        assertThat(lprs.serialize()).isEqualTo("lprs");
    }

    @Test
    public void serializeShouldReturnStringWhenTwx() throws UnsupportedRightException {
        assertThat(twx.serialize()).isEqualTo("twx");
    }
    
    @Test
    public void serializeShouldReturnStringWhenAeiklprstwx() throws UnsupportedRightException {
        assertThat(full.serialize()).isEqualTo("aeiklprstwx");
    }

    @Test
    public void serializeShouldReturnEmptyStringWhenEmpty() throws UnsupportedRightException {
        assertThat(none.serialize()).isEmpty();
    }
    
    @Test
    public void unionShouldReturnFullWhenChaining() throws UnsupportedRightException {
        assertThat(aeik.union(lprs).union(twx)).isEqualTo(full);
    }
    
    @Test
    public void unionShouldReturnOriginWhenAppliedWithEmpty() throws UnsupportedRightException {
        assertThat(lprs.union(none)).isEqualTo(lprs);
    }
    
    @Test
    public void unionShouldThrowWhenAppliedWithNull() throws UnsupportedRightException {
        assertThatThrownBy(() -> lprs.union(null)).isInstanceOf(NullPointerException.class);
    }
}
