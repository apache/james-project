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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReplaceContentTest {

    private ReplaceContent mailet;

    @BeforeEach
    void setup() {
        mailet = new ReplaceContent();
    }

    @Test
    void getMailetInfoShouldReturnValue() {
        assertThat(mailet.getMailetInfo()).isEqualTo("ReplaceContent");
    }

    @Test
    void serviceShouldReplaceSubjectWhenMatching() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("subjectPattern", "/test/TEST/i/,/o/a//,/s/s/i/")
                .build();
        mailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("one test"))
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("ane TEsT");
    }

    @Test
    void serviceShouldReplaceBodyWhenMatching() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("bodyPattern", 
                        "/test/TEST/i/," +
                        "/o/a/r/," +
                        "/S/s/r/,/è/e'//," +
                        "/test([^\\/]*?)bla/X$1Y/im/," +
                        "/X(.\\n)Y/P$1Q//," +
                        "/\\/\\/,//")
                .build();
        mailet.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setText("This is one simple test/ è one simple test.\n"
            + "Blo blo blo blo.\n");
        Mail mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getContent()).isEqualTo("This is ane simple TEsT, e' ane simple P.\n"
                + "Q bla bla bla.\n");
    }

    @Test
    void serviceShouldNotLoopWhenCaseInsensitiveAndRepeat() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("bodyPattern", "/a/a/ir/")
                .build();
        mailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("aaa"))
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getContent()).isEqualTo("aaa");
    }

    @Test
    void serviceShouldReplaceSubjectWhenConfigurationFromFile() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("subjectPatternFile", "#/org/apache/james/mailet/standard/mailets/replaceSubject.patterns")
                .build();
        mailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("re: r:ri:one test"))
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("Re: Re: Re: one test");
    }

    @Test
    void serviceShouldRemoveOrAddTextInBody() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("bodyPattern", "/--original message--/<quote>/i/,"
                        + "/<quote>(.*)(\\r\\n)([^>]+)/<quote>$1$2>$3/imrs/,"
                        + "/<quote>\\r\\n//im/")
                .build();
        mailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("Test.\r\n" + "\r\n" + "--original message--\r\n"
                        + "part of\r\n" + "message\\ that\\0 must0 be\r\n"
                        + "quoted. Let's see if\r\n" + "he can do it."))
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getContent()).isEqualTo("Test.\r\n" + "\r\n" + ">part of\r\n"
                + ">message\\ that\\0 must0 be\r\n"
                + ">quoted. Let's see if\r\n" + ">he can do it.");
    }


    @Test
    void serviceShouldReplaceBodyWhenMatchingASCIICharacter() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("bodyPattern", "/\\u2026/.../r/")
                .build();
        mailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("one test")
                    .setText("Replacement … one test …"))
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getContent()).isEqualTo("Replacement ... one test ...");
    }

    @Test
    void serviceShouldReplaceBodyWhenMatchingCharset() throws Exception {
        String messageSource = "Content-Type: text/plain; charset=\"iso-8859-1\"\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "=93test=94 with th=92 apex";

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("bodyPattern", "/[\\u2018\\u2019\\u201A]/'//,"
                        + "/[\\u201C\\u201D\\u201E]/\"//," + "/[\\x91\\x92\\x82]/'//,"
                        + "/[\\x93\\x94\\x84]/\"/r/," + "/\\x85/...//," + "/\\x8B/<//,"
                        + "/\\x9B/>//," + "/\\x96/-//," + "/\\x97/--//,")
                .build();
        mailet.init(mailetConfig);

        MimeMessage message = MimeMessageUtil.mimeMessageFromString(messageSource);

        Mail mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getContent()).isEqualTo("\"test\" with th' apex");
    }

    @Test
    void serviceShouldSetContenTypeWhenInitialized() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("subjectPattern", "/test/TEST/i/,/o/a//,/s/s/i/")
                .setProperty("charset", StandardCharsets.UTF_8.name())
                .build();
        mailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("one test")
                    .setText("This is one simple test/ è one simple test.\n"
                        + "Blo blo blo blo.\n"))
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getContentType()).isEqualTo("text/plain; charset=UTF-8");
    }
}
