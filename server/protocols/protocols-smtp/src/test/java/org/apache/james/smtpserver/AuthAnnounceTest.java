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

package org.apache.james.smtpserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthAnnounceTest {
    private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();

    @BeforeEach
    void setUp() throws Exception {
        testSystem.preSetUp();
    }

    @AfterEach
    void tearDown() {
        testSystem.smtpServer.destroy();
    }

    @Test
    void authAnnounceAlwaysShouldAnnounceAuth() throws Exception {
        configureAndInit("smtpserver-authAnnounceAlways.xml");

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString())
                .contains("250-AUTH LOGIN PLAIN");
        });
    }

    @Test
    void authAnnounceSometimeShouldNotAnnounceAuthWhenMatching() throws Exception {
        configureAndInit("smtpserver-authAnnounceSometimeMatching.xml");

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString())
                .doesNotContain("250-AUTH LOGIN PLAIN");
        });
    }

    @Test
    void plainAuthShouldNotBeAnnouncedWhenDisabled() throws Exception {
        configureAndInit("smtpserver-no-plain.xml");

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString())
                .doesNotContain("250-AUTH LOGIN PLAIN");
        });
    }

    @Test
    void plainAuthShouldFailWhenDisabled() throws Exception {
        configureAndInit("smtpserver-no-plain.xml");

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpProtocol.sendCommand("AUTH PLAIN");

        assertThat(smtpProtocol.getReplyString())
                .isEqualTo("504 Unrecognized Authentication Type\r\n");
    }

    @Test
    void authAnnounceSometimeShouldAnnounceAuthWhenNotMatching() throws Exception {
        configureAndInit("smtpserver-authAnnounceSometimeNotMatching.xml");

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString())
                .contains("250-AUTH LOGIN PLAIN");
        });
    }

    @Test
    void authAnnounceNeverShouldNotAnnounceAuth() throws Exception {
        configureAndInit("smtpserver-authAnnounceNever.xml");

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString())
                .doesNotContain("250-AUTH LOGIN PLAIN");
        });
    }

    @Test
    void authShouldNotBeAnnouncedOnPlainChannelsWhenRequireSSL() throws Exception {
        configureAndInit("smtpserver-requireSSL.xml");

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString())
                .doesNotContain("250-AUTH LOGIN PLAIN");
        });
    }

    @Test
    void shouldStartWithPreviousConfiguration() throws Exception {
        configureAndInit("smtpserver-noauth.xml");

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString())
                // SSL is required
                .doesNotContain("250-AUTH LOGIN PLAIN");
        });
    }

    private void configureAndInit(String configurationName) throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream(configurationName));
        testSystem.configureSaslMechanisms(configuration);
        testSystem.smtpServer.configure(configuration);
        testSystem.smtpServer.init();
    }
}
