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

package org.apache.james.jdkim.mailets;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.jdkim.MockPublicKeyRecordRetriever;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.Test;

public class DKIMVerifyTest {

    @Test
    void testDKIMVerifyPass() throws Exception {
        String message = "DKIM-Signature: v=1; d=example.com; t=1284762805; b=ZFfwSIzTQM7k9syRnl9VfQh0/dr99euvBe1gn/DiTrnEZjxyjzQBD2MMvowVdbHpPMtSjtCtehU9zZ3urXmj5iHKujpEkP92FEKinzElkQ2eT2zoxdg1zByPHsKPX+KjrBespAJcO2k052aOK5kIBFxpQumP4aiW7ZklBKSWMBk=; s=selector; a=rsa-sha256; bh=rHOD7fd9xnNxK7OSl5ellpQVF14NNFbOIizqtUMhnio=; h=from:to:received:received;\r\n"
            + "Received: by 10.XX.XX.12 with SMTP id dfgskldjfhgkljsdfhgkljdhfg;\r\n\tTue, 06 Oct 2009 07:37:34 -0700 (PDT)\r\nReturn-Path: <bounce@example.com>\r\nReceived: from example.co.uk (example.co.uk [XX.XXX.125.19])\r\n\tby mx.example.com with ESMTP id dgdfgsdfgsd.97.2009.10.06.07.37.32;\r\n\tTue, 06 Oct 2009 07:37:32 -0700 (PDT)\r\nFrom: apache@bago.org\r\nTo: apache@bago.org\r\n\r\nbody\r\nprova\r\n";

        Mail mail = process(message);
        
        Optional<String> attr = AttributeUtils.getValueAndCastFromMail(mail, DKIMVerify.DKIM_AUTH_RESULT, String.class);
        assertThat(attr)
            .hasValueSatisfying(result -> assertThat(result).startsWith("pass"));
    }

    @Test
    void testDKIMVerifyFail() throws Exception {
        // altered message body
        String message = "DKIM-Signature: v=1; d=example.com; t=1284762805; b=ZFfwSIzTQM7k9syRnl9VfQh0/dr99euvBe1gn/DiTrnEZjxyjzQBD2MMvowVdbHpPMtSjtCtehU9zZ3urXmj5iHKujpEkP92FEKinzElkQ2eT2zoxdg1zByPHsKPX+KjrBespAJcO2k052aOK5kIBFxpQumP4aiW7ZklBKSWMBk=; s=selector; a=rsa-sha256; bh=rHOD7fd9xnNxK7OSl5ellpQVF14NNFbOIizqtUMhnio=; h=from:to:received:received;\r\n"
            + "Received: by 10.XX.XX.12 with SMTP id dfgskldjfhgkljsdfhgkljdhfg;\r\n\tTue, 06 Oct 2009 07:37:34 -0700 (PDT)\r\nReturn-Path: <bounce@example.com>\r\nReceived: from example.co.uk (example.co.uk [XX.XXX.125.19])\r\n\tby mx.example.com with ESMTP id dgdfgsdfgsd.97.2009.10.06.07.37.32;\r\n\tTue, 06 Oct 2009 07:37:32 -0700 (PDT)\r\nFrom: apache@bago.org\r\nTo: apache@bago.org\r\n\r\nbody\r\nprova altered\r\n";

        Mail mail = process(message);
        
        Optional<String> attr = AttributeUtils.getValueAndCastFromMail(mail, DKIMVerify.DKIM_AUTH_RESULT, String.class);
        assertThat(attr)
            .hasValueSatisfying(result -> assertThat(result).startsWith("fail"));
    }

    @Test
    void testDKIMVerifyFailInvalid() throws Exception {
        // invalid version v=2
        String message = "DKIM-Signature: v=2; d=example.com; t=1284762805; b=ZFfwSIzTQM7k9syRnl9VfQh0/dr99euvBe1gn/DiTrnEZjxyjzQBD2MMvowVdbHpPMtSjtCtehU9zZ3urXmj5iHKujpEkP92FEKinzElkQ2eT2zoxdg1zByPHsKPX+KjrBespAJcO2k052aOK5kIBFxpQumP4aiW7ZklBKSWMBk=; s=selector; a=rsa-sha256; bh=rHOD7fd9xnNxK7OSl5ellpQVF14NNFbOIizqtUMhnio=; h=from:to:received:received;\r\n"
            + "Received: by 10.XX.XX.12 with SMTP id dfgskldjfhgkljsdfhgkljdhfg;\r\n\tTue, 06 Oct 2009 07:37:34 -0700 (PDT)\r\nReturn-Path: <bounce@example.com>\r\nReceived: from example.co.uk (example.co.uk [XX.XXX.125.19])\r\n\tby mx.example.com with ESMTP id dgdfgsdfgsd.97.2009.10.06.07.37.32;\r\n\tTue, 06 Oct 2009 07:37:32 -0700 (PDT)\r\nFrom: apache@bago.org\r\nTo: apache@bago.org\r\n\r\nbody\r\nprova\r\n";

        Mail mail = process(message);
        
        Optional<String> attr = AttributeUtils.getValueAndCastFromMail(mail, DKIMVerify.DKIM_AUTH_RESULT, String.class);
        assertThat(attr)
                .hasValueSatisfying(result -> assertThat(result).startsWith("fail"));
    }

    @Test
    void testDKIMVerifyNeutral() throws Exception {
        // no signatures!
        String message = ""
            + "Received: by 10.XX.XX.12 with SMTP id dfgskldjfhgkljsdfhgkljdhfg;\r\n\tTue, 06 Oct 2009 07:37:34 -0700 (PDT)\r\nReturn-Path: <bounce@example.com>\r\nReceived: from example.co.uk (example.co.uk [XX.XXX.125.19])\r\n\tby mx.example.com with ESMTP id dgdfgsdfgsd.97.2009.10.06.07.37.32;\r\n\tTue, 06 Oct 2009 07:37:32 -0700 (PDT)\r\nFrom: apache@bago.org\r\nTo: apache@bago.org\r\n\r\nbody\r\nprova altered\r\n";

        Mail mail = process(message);
        
        Optional<String> attr = AttributeUtils.getValueAndCastFromMail(mail, DKIMVerify.DKIM_AUTH_RESULT, String.class);
        assertThat(attr)
            .hasValueSatisfying(result -> assertThat(result).startsWith("neutral"));
    }

    private Mail process(String message) throws Exception {
        Mailet mailet = new DKIMVerify((new MockPublicKeyRecordRetriever(
            "v=DKIM1; k=rsa; p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDYDaYKXzwVYwqWbLhmuJ66aTAN8wmDR+rfHE8HfnkSOax0oIoTM5zquZrTLo30870YMfYzxwfB6j/Nz3QdwrUD/t0YMYJiUKyWJnCKfZXHJBJ+yfRHr7oW+UW3cVo9CG2bBfIxsInwYe175g9UjyntJpWueqdEIo1c2bhv9Mp66QIDAQAB;",
            "selector", "example.com")));

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(FakeMailContext.defaultContext())
                .build();

        mailet.init(mci);

        Mail mail = FakeMail.builder()
            .name("test")
            .mimeMessage(new MimeMessage(Session
                .getDefaultInstance(new Properties()),
                new ByteArrayInputStream(message.getBytes())))
            .build();

        mailet.service(mail);
        return mail;
    }
}
