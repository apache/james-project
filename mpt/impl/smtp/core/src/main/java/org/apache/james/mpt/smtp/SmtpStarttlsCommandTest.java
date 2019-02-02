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

import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public abstract class SmtpStarttlsCommandTest {

    public static final String USER = "bob";
    public static final String DOMAIN = "mydomain.tld";
    public static final String USER_AT_DOMAIN = USER + "@" + DOMAIN;
    public static final String PASSWORD = "secret";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    protected abstract SmtpHostSystem createSmtpHostSystem();
    
    private SmtpHostSystem hostSystem;
    private SimpleScriptedTestProtocol scriptedTest;

    @Before
    public void setUp() throws Exception {
        hostSystem = createSmtpHostSystem();
        String scriptDir = "/org/apache/james/smtp/scripts/";
        scriptedTest = new SimpleScriptedTestProtocol(scriptDir, hostSystem)
                .withLocale(Locale.US)
                .withUser(USER_AT_DOMAIN, PASSWORD);
    }

    @Test
    public void starttlsShouldWork() throws Exception {
        scriptedTest.run("starttls");
    }

    @Test
    public void starttlsShouldBeRejectedWhenFollowedByCommand() throws Exception {
        scriptedTest.run("starttls_with_injection");
    }

    @Test
    public void shouldNotRejectContentWithStartTls() throws Exception {
        scriptedTest.run("data_with_starttls");
    }


    @Test
    public void shouldNotRejectRcptWithStartTls() throws Exception {
        scriptedTest.withUser("starttls@mydomain.tld", PASSWORD);
        scriptedTest.run("rcpt_with_starttls");
    }

    @Test
    public void shouldNotRejectContentStartsWithStartTls() throws Exception {
        scriptedTest.run("data_starts_with_starttls");
    }
}
