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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

class AddFooterTest {

    private static final String MY_FOOTER = "my footer";

    private Mailet mailet;
    

    static class CharsetTuples implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            //javamail has its own charset handling logic, it needs to be exercised
            List<String> charsetNamesToTest = Lists.newArrayList(
                    "ANSI_X3.4-1968", 
                    "iso-ir-6", 
                    "ANSI_X3.4-1986", 
                    "ISO_646.irv:1991", 
                    "ASCII", 
                    "ISO646-US", 
                    "US-ASCII",
                    "us", 
                    "IBM367", 
                    "cp367",
                    "csASCII");
            return charsetNamesToTest.stream().flatMap(from -> charsetNamesToTest.stream().map(to -> Arguments.of(from, to)));
        }
    }

    @BeforeEach
    void setup() {
        mailet = new AddFooter();
    }

    @ParameterizedTest
    @ArgumentsSource(CharsetTuples.class)
    void shouldAddFooterWhenQuotedPrintableTextPlainMessage(String javaCharset, String javaMailCharset) throws MessagingException, IOException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("text", "------ " + MY_FOOTER + " à/€ ------")
                .build();
        mailet.init(mailetConfig);
        
        String quotedPrintableTextPlainMessage = Joiner.on("\r\n").join(
                "Subject: test",
                "Content-Type: text/plain; charset=ISO-8859-15",
                "MIME-Version: 1.0",
                "Content-Transfer-Encoding: quoted-printable",
                "",
                "Test=E0 and one =A4",
                "");

        String expectedFooter = "------ " + MY_FOOTER + " =E0/=A4 ------";

        Mail mail = FakeMail.fromMime(quotedPrintableTextPlainMessage, javaCharset, javaMailCharset);
        mailet.service(mail);

        assertThat(MailUtil.toString(mail, javaCharset)).endsWith(expectedFooter);
    }

    @ParameterizedTest
    @ArgumentsSource(CharsetTuples.class)
    void shouldEnsureCarriageReturnWhenAddFooterWithTextPlainMessage(String javaCharset, String javaMailCharset) throws MessagingException, IOException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("text", "------ " + MY_FOOTER + " à/€ ------")
                .build();
        mailet.init(mailetConfig);
        
        String quotedPrintableTextPlainMessage = Joiner.on("\r\n").join(
                "Subject: test",
                "Content-Type: text/plain; charset=ISO-8859-15",
                "MIME-Version: 1.0",
                "Content-Transfer-Encoding: quoted-printable",
                "",
                "Test=E0 and one =A4");

        String expectedFooter = "------ " + MY_FOOTER + " =E0/=A4 ------";
        

        Mail mail = FakeMail.fromMime(quotedPrintableTextPlainMessage, javaCharset, javaMailCharset);
        mailet.service(mail);

        assertThat(MailUtil.toString(mail, javaCharset)).endsWith("\r\n" + expectedFooter);
    }

    @ParameterizedTest
    @ArgumentsSource(CharsetTuples.class)
    void shouldNotAddFooterWhenUnsupportedEncoding(String javaCharset, String javaMailCharset) throws MessagingException, IOException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("text", "------ " + MY_FOOTER + " à/€ ------")
                .build();
        mailet.init(mailetConfig);
        
        String quotedPrintableTextPlainMessage = Joiner.on("\r\n").join(
                "Subject: test",
                "Content-Type: text/plain; charset=UNSUPPORTED_ENCODING",
                "MIME-Version: 1.0",
                "Content-Transfer-Encoding: quoted-printable",
                "", 
                "Test=E0 and one",
                "");

        Mail mail = FakeMail.fromMime(quotedPrintableTextPlainMessage, javaCharset, javaMailCharset);
        mailet.service(mail);

        assertThat(MailUtil.toString(mail, javaCharset)).doesNotContain(MY_FOOTER);
    }

    @ParameterizedTest
    @ArgumentsSource(CharsetTuples.class)
    void shouldNotAddFooterWhenUnsupportedTextContentType(String javaCharset, String javaMailCharset) throws MessagingException, IOException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("text", "------ " + MY_FOOTER + " à/€ ------")
                .build();
        mailet.init(mailetConfig);
        
        String quotedPrintableTextPlainMessage = Joiner.on("\r\n").join(
                "Subject: test",
                "Content-Type: text/calendar; charset=ASCII",
                "MIME-Version: 1.0",
                "Content-Transfer-Encoding: quoted-printable",
                "", 
                "Test=E0 and one",
                "");

        Mail mail = FakeMail.fromMime(quotedPrintableTextPlainMessage, javaCharset, javaMailCharset);
        mailet.service(mail);

        assertThat(MailUtil.toString(mail, javaCharset)).isEqualTo(quotedPrintableTextPlainMessage);
    }
    
    /*
     * Test for JAMES-443
     * This should not add the header and should leave the multipart/mixed Content-Type intact
     */
    @ParameterizedTest
    @ArgumentsSource(CharsetTuples.class)
    void shouldNotAddFooterWhenNestedUnsupportedMultipart(String javaCharset, String javaMailCharset) throws MessagingException, IOException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("text", "------ " + MY_FOOTER + " à/€ ------")
                .build();
        mailet.init(mailetConfig);
        
        String quotedPrintableMultipartMixedMessage = Joiner.on("\r\n").join(
                "MIME-Version: 1.0",
                "Content-Type: multipart/mixed; boundary=\"===============0204599088==\"",
                "",
                "This is a cryptographically signed message in MIME format.",
                "",
                "--===============0204599088==",
                "Content-Type: multipart/unsupported; boundary=\"------------ms050404020900070803030808\"",
                "",
                "--------------ms050404020900070803030808",
                "Content-Type: text/plain; charset=ISO-8859-1",
                "",
                "test",
                "",
                "--------------ms050404020900070803030808--",
                "",
                "--===============0204599088==--",
                "");

        Mail mail = FakeMail.fromMime(quotedPrintableMultipartMixedMessage, javaCharset, javaMailCharset);
        mailet.service(mail);

        assertThat(MailUtil.toString(mail, javaCharset)).isEqualTo(quotedPrintableMultipartMixedMessage);
    }
    
    /*
     * Test for JAMES-368
     */
    @ParameterizedTest
    @ArgumentsSource(CharsetTuples.class)
    void shouldAddFooterWhenMultipartRelatedHtmlMessage(String javaCharset, String javaMailCharset) throws MessagingException, IOException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("text", "------ " + MY_FOOTER + " à/€ ------")
                .build();
        mailet.init(mailetConfig);
        
        String htmlMultipartRelatedMessagePart1 = Joiner.on("\r\n").join(
                "MIME-Version: 1.0",
                "Subject: test",
                "Content-Type: multipart/related;",
                "  boundary=\"------------050206010102010306090507\"",
                "",
                "--------------050206010102010306090507",
                "Content-Type: text/html; charset=ISO-8859-15",
                "Content-Transfer-Encoding: quoted-printable",
                "",
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">",
                "<html>",
                "<head>",
                "<meta content=3D\"text/html;charset=3DISO-8859-15\" http-equiv=3D\"Content-Typ=",
                "e\">",
                "</head>",
                "<body bgcolor=3D\"#ffffff\" text=3D\"#000000\">",
                "<br>",
                "<div class=3D\"moz-signature\">-- <br>",
                "<img src=3D\"cid:part1.02060605.123@zzz.com\" border=3D\"0\"></div>",
                "");
        
        String htmlMultipartRelatedMessagePart2 = Joiner.on("\r\n").join(
                "</body>",
                "</html>",
                "",
                "--------------050206010102010306090507",
                "Content-Type: image/gif",
                "Content-Transfer-Encoding: base64",
                "Content-ID: <part1.02060605.123@zzz.com>",
                "Content-Disposition: inline;",
                "",
                "YQ==",
                "--------------050206010102010306090507--",
                "");

        String expectedFooter = "<br />------ " + MY_FOOTER + " =E0/=A4 ------";

        Mail mail = FakeMail.fromMime(htmlMultipartRelatedMessagePart1 + htmlMultipartRelatedMessagePart2, javaCharset, javaMailCharset);
        mailet.service(mail);

        assertThat(MailUtil.toString(mail, javaCharset)).contains(expectedFooter);
    }

    @ParameterizedTest
    @ArgumentsSource(CharsetTuples.class)
    void shouldAddFooterWhenMultipartAlternivateMessage(String javaCharset, String javaMailCharset) throws MessagingException,
            IOException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("text", "------ " + MY_FOOTER + " à/€ ------")
                .build();
        mailet.init(mailetConfig);
        
        String multipartAlternativeMessage = Joiner.on("\r\n").join(
                "Subject: test",
                "Content-Type: multipart/alternative;",
                "    boundary=\"--==--\"",
                "MIME-Version: 1.0",
                "",
                "----==--",
                "Content-Type: text/plain;",
                "    charset=\"ISO-8859-15\"",
                "Content-Transfer-Encoding: quoted-printable",
                "",
                "Test=E0 and @=80",
                "",
                "----==--",
                "Content-Type: text/html;",
                "    charset=\"CP1252\"",
                "Content-Transfer-Encoding: quoted-printable",
                "",
                "<html><body>test =80 ss</body></html>",
                "----==----"
                );
        
        Mail mail = FakeMail.fromMime(multipartAlternativeMessage, javaCharset, javaMailCharset);
        mailet.service(mail);
        
        assertThat(MailUtil.toString(mail, javaCharset)).matches("(.|\n|\r)*" + MY_FOOTER + "(.|\n|\r)*" + MY_FOOTER + "(.|\n|\r)*");
    }

    @ParameterizedTest
    @ArgumentsSource(CharsetTuples.class)
    void shouldAddFooterWhenHtmlMessageWithMixedCaseBodyTag(String javaCharset, String javaMailCharset) throws MessagingException,
            IOException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("text", "------ " + MY_FOOTER + " à/€ ------")
                .build();
        mailet.init(mailetConfig);
        
        String htmlMessage = Joiner.on("\r\n").join(
                "Subject: test",
                "MIME-Version: 1.0",
                "Content-Type: text/html;",
                "    charset=\"CP1252\"",
                "Content-Transfer-Encoding: quoted-printable",
                "",
                "<html><body>test =80 ss</bOdY></html>",
                ""
                );
        
        Mail mail = FakeMail.fromMime(htmlMessage, javaCharset, javaMailCharset);
        mailet.service(mail);

        String htmlContent = "<html><body>test =80 ss<br />------ " + MY_FOOTER + " =E0/=80 ------</bOdY></html>";
        assertThat(MailUtil.toString(mail, javaCharset)).contains(htmlContent);
    }

    @ParameterizedTest
    @ArgumentsSource(CharsetTuples.class)
    void shouldAddFooterWhenHtmlMessageWithNoBodyTag(String javaCharset, String javaMailCharset) throws MessagingException,
            IOException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("text", "------ " + MY_FOOTER + " à/€ ------")
                .build();
        mailet.init(mailetConfig);
        
        String htmlMessage = Joiner.on("\r\n").join(
                "Subject: test",
                "MIME-Version: 1.0",
                "Content-Type: text/html;",
                "    charset=\"CP1252\"",
                "Content-Transfer-Encoding: quoted-printable",
                "",
                "<html><body>test =80 ss",
                ""
                );
        
        Mail mail = FakeMail.fromMime(htmlMessage, javaCharset, javaMailCharset);
        mailet.service(mail);

        String expectedFooter = "<br />------ " + MY_FOOTER + " =E0/=80 ------";
        
        assertThat(MailUtil.toString(mail, javaCharset)).endsWith(expectedFooter);
    }
    

    private MimeMessage reparse(Mail mail) throws Exception {
        return new MimeMessage(Session.getDefaultInstance(new Properties()),
            new ByteArrayInputStream(MailUtil.toString(mail, "UTF-8").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldKeepFooterCharactersThatThePartCharsetCannotRepresent() throws Exception {
        String footer = "------ " + MY_FOOTER + " à/€ ------";
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", footer)
            .build());

        String message = Joiner.on("\r\n").join(
            "Subject: test",
            "MIME-Version: 1.0",
            "Content-Type: text/plain",
            "",
            "hello",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        mailet.service(mail);

        MimeMessage reparsed = reparse(mail);
        assertThat((String) reparsed.getContent()).isEqualTo("hello\r\n" + footer);
        assertThat(new ContentType(reparsed.getContentType()).getParameter("charset")).isEqualTo("UTF-8");
    }

    @Test
    void shouldKeepFooterCharactersThatAnIso88591PartCannotRepresent() throws Exception {
        String footer = "日本語 ✓ €";
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", footer)
            .build());

        String message = Joiner.on("\r\n").join(
            "Subject: test",
            "MIME-Version: 1.0",
            "Content-Type: multipart/mixed; boundary=\"BOUNDARY\"",
            "",
            "--BOUNDARY",
            "Content-Type: text/plain; charset=ISO-8859-1",
            "Content-Transfer-Encoding: quoted-printable",
            "",
            "caf=E9",
            "--BOUNDARY--",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        mailet.service(mail);

        MimeMultipart multipart = (MimeMultipart) reparse(mail).getContent();
        assertThat((String) multipart.getBodyPart(0).getContent()).isEqualTo("café\r\n" + footer);
    }

    @Test
    void shouldKeepThePartCharsetWhenItCanRepresentTheFooter() throws Exception {
        String footer = "------ " + MY_FOOTER + " à ------";
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", footer)
            .build());

        String message = Joiner.on("\r\n").join(
            "Subject: test",
            "MIME-Version: 1.0",
            "Content-Type: text/plain; charset=ISO-8859-1",
            "",
            "hello",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        mailet.service(mail);

        MimeMessage reparsed = reparse(mail);
        assertThat((String) reparsed.getContent()).isEqualTo("hello\r\n" + footer);
        assertThat(new ContentType(reparsed.getContentType()).getParameter("charset")).isEqualTo("ISO-8859-1");
    }

    @Test
    void shouldKeepFooterIntactInUtf16TextPart() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", "-- \r\nfooter\r\n")
            .build());
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setText("ഊഊഊഊ text", "UTF-16");
        message.saveChanges();
        Mail mail = FakeMail.from(message);

        mailet.service(mail);

        assertThat((String) reparse(mail).getContent()).isEqualTo("ഊഊഊഊ text\r\n-- \r\nfooter\r\n");
    }

    @Test
    void shouldInsertHtmlFooterBeforeTheLastClosingBodyTag() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());

        String message = Joiner.on("\r\n").join(
            "Subject: test",
            "Content-Type: text/html; charset=utf-8",
            "MIME-Version: 1.0",
            "Content-Transfer-Encoding: 8bit",
            "",
            "<html><body><p>Reply</p><blockquote><html><body>quoted</body></html></blockquote>",
            "</body></html>",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        mailet.service(mail);

        assertThat((String) mail.getMessage().getContent())
            .contains("quoted</body></html></blockquote>")
            .contains("<br />" + MY_FOOTER + "</body></html>");
    }

    @Test
    void shouldNotInsertHtmlFooterInAComment() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());

        String message = Joiner.on("\r\n").join(
            "Subject: test",
            "Content-Type: text/html; charset=utf-8",
            "MIME-Version: 1.0",
            "Content-Transfer-Encoding: 8bit",
            "",
            "<html><body><p>Hi</p><!-- </BODY> --></body></html>",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        mailet.service(mail);

        assertThat((String) mail.getMessage().getContent())
            .contains("<!-- </BODY> --><br />" + MY_FOOTER + "</body></html>");
    }

    @Test
    void shouldNotFailWhenTextPartContentTypeIsUnparsable() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());

        String message = Joiner.on("\r\n").join(
            "Subject: test",
            "Content-Type: text/plain; charset=utf-8\f",
            "",
            "hello",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        assertThatCode(() -> mailet.service(mail)).doesNotThrowAnyException();
        assertThat(MailUtil.toString(mail, "UTF-8")).doesNotContain(MY_FOOTER);
    }

    @Test
    void shouldNotFailWhenNestedTextPartContentTypeIsUnparsable() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());

        String message = Joiner.on("\r\n").join(
            "Subject: test",
            "MIME-Version: 1.0",
            "Content-Type: multipart/mixed; boundary=\"BOUNDARY\"",
            "",
            "--BOUNDARY",
            "Content-Type: text/plain; charset=utf-8\f",
            "",
            "hello",
            "--BOUNDARY--",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        assertThatCode(() -> mailet.service(mail)).doesNotThrowAnyException();
        assertThat(MailUtil.toString(mail, "UTF-8")).doesNotContain(MY_FOOTER);
    }

    @Test
    void shouldNotFailWhenMultipartMixedHasNoBodyPart() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());

        String message = Joiner.on("\r\n").join(
            "Subject: test",
            "Content-Type: multipart/mixed; bou*dary=\"out/mixed; bou]]ter\"",
            "",
            "--omultipaon: 1.0",
            "Con/sce: multipar--",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        assertThatCode(() -> mailet.service(mail)).doesNotThrowAnyException();
        assertThat(MailUtil.toString(mail, "UTF-8")).doesNotContain(MY_FOOTER);
    }

    @Test
    void shouldNotFailWhenMultipartRelatedHasNoBodyPart() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());

        String message = Joiner.on("\r\n").join(
            "Subject: test",
            "Content-Type: multipart/related; bou*dary=\"out/mixed; bou]]ter\"",
            "",
            "--omultipaon: 1.0",
            "Con/sce: multipar--",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        assertThatCode(() -> mailet.service(mail)).doesNotThrowAnyException();
        assertThat(MailUtil.toString(mail, "UTF-8")).doesNotContain(MY_FOOTER);
    }

    @Test
    void shouldKeepMessageWritableWhenANestedMultipartHasNoPart() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());

        String message = Joiner.on("\r\n").join(
            "From: sender@example.com",
            "MIME-Version: 1.0",
            "Content-Type: multipart/mixed; boundary=\"outer\"",
            "",
            "--outer",
            "Content-Type: text/plain",
            "",
            "hello",
            "--outer",
            "Content-Type: multipart/related; boundary=\"zz\"",
            "",
            "--zz",
            "Content-Type: text/plain",
            "--outer--",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        mailet.service(mail);

        assertThat(MailUtil.toString(mail, "UTF-8")).contains(MY_FOOTER);
    }

    @Test
    void shouldNotBringBackAttachmentsRemovedByAnEarlierMailet() throws Exception {
        assertFooterAddedWithoutAttachmentAfterStripAttachment(Joiner.on("\r\n").join(
            "From: sender@example.com",
            "MIME-Version: 1.0",
            "Content-Type: multipart/mixed; boundary=\"outer\"",
            "",
            "--outer",
            "Content-Type: text/plain",
            "",
            "hello",
            "--outer",
            "Content-Type: application/octet-stream",
            "Content-Disposition: attachment; filename=\"report.exe\"",
            "",
            "PAYLOAD",
            "--outer--",
            ""));
    }

    @Test
    void shouldNotBringBackNestedAttachmentsRemovedByAnEarlierMailet() throws Exception {
        assertFooterAddedWithoutAttachmentAfterStripAttachment(Joiner.on("\r\n").join(
            "From: sender@example.com",
            "MIME-Version: 1.0",
            "Content-Type: multipart/mixed; boundary=\"outer\"",
            "",
            "--outer",
            "Content-Type: multipart/mixed; boundary=\"inner\"",
            "",
            "--inner",
            "Content-Type: text/plain",
            "",
            "hello",
            "--inner",
            "Content-Type: application/octet-stream",
            "Content-Disposition: attachment; filename=\"report.exe\"",
            "",
            "PAYLOAD",
            "--inner--",
            "--outer--",
            ""));
    }

    private void assertFooterAddedWithoutAttachmentAfterStripAttachment(String message) throws Exception {
        Mailet stripAttachment = new StripAttachment();
        stripAttachment.init(FakeMailetConfig.builder()
            .mailetName("StripAttachment")
            .setProperty("remove", "matched")
            .setProperty("pattern", ".*\\.exe")
            .build());
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        stripAttachment.service(mail);
        mailet.service(mail);

        assertThat(MailUtil.toString(mail, "UTF-8"))
            .contains("hello", MY_FOOTER)
            .doesNotContain("PAYLOAD", "report.exe");
    }

    @Test
    void shouldNotBringBackAPartRemovedByAnEarlierMailet() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());
        Mail mail = FakeMail.fromMime(Joiner.on("\r\n").join(
            "From: sender@example.com",
            "MIME-Version: 1.0",
            "Content-Type: multipart/mixed; boundary=\"outer\"",
            "",
            "--outer",
            "Content-Type: text/plain",
            "",
            "hello",
            "--outer",
            "Content-Type: application/octet-stream",
            "Content-Disposition: attachment; filename=\"report.exe\"",
            "",
            "PAYLOAD",
            "--outer--",
            ""), "UTF-8", "UTF-8");
        MimeMultipart multipart = (MimeMultipart) mail.getMessage().getContent();
        multipart.removeBodyPart(1);
        mail.getMessage().setContent(multipart);
        mail.getMessage().saveChanges();

        mailet.service(mail);

        assertThat(MailUtil.toString(mail, "UTF-8"))
            .contains("hello", MY_FOOTER)
            .doesNotContain("PAYLOAD", "report.exe");
    }

    @Test
    void shouldAddFooterToAMultipartSetProgrammatically() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("hello");
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setContent(multipart);
        message.saveChanges();
        Mail mail = FakeMail.builder().name("mail").mimeMessage(message).build();

        mailet.service(mail);

        assertThat(MailUtil.toString(mail, "UTF-8")).contains("hello", MY_FOOTER);
    }

    private static String nestedMessage(String multipartType, int depth) {
        StringBuilder message = new StringBuilder("Subject: deep\r\nMIME-Version: 1.0\r\n");
        for (int i = 0; i < depth; i++) {
            message.append("Content-Type: multipart/").append(multipartType).append("; boundary=b").append(i)
                .append("\r\n\r\n--b").append(i).append("\r\n");
        }
        message.append("Content-Type: text/plain\r\n\r\nhello\r\n");
        for (int i = depth - 1; i >= 0; i--) {
            message.append("--b").append(i).append("--\r\n");
        }
        return message.toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"alternative", "mixed", "related"})
    void shouldNotOverflowOnDeeplyNestedMultipart(String multipartType) throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());

        Mail mail = FakeMail.fromMime(nestedMessage(multipartType, 10_000), "UTF-8", "UTF-8");

        assertTimeoutPreemptively(Duration.ofSeconds(10),
            () -> assertThatCode(() -> mailet.service(mail)).doesNotThrowAnyException());
        assertThat(MailUtil.toString(mail, "UTF-8")).doesNotContain(MY_FOOTER);
    }

    @Test
    void shouldStillAddFooterToAReasonablyNestedPart() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());

        Mail mail = FakeMail.fromMime(nestedMessage("mixed", 10), "UTF-8", "UTF-8");

        mailet.service(mail);

        assertThat(MailUtil.toString(mail, "UTF-8")).contains(MY_FOOTER);
    }

    @Test
    void shouldNotOverflowWhenADeeplyNestedMultipartIsASiblingOfTheFooterTarget() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", MY_FOOTER)
            .build());

        String deepMessage = nestedMessage("mixed", 10_000);
        String message = Joiner.on("\r\n").join(
            "Subject: test",
            "MIME-Version: 1.0",
            "Content-Type: multipart/alternative; boundary=top",
            "",
            "--top",
            "Content-Type: text/plain",
            "",
            "hello",
            "--top",
            deepMessage.substring(deepMessage.indexOf("Content-Type")) + "--top--",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        assertTimeoutPreemptively(Duration.ofSeconds(10),
            () -> assertThatCode(() -> mailet.service(mail)).doesNotThrowAnyException());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Content-Type: text/plain", "Content-Type: text/plain; charset=us-ascii"})
    void shouldSwitchAnAsciiPartToUtf8WhenTheFooterHoldsLatin1Characters(String contentTypeHeader) throws Exception {
        String footer = "Message confidentiel reçu";
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", footer)
            .build());

        String message = Joiner.on("\r\n").join(
            "Subject: test",
            "MIME-Version: 1.0",
            contentTypeHeader,
            "",
            "hello",
            "");
        Mail mail = FakeMail.fromMime(message, "UTF-8", "UTF-8");

        mailet.service(mail);

        MimeMessage reparsed = reparse(mail);
        assertThat((String) reparsed.getContent()).isEqualTo("hello\r\n" + footer);
        assertThat(new ContentType(reparsed.getContentType()).getParameter("charset")).isEqualTo("UTF-8");
    }

    @Test
    void shouldNotTurnInlineBoundaryTextIntoADelimiterWhenReencoding() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", "FOOTER-MARK")
            .build());
        String text = "This line is plain ASCII, long enough to keep the part mostly ASCII.\r\n"
            + "é".repeat(25) + "--outer\r\n"
            + "last line\r\n";
        String message = "Subject: test\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: multipart/mixed; boundary=\"outer\"\r\n"
            + "\r\n"
            + "--outer\r\n"
            + "Content-Type: text/plain; charset=UTF-8\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + text
            + "--outer\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "\r\n"
            + "attachment\r\n"
            + "--outer--\r\n";
        Session session = Session.getDefaultInstance(new Properties());
        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(new MimeMessage(session, new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))))
            .build();

        mailet.service(mail);

        MimeMultipart multipart = (MimeMultipart) reparse(mail).getContent();
        assertThat(multipart.getCount()).isEqualTo(2);
        assertThat((String) multipart.getBodyPart(0).getContent())
            .isEqualTo(text + "FOOTER-MARK");
    }

    @Test
    void shouldNotTurnInlineBoundaryTextOfAnAncestorIntoADelimiterWhenReencoding() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", "FOOTER-MARK")
            .build());
        String text = "This line is plain ASCII, long enough to keep the part mostly ASCII.\r\n"
            + "é".repeat(25) + "--outer\r\n"
            + "last line\r\n";
        String message = "Subject: test\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: multipart/mixed; boundary=\"outer\"\r\n"
            + "\r\n"
            + "--outer\r\n"
            + "Content-Type: multipart/alternative; boundary=\"inner\"\r\n"
            + "\r\n"
            + "--inner\r\n"
            + "Content-Type: text/plain; charset=UTF-8\r\n"
            + "Content-Transfer-Encoding: 8bit\r\n"
            + "\r\n"
            + text
            + "--inner--\r\n"
            + "--outer\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "\r\n"
            + "attachment\r\n"
            + "--outer--\r\n";
        Session session = Session.getDefaultInstance(new Properties());
        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(new MimeMessage(session, new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))))
            .build();

        mailet.service(mail);

        MimeMultipart multipart = (MimeMultipart) reparse(mail).getContent();
        assertThat(multipart.getCount()).isEqualTo(2);
        MimeMultipart alternative = (MimeMultipart) multipart.getBodyPart(0).getContent();
        assertThat(alternative.getCount()).isEqualTo(1);
        assertThat((String) alternative.getBodyPart(0).getContent())
            .isEqualTo(text + "FOOTER-MARK");
    }

    @Test
    void shouldKeepTheOtherContentTypeParametersOfARewrittenMultipart() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("text", "FOOTER-MARK")
            .build());
        Mail mail = FakeMail.fromMime(Joiner.on("\r\n").join(
            "From: sender@example.com",
            "MIME-Version: 1.0",
            "Content-Type: multipart/related; boundary=\"rel\"; type=\"text/plain\"; start=\"<a@b>\"",
            "",
            "--rel",
            "Content-Type: text/plain",
            "Content-ID: <a@b>",
            "",
            "hello",
            "--rel--",
            ""), "UTF-8", "UTF-8");

        mailet.service(mail);

        ContentType contentType = new ContentType(reparse(mail).getContentType());
        assertThat(contentType.getBaseType()).isEqualTo("multipart/related");
        assertThat(contentType.getParameter("type")).isEqualTo("text/plain");
        assertThat(contentType.getParameter("start")).isEqualTo("<a@b>");
        assertThat(contentType.getParameter("boundary")).isNotEqualTo("rel");
        assertThat(MailUtil.toString(mail, "UTF-8")).contains("hello", "FOOTER-MARK");
    }
}
