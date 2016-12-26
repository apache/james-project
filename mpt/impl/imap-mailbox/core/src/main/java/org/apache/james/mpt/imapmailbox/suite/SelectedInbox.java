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
import org.apache.james.mpt.imapmailbox.suite.base.BaseSelectedInbox;
import org.junit.Test;

public class SelectedInbox extends BaseSelectedInbox {

    @Inject
    private static HostSystem system;
    
    public SelectedInbox() throws Exception {
        super(system);
    }

    @Test
    public void testValidNonAuthenticatedUS() throws Exception {
        scriptTest("ValidNonAuthenticated", Locale.US);
    }

    @Test
    public void testCapabilityUS() throws Exception {
        scriptTest("Capability", Locale.US);
    }

    @Test
    public void testNoopUS() throws Exception {
        scriptTest("Noop", Locale.US);
    }

    @Test
    public void testLogoutUS() throws Exception {
        scriptTest("Logout", Locale.US);
    }

    @Test
    public void testCreateUS() throws Exception {
        scriptTest("Create", Locale.US);
    }

    @Test
    public void testWithLongMailboxNameUS() throws Exception {
        scriptTest("CreateWithLongName", Locale.US);
    }

    @Test
    public void testExamineEmptyUS() throws Exception {
        scriptTest("ExamineEmpty", Locale.US);
    }

    @Test
    public void testSelectEmptyUS() throws Exception {
        scriptTest("SelectEmpty", Locale.US);
    }

    @Test
    public void testListNamespaceUS() throws Exception {
        scriptTest("ListNamespace", Locale.US);
    }

    @Test
    public void testListMailboxesUS() throws Exception {
        scriptTest("ListMailboxes", Locale.US);
    }

    @Test
    public void testStatusUS() throws Exception {
        scriptTest("Status", Locale.US);
    }

    @Test
    public void testStringArgsUS() throws Exception {
        scriptTest("StringArgs", Locale.US);
    }

    @Test
    public void testSubscribeUS() throws Exception {
        scriptTest("Subscribe", Locale.US);
    }

    @Test
    public void testAppendUS() throws Exception {
        scriptTest("Append", Locale.US);
    }

    @Test
    public void testDeleteUS() throws Exception {
        scriptTest("Delete", Locale.US);
    }

    @Test
    public void testValidNonAuthenticatedITALY() throws Exception {
        scriptTest("ValidNonAuthenticated", Locale.ITALY);
    }

    @Test
    public void testCapabilityITALY() throws Exception {
        scriptTest("Capability", Locale.ITALY);
    }

    @Test
    public void testNoopITALY() throws Exception {
        scriptTest("Noop", Locale.ITALY);
    }

    @Test
    public void testLogoutITALY() throws Exception {
        scriptTest("Logout", Locale.ITALY);
    }

    @Test
    public void testCreateITALY() throws Exception {
        scriptTest("Create", Locale.ITALY);
    }
    
    @Test
    public void testExamineEmptyITALY() throws Exception {
        scriptTest("ExamineEmpty", Locale.ITALY);
    }

    @Test
    public void testSelectEmptyITALY() throws Exception {
        scriptTest("SelectEmpty", Locale.ITALY);
    }

    @Test
    public void testListNamespaceITALY() throws Exception {
        scriptTest("ListNamespace", Locale.ITALY);
    }

    @Test
    public void testListMailboxesITALY() throws Exception {
        scriptTest("ListMailboxes", Locale.ITALY);
    }

    @Test
    public void testStatusITALY() throws Exception {
        scriptTest("Status", Locale.ITALY);
    }

    @Test
    public void testStringArgsITALY() throws Exception {
        scriptTest("StringArgs", Locale.ITALY);
    }

    @Test
    public void testSubscribeITALY() throws Exception {
        scriptTest("Subscribe", Locale.ITALY);
    }

    @Test
    public void testAppendITALY() throws Exception {
        scriptTest("Append", Locale.ITALY);
    }

    @Test
    public void testDeleteITALY() throws Exception {
        scriptTest("Delete", Locale.ITALY);
    }

    @Test
    public void testValidNonAuthenticatedKOREA() throws Exception {
        scriptTest("ValidNonAuthenticated", Locale.KOREA);
    }

    @Test
    public void testCapabilityKOREA() throws Exception {
        scriptTest("Capability", Locale.KOREA);
    }

    @Test
    public void testNoopKOREA() throws Exception {
        scriptTest("Noop", Locale.KOREA);
    }

    @Test
    public void testLogoutKOREA() throws Exception {
        scriptTest("Logout", Locale.KOREA);
    }

    @Test
    public void testCreateKOREA() throws Exception {
        scriptTest("Create", Locale.KOREA);
    }

    @Test
    public void testExamineEmptyKOREA() throws Exception {
        scriptTest("ExamineEmpty", Locale.KOREA);
    }

    @Test
    public void testSelectEmptyKOREA() throws Exception {
        scriptTest("SelectEmpty", Locale.KOREA);
    }

    @Test
    public void testListNamespaceKOREA() throws Exception {
        scriptTest("ListNamespace", Locale.KOREA);
    }

    @Test
    public void testListMailboxesKOREA() throws Exception {
        scriptTest("ListMailboxes", Locale.KOREA);
    }

    @Test
    public void testStatusKOREA() throws Exception {
        scriptTest("Status", Locale.KOREA);
    }

    @Test
    public void testStringArgsKOREA() throws Exception {
        scriptTest("StringArgs", Locale.KOREA);
    }

    @Test
    public void testSubscribeKOREA() throws Exception {
        scriptTest("Subscribe", Locale.KOREA);
    }

    @Test
    public void testAppendKOREA() throws Exception {
        scriptTest("Append", Locale.KOREA);
    }

    @Test
    public void testDeleteKOREA() throws Exception {
        scriptTest("Delete", Locale.KOREA);
    }

}
