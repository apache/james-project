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

import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.TWO_MINUTES;
import static org.hamcrest.Matchers.equalTo;

import java.util.Locale;

import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.apache.james.utils.FakeSmtpExtension;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;


public interface ForwardSmtpTest {

    String USER = "bob";
    String DOMAIN = "mydomain.tld";
    String USER_AT_DOMAIN = USER + "@" + DOMAIN;
    String PASSWORD = "secret";
    Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    ConditionFactory calmlyAwait = Awaitility.with()
            .pollInterval(slowPacedPollInterval)
            .await();

    @RegisterExtension
    FakeSmtpExtension fakeSmtp = FakeSmtpExtension.withDefaultPort();

    @Test
    default void forwardingAnEmailShouldWork(SmtpHostSystem hostSystem,
                                             FakeSmtpExtension.FakeSmtp fakeSmtp,
                                             InMemoryDNSService dnsService) throws Exception {
        SimpleScriptedTestProtocol scriptedTest =
                new SimpleScriptedTestProtocol("/org/apache/james/smtp/scripts/", hostSystem)
                        .withLocale(Locale.US)
                        .withUser(USER_AT_DOMAIN, PASSWORD);

        dnsService.registerMxRecord("yopmail.com", fakeSmtp.getContainerIp());
        hostSystem.addAddressMapping(USER, DOMAIN, "ray@yopmail.com");

        scriptedTest.run("helo");

        calmlyAwait.atMost(TWO_MINUTES).untilAsserted(() ->
                fakeSmtp.assertEmailReceived(response -> response
                        .body("[0].from", equalTo("matthieu@yopmail.com"))
                        .body("[0].subject", equalTo("test"))
                        .body("[0].text", equalTo("content"))));
    }
}
