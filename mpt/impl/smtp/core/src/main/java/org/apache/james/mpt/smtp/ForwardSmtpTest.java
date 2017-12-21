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

import static com.jayway.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS;
import static com.jayway.awaitility.Duration.ONE_MINUTE;
import static org.hamcrest.Matchers.equalTo;

import java.util.Locale;

import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.SwarmGenericContainer;
import org.apache.james.utils.FakeSmtpHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.wait.HostPortWaitStrategy;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;
import com.jayway.restassured.RestAssured;

public abstract class ForwardSmtpTest {

    public static final String USER = "bob";
    public static final String DOMAIN = "mydomain.tld";
    public static final String USER_AT_DOMAIN = USER + "@" + DOMAIN;
    public static final String PASSWORD = "secret";

    private final TemporaryFolder folder = new TemporaryFolder();
    private final SwarmGenericContainer fakeSmtp = new SwarmGenericContainer(Images.FAKE_SMTP)
            .withExposedPorts(25)
            .withAffinityToContainer()
            .waitingFor(new HostPortWaitStrategy());
    
    @Rule
    public final RuleChain chain = RuleChain.outerRule(folder).around(fakeSmtp);
    private ConditionFactory calmlyAwait;

    protected abstract SmtpHostSystem createSmtpHostSystem();
    
    private SmtpHostSystem hostSystem;
    private SimpleScriptedTestProtocol scriptedTest;

    @Before
    public void setUp() throws Exception {
        hostSystem = createSmtpHostSystem();

        scriptedTest = new SimpleScriptedTestProtocol("/org/apache/james/smtp/scripts/", hostSystem)
                .withLocale(Locale.US)
                .withUser(USER_AT_DOMAIN, PASSWORD);
        
        hostSystem.getInMemoryDnsService()
            .registerMxRecord("yopmail.com", fakeSmtp.getContainerIp());
        hostSystem.addAddressMapping(USER, DOMAIN, "ray@yopmail.com");

        RestAssured.requestSpecification = FakeSmtpHelper.requestSpecification(fakeSmtp.getContainerIp());

        Duration slowPacedPollInterval = FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with()
            .pollInterval(slowPacedPollInterval)
            .and()
            .with()
            .pollDelay(slowPacedPollInterval)
            .await();

        calmlyAwait.atMost(ONE_MINUTE).until(() -> fakeSmtp.tryConnect(25));
    }

    @Test
    public void forwardingAnEmailShouldWork() throws Exception {
        scriptedTest.run("helo");

        calmlyAwait.atMost(ONE_MINUTE).until(() ->
            FakeSmtpHelper.isReceived(response -> response
                .body("[0].from", equalTo("matthieu@yopmail.com"))
                .body("[0].subject", equalTo("test"))
                .body("[0].text", equalTo("content"))));
    }
}
