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

import java.io.ByteArrayInputStream;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.base.Charsets;

public class ReplaceContentTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private ReplaceContent mailet;
    private FakeMailetConfig mailetConfig;

    @Before
    public void setup() {
        mailet = new ReplaceContent();
        mailetConfig = new FakeMailetConfig("Test", FakeMailContext.defaultContext());
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(mailet.getMailetInfo()).isEqualTo("ReplaceContent");
    }

    @Test
    public void serviceShouldThrowWhenPatternIsLessThanTwoCharacters() throws Exception {
        mailetConfig.setProperty("subjectPattern", "a");
        expectedException.expect(MailetException.class);

        mailet.init(mailetConfig);
    }

    @Test
    public void serviceShouldThrowWhenPatternDoesNotStartWithSlash() throws Exception {
        mailetConfig.setProperty("subjectPattern", "abc/");
        expectedException.expect(MailetException.class);

        mailet.init(mailetConfig);
    }

    @Test
    public void serviceShouldThrowWhenPatternDoesNotEndWithSlash() throws Exception {
        mailetConfig.setProperty("subjectPattern", "/abc");
        expectedException.expect(MailetException.class);

        mailet.init(mailetConfig);
    }

    @Test
    public void serviceShouldUnescapeCarriageReturn() throws Exception {
        mailetConfig.setProperty("subjectPattern", "/a/\\\\r/i/");

        mailet.init(mailetConfig);
        assertThat(mailet.replaceConfig.getSubjectReplacingUnits()).containsOnly(new ReplacingPattern(Pattern.compile("a"), false, "\\\r"));
    }

    @Test
    public void serviceShouldUnescapeLineBreak() throws Exception {
        mailetConfig.setProperty("subjectPattern", "/a/\\\\n/i/");

        mailet.init(mailetConfig);
        assertThat(mailet.replaceConfig.getSubjectReplacingUnits()).containsOnly(new ReplacingPattern(Pattern.compile("a"), false, "\\\n"));
    }

    @Test
    public void serviceShouldUnescapeTabReturn() throws Exception {
        mailetConfig.setProperty("subjectPattern", "/a/\\\\t/i/");

        mailet.init(mailetConfig);
        assertThat(mailet.replaceConfig.getSubjectReplacingUnits()).containsOnly(new ReplacingPattern(Pattern.compile("a"), false, "\\\t"));
    }

    @Test
    public void serviceShouldReplaceSubjectWhenMatching() throws Exception {
        mailetConfig.setProperty("subjectPattern", "/prova/PROVA/i/,/a/e//,/o/o/i/");
        mailetConfig.setProperty("bodyPattern", "/prova/PROVA/i/," + "/a/e//,"
                + "/o/o/i/,/\\u00E8/e'//," + "/prova([^\\/]*?)ble/X$1Y/im/,"
                + "/X(.\\n)Y/P$1Q//," + "/\\/\\/,//");
        mailet.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("una prova");
        message.setText("Sto facendo una prova di scrittura/ \u00E8 solo una prova.\n"
                + "Bla bla bla bla.\n");

        Mail mail = new FakeMail(message);
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("une PRoVA");
    }

    @Test
    public void serviceShouldReplaceBodyWhenMatching() throws Exception {
        mailetConfig.setProperty("subjectPattern", "/prova/PROVA/i/,/a/e//,/o/o/i/");
        mailetConfig.setProperty("bodyPattern", "/prova/PROVA/i/," + "/a/e//,"
                + "/o/o/i/,/\\u00E8/e'//," + "/prova([^\\/]*?)ble/X$1Y/im/,"
                + "/X(.\\n)Y/P$1Q//," + "/\\/\\/,//");
        mailet.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("una prova");
        message.setText("Sto facendo una prova di scrittura/ \u00E8 solo una prova.\n"
                + "Bla bla bla bla.\n");

        Mail mail = new FakeMail(message);
        mailet.service(mail);

        assertThat(mail.getMessage().getContent()).isEqualTo("Sto fecendo une PRoVA di scritture, e' solo une P.\n"
                + "Q ble ble ble.\n");
    }

    @Test
    public void serviceShouldReplaceSubjectWhenConfigurationFromFile() throws Exception {
        mailetConfig.setProperty("subjectPatternFile", "#/org/apache/james/mailet/standard/mailets/replaceSubject.patterns");
        mailet.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("re: r:ri:una prova");
        message.setText("Sto facendo una prova di scrittura/ \u00E8 solo una prova.\n"
                + "Bla bla bla bla.\n");

        Mail mail = new FakeMail(message);
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("Re: Re: Re: una prova");
    }

    @Test
    public void serviceShouldRemoveOrAddTextInBody() throws Exception {
        mailetConfig.setProperty("bodyPattern", "/--messaggio originale--/<quote>/i/,"
                + "/<quote>(.*)(\\r\\n)([^>]+)/<quote>$1$2>$3/imrs/,"
                + "/<quote>\\r\\n//im/");
        mailet.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("una prova");
        message.setText("Prova.\r\n" + "\r\n" + "--messaggio originale--\r\n"
                + "parte del\r\n" + "messaggio\\ che\\0 deve0 essere\r\n"
                + "quotato. Vediamo se\r\n" + "ce la fa.");

        Mail mail = new FakeMail(message);
        mailet.service(mail);

        assertThat(mail.getMessage().getContent()).isEqualTo("Prova.\r\n" + "\r\n" + ">parte del\r\n"
                + ">messaggio\\ che\\0 deve0 essere\r\n"
                + ">quotato. Vediamo se\r\n" + ">ce la fa.");
    }


    @Test
    public void serviceShouldReplaceBodyWhenMatchingASCIICharacter() throws Exception {
        mailetConfig.setProperty("bodyPattern", "/\\u2026/...//");
        mailet.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("una prova");
        message.setText("Prova \u2026 di replace \u2026");

        Mail mail = new FakeMail(message);
        mailet.service(mail);

        assertThat(mail.getMessage().getContent()).isEqualTo("Prova ... di replace ...");
    }

    @Test
    public void serviceShouldReplaceBodyWhenMatchingCharset() throws Exception {
        String messageSource = "Content-Type: text/plain; charset=\"iso-8859-1\"\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "=93prova=94 con l=92apice";

        mailetConfig.setProperty("bodyPattern", "/[\\u2018\\u2019\\u201A]/'//,"
                + "/[\\u201C\\u201D\\u201E]/\"//," + "/[\\x91\\x92\\x82]/'//,"
                + "/[\\x93\\x94\\x84]/\"//," + "/\\x85/...//," + "/\\x8B/<//,"
                + "/\\x9B/>//," + "/\\x96/-//," + "/\\x97/--//,");
        mailet.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()),
                new ByteArrayInputStream(messageSource.getBytes()));

        Mail mail = new FakeMail(message);
        mailet.service(mail);

        assertThat(mail.getMessage().getContent()).isEqualTo("\"prova\" con l'apice");
    }

    @Test
    public void serviceShouldSetContenTypeWhenInitialized() throws Exception {
        mailetConfig.setProperty("subjectPattern", "/prova/PROVA/i/,/a/e//,/o/o/i/");
        mailetConfig.setProperty("charset", Charsets.UTF_8.name());
        mailet.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("una prova");
        message.setText("Sto facendo una prova di scrittura/ \u00E8 solo una prova.\n"
                + "Bla bla bla bla.\n");

        Mail mail = new FakeMail(message);
        mailet.service(mail);

        assertThat(mail.getMessage().getContentType()).isEqualTo("text/plain; charset=UTF-8");
    }
}
