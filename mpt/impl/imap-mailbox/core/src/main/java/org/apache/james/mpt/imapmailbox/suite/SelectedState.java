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
import org.apache.james.mpt.imapmailbox.suite.base.BaseSelectedState;
import org.junit.Test;

public class SelectedState extends BaseSelectedState {

    @Inject
    private static HostSystem system;
    
    public SelectedState() throws Exception {
        super(system);
    }

    @Test
    public void testCheckUS() throws Exception {
        scriptTest("Check", Locale.US);
    }

    @Test
    public void testExpungeUS() throws Exception {
        scriptTest("Expunge", Locale.US);
    }

    @Test
    public void testSearchUS() throws Exception {
        scriptTest("Search", Locale.US);
    }

    @Test
    public void testFetchSingleMessageUS() throws Exception {
        scriptTest("FetchSingleMessage", Locale.US);
    }

    @Test
    public void testFetchMultipleMessagesUS() throws Exception {
        scriptTest("FetchMultipleMessages", Locale.US);
    }

    @Test
    public void testFetchPeekUS() throws Exception {
        scriptTest("FetchPeek", Locale.US);
    }

    @Test
    public void testStoreUS() throws Exception {
        scriptTest("Store", Locale.US);
    }

    @Test
    public void testCopyUS() throws Exception {
        scriptTest("Copy", Locale.US);
    }

    @Test
    public void testUidUS() throws Exception {
        scriptTest("Uid", Locale.US);
    }

    @Test
    public void testCheckITALY() throws Exception {
        scriptTest("Check", Locale.ITALY);
    }

    @Test
    public void testExpungeITALY() throws Exception {
        scriptTest("Expunge", Locale.ITALY);
    }

    @Test
    public void testSearchITALY() throws Exception {
        scriptTest("Search", Locale.ITALY);
    }

    @Test
    public void testFetchSingleMessageITALY() throws Exception {
        scriptTest("FetchSingleMessage", Locale.ITALY);
    }

    @Test
    public void testFetchMultipleMessagesITALY() throws Exception {
        scriptTest("FetchMultipleMessages", Locale.ITALY);
    }

    @Test
    public void testFetchPeekITALY() throws Exception {
        scriptTest("FetchPeek", Locale.ITALY);
    }

    @Test
    public void testStoreITALY() throws Exception {
        scriptTest("Store", Locale.ITALY);
    }

    @Test
    public void testCopyITALY() throws Exception {
        scriptTest("Copy", Locale.ITALY);
    }

    @Test
    public void testUidITALY() throws Exception {
        scriptTest("Uid", Locale.ITALY);
    }

    @Test
    public void testCheckKOREA() throws Exception {
        scriptTest("Check", Locale.KOREA);
    }

    @Test
    public void testExpungeKOREA() throws Exception {
        scriptTest("Expunge", Locale.KOREA);
    }

    @Test
    public void testSearchKOREA() throws Exception {
        scriptTest("Search", Locale.KOREA);
    }

    @Test
    public void testFetchSingleMessageKOREA() throws Exception {
        scriptTest("FetchSingleMessage", Locale.KOREA);
    }

    @Test
    public void testFetchMultipleMessagesKOREA() throws Exception {
        scriptTest("FetchMultipleMessages", Locale.KOREA);
    }

    @Test
    public void testFetchPeekKOREA() throws Exception {
        scriptTest("FetchPeek", Locale.KOREA);
    }

    @Test
    public void testStoreKOREA() throws Exception {
        scriptTest("Store", Locale.KOREA);
    }

    @Test
    public void testCopyKOREA() throws Exception {
        scriptTest("Copy", Locale.KOREA);
    }

    @Test
    public void testUidKOREA() throws Exception {
        scriptTest("Uid", Locale.KOREA);
    }
    
    @Test
    public void testNamespaceUS() throws Exception {
        scriptTest("Namespace", Locale.US);
    }

    @Test
    public void testNamespaceITALY() throws Exception {
        scriptTest("Namespace", Locale.ITALY);
    }
    
    @Test
    public void testNamespaceKOREA() throws Exception {
        scriptTest("Namespace", Locale.KOREA);
    }
}
