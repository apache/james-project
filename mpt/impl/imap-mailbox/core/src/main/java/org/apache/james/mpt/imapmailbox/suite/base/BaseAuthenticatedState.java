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
import org.apache.james.mpt.imapmailbox.ImapTestConstants;
import org.junit.Before;

/**
 * <p>
 * Runs tests for commands valid in the AUTHENTICATED state. A login session
 * precedes the execution of the test elements.
 * </p>
 * <p>
 * Suggested tests:
 * </p>
 * <ul>
 * <li>ValidSelected</li>
 * <li>ValidNonAuthenticated</li>
 * <li>Capability</li>
 * <li>Noop</li>
 * <li>Logout</li>
 * <li>AppendExamineInbox</li>
 * <li>AppendSelectInbox</li>
 * <li>Create</li>
 * <li>ExamineEmpty</li>
 * <li>SelectEmpty</li>
 * <li>ListNamespace</li>
 * <li>ListMailboxes</li>
 * <li>Status</li>
 * <li>Subscribe</li>
 * <li>Delete</li>
 * <li>Append</li>
 * <li>Compound:
 * <ul>
 * <li>AppendExpunge</li>
 * <li>SelectAppend</li>
 * <li>StringArgs</li>
 * </ul>
 * </li>
 * </ul>
 * </p>
 */
public class BaseAuthenticatedState extends
        BaseImapProtocol implements ImapTestConstants {
    public BaseAuthenticatedState(HostSystem hostSystem) throws Exception {
        super(hostSystem);
    }

    /**
     * Sets up {@link #preElements} with a welcome message and login
     * request/response.
     * 
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        super.setUp();
        addTestFile("Welcome.test", preElements);
        addLogin(USER, PASSWORD);
    }

    protected void addLogin(String username, String password) {
        preElements.CL("a001 LOGIN " + username + " " + password);
        preElements.SL("a001 OK .*",
                "BaseAuthenticatedState.java:83");
    }
}
