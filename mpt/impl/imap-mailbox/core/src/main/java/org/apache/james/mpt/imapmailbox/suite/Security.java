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

package org.apache.james.mpt.imapmailbox.suite;

import java.util.Locale;

import javax.inject.Inject;

import org.apache.james.mpt.api.HostSystem;
import org.apache.james.mpt.imapmailbox.suite.base.BaseImapProtocol;
import org.junit.Test;

public class Security extends BaseImapProtocol {

    @Inject
    private static HostSystem system;
    
    public Security() throws Exception {
        super(system);
    }

    @Test
    public void accessingOtherPeopleNamespaceShouldBeDenied() throws Exception {
        scriptTest("SharedMailbox", Locale.US);
    }

    @Test
    public void testLoginThreeStrikesUS() throws Exception {
        scriptTest("LoginThreeStrikes", Locale.US);
    }

    @Test
    public void testLoginThreeStrikesKOREA() throws Exception {
        scriptTest("LoginThreeStrikes", Locale.KOREA);
    }

    @Test
    public void testLoginThreeStrikesITALY() throws Exception {
        scriptTest("LoginThreeStrikes", Locale.ITALY);
    }

    @Test
    public void testBadTagUS() throws Exception {
        scriptTest("BadTag", Locale.US);
    }

    @Test
    public void testBadTagKOREA() throws Exception {
        scriptTest("BadTag", Locale.KOREA);
    }

    @Test
    public void testBadTagITALY() throws Exception {
        scriptTest("BadTag", Locale.ITALY);
    }

    @Test
    public void testNoTagUS() throws Exception {
        scriptTest("NoTag", Locale.US);
    }

    @Test
    public void testNoTagKOREA() throws Exception {
        scriptTest("NoTag", Locale.KOREA);
    }

    @Test
    public void testNoTagITALY() throws Exception {
        scriptTest("NoTag", Locale.ITALY);
    }

    @Test
    public void testIllegalTagUS() throws Exception {
        scriptTest("IllegalTag", Locale.US);
    }

    @Test
    public void testIllegalTagKOREA() throws Exception {
        scriptTest("IllegalTag", Locale.KOREA);
    }

    @Test
    public void testIllegalTagITALY() throws Exception {
        scriptTest("IllegalTag", Locale.ITALY);
    }

    @Test
    public void testJustTagUS() throws Exception {
        scriptTest("JustTag", Locale.US);
    }

    @Test
    public void testJustTagKOREA() throws Exception {
        scriptTest("JustTag", Locale.KOREA);
    }

    @Test
    public void testJustTagITALY() throws Exception {
        scriptTest("JustTag", Locale.ITALY);
    }

    @Test
    public void testNoCommandUS() throws Exception {
        scriptTest("NoCommand", Locale.US);
    }

    @Test
    public void testNoCommandKOREA() throws Exception {
        scriptTest("NoCommand", Locale.KOREA);
    }

    @Test
    public void testNoCommandITALY() throws Exception {
        scriptTest("NoCommand", Locale.ITALY);
    }

    @Test
    public void testBogusCommandUS() throws Exception {
        scriptTest("BogusCommand", Locale.US);
    }

    @Test
    public void testBogusCommandKOREA() throws Exception {
        scriptTest("BogusCommand", Locale.KOREA);
    }

    @Test
    public void testNoBogusITALY() throws Exception {
        scriptTest("BogusCommand", Locale.ITALY);
    }
}
