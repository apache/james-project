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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class SmtpStarttlsCommandTest {

    public static final String USER = "bob";
    public static final String DOMAIN = "mydomain.tld";
    public static final String USER_AT_DOMAIN = USER + "@" + DOMAIN;
    public static final String PASSWORD = "secret";

    protected SmtpHostSystem hostSystem;
    private SimpleScriptedTestProtocol scriptedTest;

    @BeforeEach
    public void setUp(SmtpHostSystem hostSystem) throws Exception {
        this.hostSystem = hostSystem;
        String scriptDir = "/org/apache/james/smtp/scripts/";
        scriptedTest = new SimpleScriptedTestProtocol(scriptDir, hostSystem)
                .withLocale(Locale.US)
                .withUser(USER_AT_DOMAIN, PASSWORD);
    }

    @Test
    void starttlsShouldWork() throws Exception {
        scriptedTest.run("starttls");
    }

    @Test
    void starttlsShouldBeRejectedWhenFollowedByCommand() throws Exception {
        scriptedTest.run("starttls_with_injection");
    }

    @Test
    void shouldNotRejectContentWithStartTls() throws Exception {
        scriptedTest.run("data_with_starttls");
    }


    @Test
    void shouldNotRejectRcptWithStartTls() throws Exception {
        scriptedTest.withUser("starttls@mydomain.tld", PASSWORD);
        scriptedTest.run("rcpt_with_starttls");
    }

    @Test
    void shouldNotRejectContentStartsWithStartTls() throws Exception {
        scriptedTest.run("data_starts_with_starttls");
    }
}
