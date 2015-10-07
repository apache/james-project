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

public class FetchBodyStructure extends BaseSelectedState {

    @Inject
    private static HostSystem system;
    
    public FetchBodyStructure() throws Exception {
        super(system);
    }

    @Test
    public void testFetchFetchSimpleBodyStructureUS() throws Exception {
        scriptTest("FetchSimpleBodyStructure", Locale.US);
    }

    @Test
    public void testFetchFetchSimpleBodyStructureKOREA() throws Exception {
        scriptTest("FetchSimpleBodyStructure", Locale.KOREA);
    }

    @Test
    public void testFetchFetchSimpleBodyStructureITALY() throws Exception {
        scriptTest("FetchSimpleBodyStructure", Locale.ITALY);
    }

    @Test
    public void testFetchFetchMultipartBodyStructureUS() throws Exception {
        scriptTest("FetchMultipartBodyStructure", Locale.US);
    }

    @Test
    public void testFetchFetchMultipartBodyStructureKOREA() throws Exception {
        scriptTest("FetchMultipartBodyStructure", Locale.KOREA);
    }

    @Test
    public void testFetchFetchMultipartBodyStructureITALY() throws Exception {
        scriptTest("FetchMultipartBodyStructure", Locale.ITALY);
    }

    @Test
    public void testFetchStructureEmbeddedUS() throws Exception {
        scriptTest("FetchStructureEmbedded", Locale.US);
    }

    @Test
    public void testFetchStructureEmbeddedITALY() throws Exception {
        scriptTest("FetchStructureEmbedded", Locale.ITALY);
    }

    @Test
    public void testFetchStructureEmbeddedKOREA() throws Exception {
        scriptTest("FetchStructureEmbedded", Locale.KOREA);
    }

    @Test
    public void testFetchStructureComplexUS() throws Exception {
        scriptTest("FetchStructureComplex", Locale.US);
    }

    @Test
    public void testFetchStructureComplexITALY() throws Exception {
        scriptTest("FetchStructureComplex", Locale.ITALY);
    }

    @Test
    public void testFetchStructureComplexKOREA() throws Exception {
        scriptTest("FetchStructureComplex", Locale.KOREA);
    }
}
