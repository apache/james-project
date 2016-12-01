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
package org.apache.james.mpt.smtp;

import java.util.Locale;

import javax.inject.Inject;

import org.apache.james.mpt.script.AbstractSimpleScriptedTestProtocol;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class SmtpStarttlsCommandTest extends AbstractSimpleScriptedTestProtocol {

    public static final String USER = "bob";
    public static final String DOMAIN = "mydomain.tld";
    public static final String USER_AT_DOMAIN = USER + "@" + DOMAIN;
    public static final String PASSWORD = "secret";

    private final TemporaryFolder folder = new TemporaryFolder();
    
    @Rule
    public final RuleChain chain = RuleChain.outerRule(folder);

    @Inject
    private static SmtpHostSystem hostSystem;

    public SmtpStarttlsCommandTest() throws Exception {
        super(hostSystem, USER_AT_DOMAIN, PASSWORD, "/org/apache/james/smtp/scripts/");
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void starttlsShouldWork() throws Exception {
        scriptTest("starttls", Locale.US);
    }

    @Test
    public void starttlsShouldBeRejectedWhenFollowedByCommand() throws Exception {
        scriptTest("starttls_with_injection", Locale.US);
    }
}
