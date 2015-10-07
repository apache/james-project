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

package org.apache.james.mpt.imapmailbox.suite.base;

import org.apache.james.mpt.api.HostSystem;
import org.junit.Before;

/**
 * <p>
 * Runs tests for commands valid in the NON_AUTHENTICATED state. A welcome
 * message precedes the execution of the test elements.
 * </p>
 * <p>
 * Recommended test scripts:
 * </p>
 * <ul>
 * <li>ValidAuthenticated</li>
 * <li>ValidSelected</li>
 * <li>Capability</li>
 * <li>Noop</li>
 * <li>Logout</li>
 * <li>Authenticate</li>
 * <li>Login</li>
 * </ul>
 */
public class BaseNonAuthenticatedState extends BaseImapProtocol {
    public BaseNonAuthenticatedState(HostSystem system) throws Exception {
        super(system);
    }

    /**
     * Adds a welcome message to the {@link #preElements}.
     * 
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        super.setUp();

        addTestFile("Welcome.test", preElements);
    }
}
