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
import org.apache.james.mpt.imapmailbox.suite.base.BaseAuthenticatedState;
import org.junit.Test;

public class ConcurrentSessions extends BaseAuthenticatedState {

    @Inject
    private static HostSystem system;
    
    public ConcurrentSessions() throws Exception {
        super(system);
    }
    
    @Test
    public void testConcurrentExpungeResponseUS() throws Exception {
          scriptTest("ConcurrentExpungeResponse", Locale.US);
    }

    @Test
    public void testConcurrentExpungeResponseITALY() throws Exception {
        scriptTest("ConcurrentExpungeResponse", Locale.ITALY);
    }

    @Test
    public void testConcurrentExpungeResponseKOREA() throws Exception {
        scriptTest("ConcurrentExpungeResponse", Locale.KOREA);
    }

    @Test
    public void testConcurrentCrossExpungeUS() throws Exception {
          scriptTest("ConcurrentCrossExpunge", Locale.US);
    }
    
    @Test
    public void testConcurrentCrossExpungeITALY() throws Exception {
          scriptTest("ConcurrentCrossExpunge", Locale.ITALY);
    }
    
    @Test
    public void testConcurrentCrossExpungeKOREA() throws Exception {
          scriptTest("ConcurrentCrossExpunge", Locale.KOREA);
    }
    
    @Test
    public void testConcurrentRenameSelectedSubUS() throws Exception {
        scriptTest("ConcurrentRenameSelectedSub", Locale.US);
    }

    @Test
    public void testConcurrentExistsResponseUS() throws Exception {
        scriptTest("ConcurrentExistsResponse", Locale.US);
    }

    @Test
    public void testConcurrentDeleteSelectedUS() throws Exception {
        scriptTest("ConcurrentDeleteSelected", Locale.US);
    }

    @Test
    public void testConcurrentFetchResponseUS() throws Exception {
        scriptTest("ConcurrentFetchResponse", Locale.US);
    }

    @Test
    public void testConcurrentRenameSelectedUS() throws Exception {
        scriptTest("ConcurrentRenameSelected", Locale.US);
    }

    @Test
    public void testConcurrentRenameSelectedSubKOREA() throws Exception {
        scriptTest("ConcurrentRenameSelectedSub", Locale.KOREA);
    }
    
    @Test
    public void testConcurrentExistsResponseKOREA() throws Exception {
        scriptTest("ConcurrentExistsResponse", Locale.KOREA);
    }

    @Test
    public void testConcurrentDeleteSelectedKOREA() throws Exception {
        scriptTest("ConcurrentDeleteSelected", Locale.KOREA);
    }

    @Test
    public void testConcurrentFetchResponseKOREA() throws Exception {
        scriptTest("ConcurrentFetchResponse", Locale.KOREA);
    }

    @Test
    public void testConcurrentRenameSelectedKOREA() throws Exception {
        scriptTest("ConcurrentRenameSelected", Locale.KOREA);
    }

    @Test
    public void testConcurrentRenameSelectedSubITALY() throws Exception {
        scriptTest("ConcurrentRenameSelectedSub", Locale.ITALY);
    }
    
    @Test
    public void testConcurrentExistsResponseITALY() throws Exception {
        scriptTest("ConcurrentExistsResponse", Locale.ITALY);
    }

    @Test
    public void testConcurrentDeleteSelectedITALY() throws Exception {
        scriptTest("ConcurrentDeleteSelected", Locale.ITALY);
    }

    @Test
    public void testConcurrentFetchResponseITALY() throws Exception {
        scriptTest("ConcurrentFetchResponse", Locale.ITALY);
    }

    @Test
    public void testConcurrentRenameSelectedITALY() throws Exception {
        scriptTest("ConcurrentRenameSelected", Locale.ITALY);
    }

    @Test
    public void expungeShouldNotBreakUIDToMSNMapping() throws Exception {
        scriptTest("ConcurrentExpungeUIDToMSNMapping", Locale.US);
    }

    @Test
    public void appendShouldNotBreakUIDToMSNMapping() throws Exception {
        scriptTest("ConcurrentAppendUIDToMSNMapping", Locale.US);
    }
}
