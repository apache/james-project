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
package org.apache.james.mpt.imapmailbox;

import org.apache.james.mpt.imapmailbox.suite.AuthenticatedState;
import org.apache.james.mpt.imapmailbox.suite.ConcurrentSessions;
import org.apache.james.mpt.imapmailbox.suite.Events;
import org.apache.james.mpt.imapmailbox.suite.Expunge;
import org.apache.james.mpt.imapmailbox.suite.Fetch;
import org.apache.james.mpt.imapmailbox.suite.FetchBodySection;
import org.apache.james.mpt.imapmailbox.suite.FetchBodyStructure;
import org.apache.james.mpt.imapmailbox.suite.FetchHeaders;
import org.apache.james.mpt.imapmailbox.suite.Listing;
import org.apache.james.mpt.imapmailbox.suite.Move;
import org.apache.james.mpt.imapmailbox.suite.NonAuthenticatedState;
import org.apache.james.mpt.imapmailbox.suite.PartialFetch;
import org.apache.james.mpt.imapmailbox.suite.QuotaTest;
import org.apache.james.mpt.imapmailbox.suite.Rename;
import org.apache.james.mpt.imapmailbox.suite.Search;
import org.apache.james.mpt.imapmailbox.suite.Security;
import org.apache.james.mpt.imapmailbox.suite.Select;
import org.apache.james.mpt.imapmailbox.suite.SelectedInbox;
import org.apache.james.mpt.imapmailbox.suite.SelectedState;
import org.apache.james.mpt.imapmailbox.suite.UidSearch;
import org.apache.james.mpt.imapmailbox.suite.UserFlagsSupport;
import org.apache.onami.test.OnamiSuite;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(OnamiSuite.class)
@SuiteClasses({ 
    AuthenticatedState.class,
    ConcurrentSessions.class,
    Events.class,
    Expunge.class,
    Fetch.class,
    FetchBodySection.class,
    FetchBodyStructure.class,
    FetchHeaders.class,
    Listing.class,
    NonAuthenticatedState.class,
    PartialFetch.class,
    Rename.class,
    Search.class,
    Security.class,
    Select.class,
    QuotaTest.class,
    UserFlagsSupport.class,
    Move.class,
    SelectedInbox.class,
    SelectedState.class,
    UidSearch.class
})
public abstract class AbstractMailboxTest {

}
