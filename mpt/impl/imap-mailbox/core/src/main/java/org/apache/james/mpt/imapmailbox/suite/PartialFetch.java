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

public class PartialFetch extends BaseSelectedState {

    @Inject
    private static HostSystem system;
    
    public PartialFetch() throws Exception {
        super(system);
    }

    @Test
    public void testBodyPartialFetchUS() throws Exception {
        scriptTest("BodyPartialFetch", Locale.US);
    }

    @Test
    public void testBodyPartialFetchIT() throws Exception {
        scriptTest("BodyPartialFetch", Locale.ITALY);
    }

    @Test
    public void testBodyPartialFetchKO() throws Exception {
        scriptTest("BodyPartialFetch", Locale.KOREA);
    }

    @Test
    public void testTextPartialFetchUS() throws Exception {
        scriptTest("TextPartialFetch", Locale.US);
    }

    @Test
    public void testTextPartialFetchKO() throws Exception {
        scriptTest("TextPartialFetch", Locale.US);
    }

    @Test
    public void testTextPartialFetchIT() throws Exception {
        scriptTest("TextPartialFetch", Locale.US);
    }

    @Test
    public void testMimePartialFetchUS() throws Exception {
        scriptTest("MimePartialFetch", Locale.US);
    }

    @Test
    public void testMimePartialFetchIT() throws Exception {
        scriptTest("MimePartialFetch", Locale.ITALY);
    }

    @Test
    public void testMimePartialFetchKO() throws Exception {
        scriptTest("MimePartialFetch", Locale.KOREA);
    }

    @Test
    public void testHeaderPartialFetchUS() throws Exception {
        scriptTest("HeaderPartialFetch", Locale.US);
    }

    @Test
    public void testHeaderPartialFetchIT() throws Exception {
        scriptTest("HeaderPartialFetch", Locale.ITALY);
    }

    @Test
    public void testHeaderPartialFetchKO() throws Exception {
        scriptTest("HeaderPartialFetch", Locale.KOREA);
    }
}
