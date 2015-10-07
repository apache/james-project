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

import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLRights;
import org.apache.james.mailbox.model.SimpleMailboxACL.Rfc4314Rights;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Peter Palaga
 */
public class Rfc4314RightsTest {
    
    private Rfc4314Rights aeik;
    private Rfc4314Rights lprs;
    private Rfc4314Rights twx;
    private MailboxACLRights full;
    private MailboxACLRights none;
    
    @Before
    public void setUp() throws Exception {
        aeik = new SimpleMailboxACL.Rfc4314Rights("aeik");
        lprs = new SimpleMailboxACL.Rfc4314Rights("lprs");
        twx = new SimpleMailboxACL.Rfc4314Rights("twx");
        full = SimpleMailboxACL.FULL_RIGHTS;
        none = SimpleMailboxACL.NO_RIGHTS;
    }
    
    @Test(expected=NullPointerException.class)
    public void newInstanceShouldThrowWhenNullString() throws UnsupportedRightException {
        new SimpleMailboxACL.Rfc4314Rights((String) null);
    }
    
    @Test
    public void newInstanceShouldHaveNoRightsWhenEmptyString() throws UnsupportedRightException {
        Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights("");
        assertThat(rights).isEmpty();
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

    @Test(expected=UnsupportedRightException.class)
    public void rfc4314RightsShouldThrowWhenUnknownFlag() throws UnsupportedRightException {
        new SimpleMailboxACL.Rfc4314Rights("z");
    }
    
    @Test
    public void exceptShouldReturnOriginWhenExceptingEmpty() throws UnsupportedRightException {
        assertThat(aeik.except(none)).isEqualTo(aeik);
    }
    
    @Test
    public void fullRightsShouldContainsAllRights() {
        assertThat(full).containsOnly(
                Rfc4314Rights.a_Administer_RIGHT, 
                Rfc4314Rights.e_PerformExpunge_RIGHT, 
                Rfc4314Rights.i_Insert_RIGHT, 
                Rfc4314Rights.k_CreateMailbox_RIGHT,
                Rfc4314Rights.l_Lookup_RIGHT,
                Rfc4314Rights.p_Post_RIGHT,
                Rfc4314Rights.r_Read_RIGHT,
                Rfc4314Rights.s_WriteSeenFlag_RIGHT,
                Rfc4314Rights.t_DeleteMessages_RIGHT,
                Rfc4314Rights.w_Write_RIGHT,
                Rfc4314Rights.x_DeleteMailbox_RIGHT);
    }
    
    @Test
    public void noneRightsShouldContainsNoRights() {
        assertThat(none).isEmpty();
    }
    
    @Test
    public void rightsShouldContainsSpecificRightsWhenAEIK() {
        assertThat(aeik).containsOnly(
                Rfc4314Rights.a_Administer_RIGHT, 
                Rfc4314Rights.e_PerformExpunge_RIGHT, 
                Rfc4314Rights.i_Insert_RIGHT, 
                Rfc4314Rights.k_CreateMailbox_RIGHT);
    }
    
    @Test
    public void rightsShouldContainsSpecificRightsWhenLPRS() {
        assertThat(lprs).containsOnly(
                Rfc4314Rights.l_Lookup_RIGHT, 
                Rfc4314Rights.p_Post_RIGHT, 
                Rfc4314Rights.r_Read_RIGHT, 
                Rfc4314Rights.s_WriteSeenFlag_RIGHT);
    }
    
    @Test
    public void rightsShouldContainsSpecificRightsWhenTWX() {
        assertThat(twx).containsOnly(
                Rfc4314Rights.t_DeleteMessages_RIGHT, 
                Rfc4314Rights.w_Write_RIGHT, 
                Rfc4314Rights.x_DeleteMailbox_RIGHT);
    }

    @Test
    public void getValueShouldReturnSigmaWhenAeik() throws UnsupportedRightException {
        assertThat(aeik.getValue()).isEqualTo(Rfc4314Rights.a_Administer_MASK | Rfc4314Rights.e_PerformExpunge_MASK | Rfc4314Rights.i_Insert_MASK | Rfc4314Rights.k_CreateMailbox_MASK);
    }

    @Test
    public void getValueShouldReturnSigmaWhenLprs() throws UnsupportedRightException {
        assertThat(lprs.getValue()).isEqualTo(Rfc4314Rights.l_Lookup_MASK | Rfc4314Rights.p_Post_MASK | Rfc4314Rights.s_WriteSeenFlag_MASK | Rfc4314Rights.r_Read_MASK);
    }

    @Test
    public void getValueShouldReturnSigmaWhenTwx() throws UnsupportedRightException {
        assertThat(twx.getValue()).isEqualTo(Rfc4314Rights.t_DeleteMessages_MASK | Rfc4314Rights.w_Write_MASK | Rfc4314Rights.x_DeleteMailbox_MASK);
    }

    @Test
    public void getValueShouldReturnEmptyWhenNone() throws UnsupportedRightException {
        assertThat(new SimpleMailboxACL.Rfc4314Rights("").getValue()).isEqualTo(Rfc4314Rights.EMPTY_MASK);
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
    
    @Test(expected=NullPointerException.class)
    public void unionShouldThrowWhenAppliedWithNull() throws UnsupportedRightException {
        assertThat(lprs.union(null)).isEqualTo(lprs);
    }
}
