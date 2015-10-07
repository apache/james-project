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

public class Fetch extends BaseSelectedState {

    @Inject
    private static HostSystem system;
    
    public Fetch() throws Exception {
        super(system);
    }

    @Test
    public void testFetchEnvelopeUS() throws Exception {
        scriptTest("FetchEnvelope", Locale.US);
    }

    @Test
    public void testFetchEnvelopeIT() throws Exception {
        scriptTest("FetchEnvelope", Locale.ITALY);
    }

    @Test
    public void testFetchEnvelopeKOREA() throws Exception {
        scriptTest("FetchEnvelope", Locale.KOREA);
    }

    @Test
    public void testFetchTextUS() throws Exception {
        scriptTest("FetchText", Locale.US);
    }

    @Test
    public void testFetchBodyNoSectionUS() throws Exception {
        scriptTest("FetchBodyNoSection", Locale.US);
    }

    @Test
    public void testFetchTextIT() throws Exception {
        scriptTest("FetchText", Locale.ITALY);
    }

    @Test
    public void testFetchBodyNoSectionIT() throws Exception {
        scriptTest("FetchBodyNoSection", Locale.ITALY);
    }

    @Test
    public void testFetchTextKOREA() throws Exception {
        scriptTest("FetchText", Locale.KOREA);
    }

    @Test
    public void testFetchBodyNoSectionKOREA() throws Exception {
        scriptTest("FetchBodyNoSection", Locale.KOREA);
    }

    @Test
    public void testFetchRFC822US() throws Exception {
        scriptTest("FetchRFC822", Locale.US);
    }

    @Test
    public void testFetchRFC822TextUS() throws Exception {
        scriptTest("FetchRFC822Text", Locale.US);
    }

    @Test
    public void testFetchRFC822HeaderUS() throws Exception {
        scriptTest("FetchRFC822Header", Locale.US);
    }

    @Test
    public void testFetchRFC822KOREA() throws Exception {
        scriptTest("FetchRFC822", Locale.KOREA);
    }

    @Test
    public void testFetchRFC822TextKOREA() throws Exception {
        scriptTest("FetchRFC822Text", Locale.KOREA);
    }

    @Test
    public void testFetchRFC822HeaderKOREA() throws Exception {
        scriptTest("FetchRFC822Header", Locale.KOREA);
    }

    @Test
    public void testFetchRFC822ITALY() throws Exception {
        scriptTest("FetchRFC822", Locale.ITALY);
    }

    @Test
    public void testFetchRFC822TextITALY() throws Exception {
        scriptTest("FetchRFC822Text", Locale.ITALY);
    }

    @Test
    public void testFetchRFC822HeaderITALY() throws Exception {
        scriptTest("FetchRFC822Header", Locale.ITALY);
    }

    @Test
    public void testFetchInternalDateUS() throws Exception {
        scriptTest("FetchInternalDate", Locale.US);
    }

    @Test
    public void testFetchInternalDateITALY() throws Exception {
        scriptTest("FetchInternalDate", Locale.ITALY);
    }

    @Test
    public void testFetchInternalDateKOREA() throws Exception {
        scriptTest("FetchInternalDate", Locale.KOREA);
    }

    @Test
    public void testFetchFetchRfcMixedUS() throws Exception {
        scriptTest("FetchRFC822Mixed", Locale.US);
    }

    @Test
    public void testFetchFetchRfcMixedKOREA() throws Exception {
        scriptTest("FetchRFC822Mixed", Locale.KOREA);
    }

    @Test
    public void testFetchFetchRfcMixedITALY() throws Exception {
        scriptTest("FetchRFC822Mixed", Locale.ITALY);
    }
}
