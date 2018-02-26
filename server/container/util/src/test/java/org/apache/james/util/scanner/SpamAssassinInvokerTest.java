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
package org.apache.james.util.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.internet.MimeMessage;

import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.scanner.SpamAssassinExtension.SpamAssassin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SpamAssassinExtension.class)
public class SpamAssassinInvokerTest {

    private SpamAssassin spamAssassin;
    private SpamAssassinInvoker testee;

    @BeforeEach
    public void setup(SpamAssassin spamAssassin) throws Exception {
        this.spamAssassin = spamAssassin;
        testee = new SpamAssassinInvoker(spamAssassin.getIp(), spamAssassin.getBindingPort());
    }

    @Test
    public void scanMailShouldModifyHitsField() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("eml/spam.eml"));
        SpamAssassinResult result = testee.scanMail(mimeMessage);

        // The result is varying from 0.4 to 0.0
        assertThat(result.getHits()).startsWith("0.");
    }

    @Test
    public void scanMailShouldModifyRequiredHitsField() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("eml/spam.eml"));
        SpamAssassinResult result = testee.scanMail(mimeMessage);

        assertThat(result.getRequiredHits()).isEqualTo("5.0");
    }

    @Test
    public void scanMailShouldModifyHeadersField() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("eml/spam.eml"));
        SpamAssassinResult result = testee.scanMail(mimeMessage);

        assertThat(result.getHeadersAsAttribute()).isNotEmpty();
    }

    @Test
    public void scanMailShouldMarkHasSpamWhenKnownHasSpam() throws Exception {
        spamAssassin.train();
        
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("spamassassin_db/spam/spam1"));

        SpamAssassinResult result = testee.scanMail(mimeMessage);

        assertThat(result.getHeadersAsAttribute().get(SpamAssassinInvoker.FLAG_MAIL_ATTRIBUTE_NAME)).isEqualTo("YES");
    }
}
