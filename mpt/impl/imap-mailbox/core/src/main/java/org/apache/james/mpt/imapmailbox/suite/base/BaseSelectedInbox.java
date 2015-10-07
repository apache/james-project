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

/**
 * <p>
 * Tests commands which are valid in AUTHENTICATED and NONAUTHENTICATED by
 * running them in the SELECTED state. Many commands function identically, while
 * others are invalid in this state.
 * </p>
 * <p>
 * Recommended scripts:
 * </p>
 * <ul>
 * <li>ValidNonAuthenticated</li>
 * <li>Capability</li>
 * <li>Noop</li>
 * <li>Logout</li>
 * <li>Create</li>
 * <li>ExamineEmpty</li>
 * <li>SelectEmpty</li>
 * <li>ListNamespace</li>
 * <li>ListMailboxes</li>
 * <li>Status</li>
 * <li>StringArgs</li>
 * <li>Subscribe</li>
 * <li>Append</li>
 * <li>Delete</li>
 * </ul>
 * 
 * @author Darrell DeBoer <darrell@apache.org>
 * 
 * @version $Revision: 560719 $
 */
public class BaseSelectedInbox extends BaseAuthenticatedState {
    public BaseSelectedInbox(HostSystem system) throws Exception {
        super(system);
    }

    /**
     * Superclass sets up welcome message and login session in
     * {@link #preElements}. A "SELECT INBOX" session is then added to these
     * elements.
     * 
     * @throws Exception
     */
    public void setUp() throws Exception {
        super.setUp();
        addTestFile("SelectInbox.test", preElements);
    }

    protected void addCloseInbox() {
        postElements.CL("a CLOSE");
        postElements.SL(".*", "AbstractBaseTestSelectedInbox.java:76");
    }
}
