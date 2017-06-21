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

package org.apache.james.mpt.testsuite;

import java.util.Locale;

import org.apache.james.mpt.host.ManageSieveHostSystem;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Inject;

public class UnauthenticatedTest {

    @Inject
    private static ManageSieveHostSystem hostSystem;

    public static final String USER = "user";
    public static final String PASSWORD = "password";
    
    private SimpleScriptedTestProtocol simpleScriptedTestProtocol;

    @Before
    public void setUp() throws Exception {
        simpleScriptedTestProtocol = new SimpleScriptedTestProtocol("/org/apache/james/managesieve/scripts/", hostSystem)
                .withUser(USER, PASSWORD)
                .withLocale(Locale.US);
    }

    @After
    public void tearDown() throws Exception {
        hostSystem.afterTest();
    }
    
    @Test
    public void unauthenticatedCommandShouldWork() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("unauthenticate");
    }

}
