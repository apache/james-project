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
package org.apache.james.spamassassin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.User;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.spamassassin.SpamAssassinExtension.SpamAssassin;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SpamAssassinExtension.class)
public class SpamAssassinInvokerTest {

    public static final User USER = User.fromUsername("any@james");
    private SpamAssassin spamAssassin;
    private SpamAssassinInvoker testee;

    @BeforeEach
    public void setup(SpamAssassin spamAssassin) throws Exception {
        this.spamAssassin = spamAssassin;
        testee = new SpamAssassinInvoker(new NoopMetricFactory(), spamAssassin.getIp(), spamAssassin.getBindingPort());
    }

    @Test
    public void scanMailShouldModifyHitsField() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("eml/spam.eml"));
        SpamAssassinResult result = testee.scanMail(mimeMessage, USER);

        assertThat(result.getHits()).isNotEqualTo(SpamAssassinResult.NO_RESULT);
    }

    @Test
    public void scanMailShouldModifyRequiredHitsField() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("eml/spam.eml"));
        SpamAssassinResult result = testee.scanMail(mimeMessage, USER);

        assertThat(result.getRequiredHits()).isEqualTo("5.0");
    }

    @Test
    public void scanMailShouldModifyHeadersField() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("eml/spam.eml"));
        SpamAssassinResult result = testee.scanMail(mimeMessage, USER);

        assertThat(result.getHeadersAsAttributes()).isNotEmpty();
    }

    @Disabled("MAILBOX-377 This test is not stable, fails on our CI and thus is temporarily disabled")
    @Test
    public void scanMailShouldMarkAsSpamWhenKnownAsSpam() throws Exception {
        spamAssassin.train("user");
        
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("spamassassin_db/spam/spam1"));

        SpamAssassinResult result = testee.scanMail(mimeMessage, USER);

        assertThat(result.getHeadersAsAttributes()).contains(new Attribute(SpamAssassinResult.FLAG_MAIL, AttributeValue.of("YES")));
    }

    @Test
    public void learnAsSpamShouldReturnTrueWhenLearningWorks() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("spamassassin_db/spam/spam2"));

        boolean result = testee.learnAsSpam(mimeMessage.getInputStream(), USER);

        assertThat(result).isTrue();
    }

    @Test
    public void scanMailShouldMarkAsSpamWhenMessageAlreadyLearnedAsSpam() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("spamassassin_db/spam/spam1"));

        byte[] messageAsBytes = MimeMessageUtil.asString(mimeMessage).getBytes(StandardCharsets.UTF_8);

        testee.learnAsSpam(new ByteArrayInputStream(messageAsBytes), USER);

        SpamAssassinResult result = testee.scanMail(mimeMessage, USER);

        assertThat(result.getHeadersAsAttributes()).contains(new Attribute(SpamAssassinResult.FLAG_MAIL, AttributeValue.of("YES")));
    }

    @Test
    public void learnAsHamShouldReturnTrueWhenLearningWorks() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("spamassassin_db/ham/ham2"));

        boolean result = testee.learnAsHam(mimeMessage.getInputStream(), USER);

        assertThat(result).isTrue();
    }

    @Test
    public void scanMailShouldMarkAsHamWhenMessageAlreadyLearnedAsHam() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("spamassassin_db/ham/ham1"));

        testee.learnAsHam(mimeMessage.getInputStream(), USER);

        SpamAssassinResult result = testee.scanMail(mimeMessage, USER);

        assertThat(result.getHeadersAsAttributes()).contains(new Attribute(SpamAssassinResult.FLAG_MAIL, AttributeValue.of("NO")));
    }

    @Test
    public void learnAsHamShouldAllowToForgetSpam() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("eml/spam.eml"));

        byte[] messageAsBytes = MimeMessageUtil.asString(mimeMessage).getBytes(StandardCharsets.UTF_8);

        testee.learnAsSpam(new ByteArrayInputStream(messageAsBytes), USER);
        testee.learnAsHam(new ByteArrayInputStream(messageAsBytes), USER);

        SpamAssassinResult result = testee.scanMail(mimeMessage, USER);

        assertThat(result.getHeadersAsAttributes()).contains(new Attribute(SpamAssassinResult.FLAG_MAIL, AttributeValue.of("NO")));
    }
}
