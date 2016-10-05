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

package org.apache.james.mpt.managesieve.file;

import org.apache.james.mpt.onami.test.OnamiSuite;
import org.apache.james.mpt.onami.test.annotation.GuiceModules;
import org.apache.james.mpt.testsuite.AuthenticateTest;
import org.apache.james.mpt.testsuite.CapabilityTest;
import org.apache.james.mpt.testsuite.CheckScriptTest;
import org.apache.james.mpt.testsuite.DeleteScriptTest;
import org.apache.james.mpt.testsuite.GetScriptTest;
import org.apache.james.mpt.testsuite.HaveSpaceTest;
import org.apache.james.mpt.testsuite.ListScriptsTest;
import org.apache.james.mpt.testsuite.LogoutTest;
import org.apache.james.mpt.testsuite.NoopTest;
import org.apache.james.mpt.testsuite.PutScriptTest;
import org.apache.james.mpt.testsuite.RenameScriptTest;
import org.apache.james.mpt.testsuite.SetActiveTest;
import org.apache.james.mpt.testsuite.StartTlsTest;
import org.apache.james.mpt.testsuite.UnauthenticatedTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@GuiceModules({ FileModule.class })
@RunWith(OnamiSuite.class)
@Suite.SuiteClasses({
    NoopTest.class,
    UnauthenticatedTest.class,
    LogoutTest.class,
    AuthenticateTest.class,
    StartTlsTest.class,
    CapabilityTest.class,
    HaveSpaceTest.class,
    PutScriptTest.class,
    SetActiveTest.class,
    GetScriptTest.class,
    DeleteScriptTest.class,
    RenameScriptTest.class,
    CheckScriptTest.class,
    ListScriptsTest.class
})
public class ManageSieveFileTest {
}
