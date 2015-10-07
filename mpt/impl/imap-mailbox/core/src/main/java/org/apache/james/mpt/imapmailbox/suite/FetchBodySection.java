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

public class FetchBodySection extends BaseSelectedState {

    @Inject
    private static HostSystem system;
    
    public FetchBodySection() throws Exception {
        super(system);
    }

    @Test
    public void testFetchMultipartAlternativeUS() throws Exception {
        scriptTest("FetchMultipartAlternative", Locale.US);
    }

    @Test
    public void testFetchMultipartAlternativeITALY() throws Exception {
        scriptTest("FetchMultipartAlternative", Locale.ITALY);
    }

    @Test
    public void testFetchMultipartAlternativeKOREA() throws Exception {
        scriptTest("FetchMultipartAlternative", Locale.KOREA);
    }

    @Test
    public void testFetchMultipartMixedUS() throws Exception {
        scriptTest("FetchMultipartMixed", Locale.US);
    }

    @Test
    public void testFetchMultipartMixedITALY() throws Exception {
        scriptTest("FetchMultipartMixed", Locale.ITALY);
    }

    @Test
    public void testFetchMultipartMixedKOREA() throws Exception {
        scriptTest("FetchMultipartMixed", Locale.KOREA);
    }

    @Test
    public void testFetchMultipartMixedComplexUS() throws Exception {
        scriptTest("FetchMultipartMixedComplex", Locale.US);
    }

    @Test
    public void testFetchMultipartMixedComplexITALY() throws Exception {
        scriptTest("FetchMultipartMixedComplex", Locale.ITALY);
    }

    @Test
    public void testFetchMultipartMixedComplexKOREA() throws Exception {
        scriptTest("FetchMultipartMixedComplex", Locale.KOREA);
    }
}
