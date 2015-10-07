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
import org.apache.james.mpt.imapmailbox.suite.base.BaseNonAuthenticatedState;
import org.junit.Test;

public class NonAuthenticatedState extends BaseNonAuthenticatedState {

    @Inject
    private static HostSystem system;
    
    public NonAuthenticatedState() throws Exception {
        super(system);
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
    public void testCapabilityUS() throws Exception {
        scriptTest("Capability", Locale.US);
    }

    @Test
    public void testLoginUS() throws Exception {
        scriptTest("Login", Locale.US);
    }

    @Test
    public void testValidAuthenticatedUS() throws Exception {
        scriptTest("ValidAuthenticated", Locale.US);
    }

    @Test
    public void testValidSelectedUS() throws Exception {
        scriptTest("ValidSelected", Locale.US);
    }

    @Test
    public void testAuthenticateUS() throws Exception {
        scriptTest("Authenticate", Locale.US);
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
    public void testCapabilityITALY() throws Exception {
        scriptTest("Capability", Locale.ITALY);
    }

    @Test
    public void testLoginITALY() throws Exception {
        scriptTest("Login", Locale.ITALY);
    }

    @Test
    public void testValidAuthenticatedITALY() throws Exception {
        scriptTest("ValidAuthenticated", Locale.ITALY);
    }

    @Test
    public void testValidSelectedITALY() throws Exception {
        scriptTest("ValidSelected", Locale.ITALY);
    }

    @Test
    public void testAuthenticateITALY() throws Exception {
        scriptTest("Authenticate", Locale.ITALY);
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
    public void testCapabilityKOREA() throws Exception {
        scriptTest("Capability", Locale.KOREA);
    }

    @Test
    public void testLoginKOREA() throws Exception {
        scriptTest("Login", Locale.KOREA);
    }

    @Test
    public void testValidAuthenticatedKOREA() throws Exception {
        scriptTest("ValidAuthenticated", Locale.KOREA);
    }

    @Test
    public void testValidSelectedKOREA() throws Exception {
        scriptTest("ValidSelected", Locale.KOREA);
    }

    @Test
    public void testAuthenticateKOREA() throws Exception {
        scriptTest("Authenticate", Locale.KOREA);
    }
}
