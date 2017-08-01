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
import static org.assertj.guava.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.io.IOUtils;
import org.apache.james.transport.mailets.StripAttachment.OutputFileName;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;

public class StripAttachmentTest {

    private static final String EXPECTED_ATTACHMENT_CONTENT = "\u0023\u00A4\u00E3\u00E0\u00E9";
    private static final Optional<String> ABSENT_MIME_TYPE = Optional.absent();
    private static final String CONTENT_TRANSFER_ENCODING_VALUE ="8bit";

    public static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_DEFAULT = "application/octet-stream; charset=utf-8";
    public static final String TEXT_CALENDAR_CHARSET_UTF_8 = "text/calendar; charset=utf-8";
    public static final String TEXT_HTML_CHARSET_UTF_8 = "text/html; charset=utf-8";

    private static MimeMessageBuilder.Header[] TEXT_HEADERS = {
        new MimeMessageBuilder.Header(CONTENT_TRANSFER_ENCODING, CONTENT_TRANSFER_ENCODING_VALUE),
        new MimeMessageBuilder.Header(CONTENT_TYPE, CONTENT_TYPE_DEFAULT)
    };

    private static MimeMessageBuilder.Header[] HTML_HEADERS = {
        new MimeMessageBuilder.Header(CONTENT_TRANSFER_ENCODING, CONTENT_TRANSFER_ENCODING_VALUE),
        new MimeMessageBuilder.Header(CONTENT_TYPE, TEXT_HTML_CHARSET_UTF_8)
    };

    private static MimeMessageBuilder.Header[] CALENDAR_HEADERS = {
        new MimeMessageBuilder.Header(CONTENT_TRANSFER_ENCODING, CONTENT_TRANSFER_ENCODING_VALUE),
        new MimeMessageBuilder.Header(CONTENT_TYPE, TEXT_CALENDAR_CHARSET_UTF_8)
    };

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String folderPath;

    @Before
    public void setUp() throws IOException {
        folderPath = folder.getRoot().getPath() + "/";
    }

    @After
    public void tearDown() throws IOException {
        folder.delete();
    }

    @Test
    public void serviceShouldNotModifyMailWhenNotMultipart() throws MessagingException, IOException {
        Mailet mailet = initMailet();
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText("simple text")
            .build();

        MimeMessage expectedMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText("simple text")
            .build();

        Mail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();
        Mail expectedMail = FakeMail.builder()
                .mimeMessage(expectedMessage)
                .build();

        mailet.service(mail);

        assertThat(mail).isEqualToIgnoringGivenFields(expectedMail, "msg");
        assertThat(mail.getMessage().getContent()).isEqualTo("simple text");
    }
    
    @Test
    public void serviceShouldSaveAttachmentInAFolderWhenPatternMatch() throws MessagingException, IOException {
        Mailet mailet = initMailet();

        String expectedAttachmentContent = EXPECTED_ATTACHMENT_CONTENT;
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .build(),
                createAttachmentBodyPart(expectedAttachmentContent, "10.tmp", TEXT_HEADERS),
                createAttachmentBodyPart("\u0014\u00A3\u00E1\u00E2\u00E4", "temp.zip", TEXT_HEADERS))
            .build();

        Mail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        mailet.service(mail);

        @SuppressWarnings("unchecked")
        Collection<String> savedAttachments = (Collection<String>) mail.getAttribute(StripAttachment.SAVED_ATTACHMENTS_ATTRIBUTE_KEY);
        assertThat(savedAttachments).isNotNull();
        assertThat(savedAttachments).hasSize(1);

        String attachmentFilename = savedAttachments.iterator().next();

        assertThat(new File(folderPath + attachmentFilename)).hasContent(expectedAttachmentContent);
    }

    @Test
    public void serviceShouldRemoveWhenMimeTypeMatches() throws MessagingException, IOException {
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("mimeType", "text/calendar")
                .setProperty("remove", "matched")
                .build();
        Mailet mailet = new StripAttachment();
        mailet.init(mci);

        String expectedFileName = "10.ical";
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .build(),
                createAttachmentBodyPart("content", expectedFileName, CALENDAR_HEADERS),
                createAttachmentBodyPart("other content", "11.ical", TEXT_HEADERS),
                createAttachmentBodyPart("<p>html</p>", "index.html", HTML_HEADERS))
            .build();


        Mail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        mailet.service(mail);

        @SuppressWarnings("unchecked")
        List<String> removedAttachments = (List<String>) mail.getAttribute(StripAttachment.REMOVED_ATTACHMENTS_ATTRIBUTE_KEY);
        assertThat(removedAttachments).containsOnly(expectedFileName);
    }

    private BodyPart createAttachmentBodyPart(String body, String fileName, MimeMessageBuilder.Header... headers) throws MessagingException, IOException {
        return MimeMessageBuilder.bodyPartBuilder()
            .data(body)
            .addHeaders(headers)
            .disposition(MimeBodyPart.ATTACHMENT)
            .filename(fileName)
            .build();
    }

    @Test
    public void serviceShouldSaveAttachmentInAFolderWhenNotPatternDoesntMatch() throws MessagingException, IOException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("directory", folderPath)
                .setProperty("remove", "all")
                .setProperty("notpattern", "^(winmail\\.dat$)")
                .build();
        mailet.init(mci);

        String expectedAttachmentContent = EXPECTED_ATTACHMENT_CONTENT;
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .build(),
                createAttachmentBodyPart(expectedAttachmentContent, "temp_filname.tmp", TEXT_HEADERS),
                createAttachmentBodyPart("\u0014\u00A3\u00E1\u00E2\u00E4", "winmail.dat", TEXT_HEADERS))
            .build();

        Mail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        mailet.service(mail);

        @SuppressWarnings("unchecked")
        Collection<String> savedAttachments = (Collection<String>) mail.getAttribute(StripAttachment.SAVED_ATTACHMENTS_ATTRIBUTE_KEY);
        assertThat(savedAttachments).isNotNull();
        assertThat(savedAttachments).hasSize(2);

        String attachmentFilename = retrieveFilenameStartingWith(savedAttachments, "temp_filname");
        assertThat(attachmentFilename).isNotNull();
        assertThat(new File(folderPath + attachmentFilename)).hasContent(expectedAttachmentContent);
    }

    private String retrieveFilenameStartingWith(Collection<String> savedAttachments, final String filename) {
        return FluentIterable.from(savedAttachments)
                .filter(attachmentFilename -> attachmentFilename.startsWith(filename))
                .first()
                .get();
    }

    @Test
    public void serviceShouldDecodeFilenameAndSaveAttachmentInAFolderWhenPatternMatchAndDecodeFilenameTrue() throws MessagingException, IOException {
        Mailet mailet = initMailet();

        String expectedAttachmentContent = EXPECTED_ATTACHMENT_CONTENT;
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .build(),
                createAttachmentBodyPart(expectedAttachmentContent,
                    "=?iso-8859-15?Q?=E9_++++Pubblicit=E0_=E9_vietata____Milano9052.tmp?=", TEXT_HEADERS),
                createAttachmentBodyPart("\u0014\u00A3\u00E1\u00E2\u00E4", "temp.zip", TEXT_HEADERS))
            .build();

        Mail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        mailet.service(mail);

        @SuppressWarnings("unchecked")
        Collection<String> savedAttachments = (Collection<String>) mail.getAttribute(StripAttachment.SAVED_ATTACHMENTS_ATTRIBUTE_KEY);
        assertThat(savedAttachments).isNotNull();
        assertThat(savedAttachments).hasSize(1);

        String name = savedAttachments.iterator().next();

        assertThat(name.startsWith("e_Pubblicita_e_vietata_Milano9052")).isTrue();
        
        assertThat(new File(folderPath + name)).hasContent(expectedAttachmentContent);
    }

    @Test
    public void serviceShouldSaveFilenameAttachmentAndFileContentInCustomAttribute() throws MessagingException, IOException {
        StripAttachment mailet = new StripAttachment();

        String customAttribute = "my.custom.attribute";
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "matched")
                .setProperty("directory", folderPath)
                .setProperty("pattern", ".*\\.tmp")
                .setProperty("attribute", customAttribute)
                .build();
        mailet.init(mci);

        String expectedKey = "10.tmp";
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .build(),
                createAttachmentBodyPart(EXPECTED_ATTACHMENT_CONTENT, expectedKey, TEXT_HEADERS),
                createAttachmentBodyPart("\u0014\u00A3\u00E1\u00E2\u00E4", "temp.zip", TEXT_HEADERS))
            .build();

        Mail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        mailet.service(mail);

        @SuppressWarnings("unchecked")
        Map<String, byte[]> saved = (Map<String, byte[]>) mail.getAttribute(customAttribute);
        assertThat(saved).hasSize(1);
        assertThat(saved).containsKey(expectedKey);
        MimeBodyPart savedBodyPart = new MimeBodyPart(new ByteArrayInputStream(saved.get(expectedKey)));
        String content = IOUtils.toString(savedBodyPart.getInputStream());
        assertThat(content).isEqualTo(EXPECTED_ATTACHMENT_CONTENT);
    }

    @Test
    public void serviceShouldDecodeHeaderFilenames() throws MessagingException, IOException {
        StripAttachment mailet = new StripAttachment();

        String customAttribute = "my.custom.attribute";
        FakeMailetConfig mci = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("pattern", ".*\\.tmp")
            .setProperty("attribute", customAttribute)
            .build();
        mailet.init(mci);

        String expectedKey = "invite.tmp";
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .build(),
                createAttachmentBodyPart(EXPECTED_ATTACHMENT_CONTENT, "=?US-ASCII?Q?" + expectedKey + "?=", TEXT_HEADERS))
            .build();

        Mail mail = FakeMail.builder()
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        @SuppressWarnings("unchecked")
        Map<String, byte[]> saved = (Map<String, byte[]>) mail.getAttribute(customAttribute);
        assertThat(saved).hasSize(1);
        assertThat(saved).containsKey(expectedKey);
        MimeBodyPart savedBodyPart = new MimeBodyPart(new ByteArrayInputStream(saved.get(expectedKey)));
        String content = IOUtils.toString(savedBodyPart.getInputStream());
        assertThat(content).isEqualTo(EXPECTED_ATTACHMENT_CONTENT);
    }

    @Test
    public void initShouldThrowWhenPatternAndNotPatternAndMimeTypeAreNull() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();

        expectedException.expect(MailetException.class);
        expectedException.expectMessage("At least one of 'pattern', 'notpattern' or 'mimeType' parameter should be provided.");
        mailet.init(mci);
    }

    @Test
    public void initShouldThrowWhenMimeTypeIsEmpty() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("mimeType", "")
                .build();

        expectedException.expect(MailetException.class);
        expectedException.expectMessage("At least one of 'pattern', 'notpattern' or 'mimeType' parameter should be provided.");
        mailet.init(mci);
    }

    @Test
    public void initShouldWorkWhenPatternIsDefinedAndValid() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("pattern", ".*\\.tmp")
                .build();

        mailet.init(mci);
    }

    @Test
    public void initShouldWorkWhenNotPatternIsDefinedAndValid() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("notpattern", ".*\\.tmp")
                .build();

        mailet.init(mci);
    }

    @Test
    public void initShouldWorkWhenMimeTypeIsDefined() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("mimeType", "text/calendar")
                .build();

        mailet.init(mci);
    }

    @Test
    public void initShouldThrowWhenWrongPattern() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("pattern", ".****\\.tmp")
                .build();

        expectedException.expect(MailetException.class);
        expectedException.expectMessage("Could not compile regex [.****\\.tmp]");
        mailet.init(mci);
    }

    @Test
    public void initShouldThrowWhenWrongNotPattern() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("notpattern", ".****\\.tmp")
                .build();

        expectedException.expect(MailetException.class);
        expectedException.expectMessage("Could not compile regex [.****\\.tmp]");
        mailet.init(mci);
    }

    @Test
    public void initShouldThrowWhenRemoveParameterIsUnknown() throws MessagingException {
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "unknown")
                .setProperty("pattern", ".*\\.tmp")
                .build();

        expectedException.expect(MailetException.class);
        expectedException.expectMessage("Unknown remove parameter value 'unknown' waiting for 'matched', 'all' or 'no'.");
        mailet.init(mci);
    }

    @Test
    public void initShouldSetRemoveParameterWhenEqualsMatched() throws MessagingException {
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "matched")
                .setProperty("pattern", ".*\\.tmp")
                .build();

        mailet.init(mci);
        assertThat(mailet.removeAttachments).isEqualTo(StripAttachment.REMOVE_MATCHED);
    }

    @Test
    public void initShouldSetRemoveParameterWhenEqualsAll() throws MessagingException {
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "all")
                .setProperty("pattern", ".*\\.tmp")
                .build();

        mailet.init(mci);
        assertThat(mailet.removeAttachments).isEqualTo(StripAttachment.REMOVE_ALL);
    }

    @Test
    public void initShouldSetRemoveParameterWhenEqualsNo() throws MessagingException {
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "no")
                .setProperty("pattern", ".*\\.tmp")
                .build();

        mailet.init(mci);
        assertThat(mailet.removeAttachments).isEqualTo(StripAttachment.REMOVE_NONE);
    }

    @Test
    public void initShouldSetRemoveParameterDefaultValueWhenNotGiven() throws MessagingException {
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("pattern", ".*\\.tmp")
                .build();

        mailet.init(mci);
        assertThat(mailet.removeAttachments).isEqualTo("no");
    }

    @Test
    public void serviceShouldThrowWhenUnretrievableMessage() throws MessagingException {
        Mailet mailet = initMailet();
        
        Mail mail = mock(Mail.class);
        when(mail.getMessage())
            .thenThrow(new MessagingException("Test exception"));

        expectedException.expect(MailetException.class);
        expectedException.expectMessage("Could not retrieve message from Mail object");
        
        mailet.service(mail);
    }

    @Test
    public void serviceShouldThrowWhenUnretrievableContentTypeMessage() throws MessagingException {
        Mailet mailet = initMailet();

        MimeMessage message = mock(MimeMessage.class);
        Mail mail = mock(Mail.class);
        when(mail.getMessage())
            .thenReturn(message);
        when(message.isMimeType("multipart/*"))
            .thenThrow(new MessagingException("Test exception"));

        expectedException.expect(MailetException.class);
        expectedException.expectMessage("Could not retrieve contenttype of MimePart.");
        
        mailet.service(mail);
    }

    @Test
    public void getMailetInfoShouldReturn() throws MessagingException {
        StripAttachment mailet = new StripAttachment();

        assertThat(mailet.getMailetInfo()).isEqualTo("StripAttachment");
    }

    @Test
    public void processMultipartPartMessageShouldReturnFalseWhenPartIsNotMultipart() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();
        Part part = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));
        Mail mail = mock(Mail.class);
        //When
        boolean actual = mailet.processMultipartPartMessage(part, mail);
        //Then
        assertThat(actual).isFalse();
    }

    @Test
    public void processMultipartPartMessageShouldReturnTrueWhenAtLeastOneMultipartShouldHaveBeenRemoved() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "all")
                .setProperty("pattern", ".*")
                .build();
        mailet.init(mci);

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(MimeMessageBuilder.bodyPartBuilder()
                .filename("removeMe.tmp")
                .build())
            .build();
        Mail mail = mock(Mail.class);
        //When
        boolean actual = mailet.processMultipartPartMessage(mimeMessage, mail);
        //Then
        assertThat(actual).isTrue();
    }

    @Test
    public void processMultipartPartMessageShouldReturnTrueWhenAtLeastOneMultipartShouldHaveBeenRemovedAndPartialRemove() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "matched")
                .setProperty("pattern", ".*")
                .build();
        mailet.init(mci);

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(MimeMessageBuilder.bodyPartBuilder()
                .filename("removeMe.tmp")
                .build())
            .build();

        Mail mail = mock(Mail.class);
        //When
        boolean actual = mailet.processMultipartPartMessage(mimeMessage, mail);
        //Then
        assertThat(actual).isTrue();
    }

    @Test
    public void processMultipartPartMessageShouldPutTwoPartsInDefaultAttributeWhenTwoPartsMatch() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "matched")
                .setProperty("directory", folderPath)
                .setProperty("pattern", ".*")
                .build();
        mailet.init(mci);

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .filename("removeMe.tmp")
                    .build(),
                MimeMessageBuilder.bodyPartBuilder()
                    .filename("removeMe.tmp")
                    .build())
            .build();

        Mail mail = FakeMail.builder().build();
        //When
        boolean actual = mailet.processMultipartPartMessage(mimeMessage, mail);
        //Then
        assertThat(actual).isTrue();
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>)mail.getAttribute(StripAttachment.SAVED_ATTACHMENTS_ATTRIBUTE_KEY);
        assertThat(values).hasSize(2);
    }

    @Test
    public void processMultipartPartMessageShouldPutTwoPartsInCustomAttributeWhenTwoPartsMatch() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        String customAttribute = "my.custom.attribute";
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "matched")
                .setProperty("directory", folderPath)
                .setProperty("pattern", ".*")
                .setProperty("attribute", customAttribute)
                .build();
        mailet.init(mci);

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .filename("removeMe1.tmp")
                    .build(),
                MimeMessageBuilder.bodyPartBuilder()
                    .filename("removeMe2.tmp")
                    .build())
            .build();
        
        Mail mail = FakeMail.builder().build();
        
        //When
        boolean actual = mailet.processMultipartPartMessage(mimeMessage, mail);
        
        //Then
        assertThat(actual).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, byte[]> values = (Map<String, byte[]>)mail.getAttribute(customAttribute);
        assertThat(values).hasSize(2);
    }

    @Test
    public void processMultipartPartMessageShouldReturnTrueWhenAtLeastOneSubMultipartShouldHaveBeenRemoved() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "all")
                .setProperty("pattern", ".*\\.tmp")
                .build();
        mailet .init(mci);

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithSubMessage(
                MimeMessageBuilder.mimeMessageBuilder()
                    .setMultipartWithBodyParts(
                        MimeMessageBuilder.bodyPartBuilder()
                            .filename("removeMe.tmp")
                            .build())
                    .build())
            .build();
        Mail mail = mock(Mail.class);
        //When
        boolean actual = mailet.processMultipartPartMessage(message, mail);
        //Then
        assertThat(actual).isTrue();
    }

    @Test
    public void processMultipartPartMessageShouldReturnFalseWhenNoPartHasBeenRemovedInSubMultipart() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "matched")
                .setProperty("pattern", ".*\\.tmp")
                .build();
        mailet.init(mci);

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithSubMessage(
                MimeMessageBuilder.mimeMessageBuilder()
                    .setMultipartWithBodyParts(
                        MimeMessageBuilder.bodyPartBuilder()
                            .filename("dontRemoveMe.other")
                            .build())
                    .build())
            .build();
        Mail mail = mock(Mail.class);
        //When
        boolean actual = mailet.processMultipartPartMessage(message, mail);
        //Then
        assertThat(actual).isFalse();
    }

    @Test
    public void processMultipartPartMessageShouldRemovePartWhenOnePartShouldHaveBeenRemoved() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "all")
                .setProperty("pattern", ".*")
                .build();
        mailet.init(mci);

        MimeMultipart mimeMultipart = MimeMessageBuilder.multipartBuilder()
            .addBody(MimeMessageBuilder.bodyPartBuilder()
                .filename("removeMe.tmp")
                .build())
            .build();
        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setContent(mimeMultipart)
            .build();

        Mail mail = mock(Mail.class);
        //When
        mailet.processMultipartPartMessage(mimeMessage, mail);
        //Then
        assertThat(mimeMultipart.getCount()).isZero();
    }

    @Test
    public void processMultipartPartMessageShouldSetFilenameToMatchingAttachmentsWhenAttachmentWithoutFilename() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "matched")
                .setProperty("directory", folderPath)
                .setProperty("pattern", ".*")
                .build();
        mailet.init(mci);

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .build())
            .build();

        Mail mail = FakeMail.builder().build();
        //When
        boolean actual = mailet.processMultipartPartMessage(mimeMessage, mail);
        //Then
        assertThat(actual).isTrue();
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>)mail.getAttribute(StripAttachment.SAVED_ATTACHMENTS_ATTRIBUTE_KEY);
        assertThat(values).hasSize(1);
    }

    @Test
    public void saveAttachmentShouldUsePartNameIfNoFilename() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("directory", folderPath)
                .setProperty("pattern", ".*\\.tmp")
                .build();
        mailet.init(mci);

        Part part = MimeMessageBuilder.bodyPartBuilder()
            .filename("example.tmp")
            .build();
        //When
        Optional<String> maybeFilename = mailet.saveAttachmentToFile(part, ABSENT_MIME_TYPE);
        //Then
        assertThat(maybeFilename).isPresent();
        String filename = maybeFilename.get();
        assertThat(filename).startsWith("example");
        assertThat(filename).endsWith(".tmp");
    }
    
    @Test
    public void saveAttachmentShouldReturnAbsentWhenNoFilenameAtAll() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("directory", folderPath)
                .setProperty("pattern", ".*\\.tmp")
                .build();
        mailet.init(mci);
        Part part = MimeMessageBuilder.bodyPartBuilder().build();

        Optional<String> maybeFilename = mailet.saveAttachmentToFile(part, ABSENT_MIME_TYPE);
        assertThat(maybeFilename).isAbsent();
    }
    
    @Test
    public void saveAttachmentShouldAddBinExtensionWhenNoFileNameExtension() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("directory", folderPath)
                .setProperty("pattern", ".*")
                .build();
        mailet.init(mci);
        Part part = MimeMessageBuilder.bodyPartBuilder().build();
        String fileName = "exampleWithoutSuffix";
        //When
        Optional<String> maybeFilename = mailet.saveAttachmentToFile(part, Optional.of(fileName));
        //Then
        assertThat(maybeFilename).isPresent();
        String filename = maybeFilename.get();
        assertThat(filename).startsWith("exampleWithoutSuffix");
        assertThat(filename).endsWith(".bin");
    }
    
    private Mailet initMailet() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("directory", folderPath)
                .setProperty("remove", "all")
                .setProperty("pattern", ".*\\.tmp")
                .setProperty("decodeFilename", "true")
                .setProperty("replaceFilenamePattern",
                    "/[\u00C0\u00C1\u00C2\u00C3\u00C4\u00C5]/A//,"
                            + "/[\u00C6]/AE//,"
                            + "/[\u00C8\u00C9\u00CA\u00CB]/E//,"
                            + "/[\u00CC\u00CD\u00CE\u00CF]/I//,"
                            + "/[\u00D2\u00D3\u00D4\u00D5\u00D6]/O//,"
                            + "/[\u00D7]/x//," + "/[\u00D9\u00DA\u00DB\u00DC]/U//,"
                            + "/[\u00E0\u00E1\u00E2\u00E3\u00E4\u00E5]/a//,"
                            + "/[\u00E6]/ae//,"
                            + "/[\u00E8\u00E9\u00EA\u00EB]/e/r/,"
                            + "/[\u00EC\u00ED\u00EE\u00EF]/i//,"
                            + "/[\u00F2\u00F3\u00F4\u00F5\u00F6]/o//,"
                            + "/[\u00F9\u00FA\u00FB\u00FC]/u//,"
                            + "/[^A-Za-z0-9._-]+/_/r/")
                .build();

        mailet.init(mci);
        return mailet;
    }

    @Test
    public void fileNameMatchesShouldThrowWhenPatternIsNull() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("pattern", ".*pattern.*")
                .build();
        mailet.init(mci);
        
        expectedException.expect(NullPointerException.class);
        mailet.fileNameMatches(null);
    }

    @Test
    public void fileNameMatchesShouldReturnFalseWhenPatternDoesntMatch() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("pattern", ".*pattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("not matching")).isFalse();
    }

    @Test
    public void fileNameMatchesShouldReturnTrueWhenPatternMatches() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("pattern", ".*pattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("I've got a pattern.")).isTrue();
    }

    @Test
    public void fileNameMatchesShouldReturnFalseWhenNotPatternMatches() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("notpattern", ".*pattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("I've got a pattern.")).isFalse();
    }

    @Test
    public void fileNameMatchesShouldReturnTrueWhenNotPatternDoesntMatch() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("notpattern", ".*pattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("not matching")).isTrue();
    }

    @Test
    public void fileNameMatchesShouldReturnFalseWhenPatternAndNotPatternAreTheSame() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("pattern", ".*pattern.*")
                .setProperty("notpattern", ".*pattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("not matching")).isFalse();
        assertThat(mailet.fileNameMatches("I've got a pattern.")).isFalse();
    }

    @Test
    public void fileNameMatchesShouldReturnTrueWhenPatternMatchesAndNotPatternDoesntMatch() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("pattern", ".*pattern.*")
                .setProperty("notpattern", ".*notpattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("I've got a pattern.")).isTrue();
    }

    @Test
    public void fileNameMatchesShouldReturnTrueWhenPatternDoesntMatchesAndNotPatternDoesntMatch() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("pattern", ".*pattern.*")
                .setProperty("notpattern", ".*notpattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("o.")).isTrue();
    }

    @Test
    public void prependedPrefixShouldAddUnderscoreWhenPrefixIsLessThanThreeCharacters() {
        String prefix = OutputFileName.prependedPrefix("a");
        assertThat(prefix).isEqualTo("__a");
    }

    @Test
    public void prependedPrefixShouldReturnPrefixWhenPrefixIsGreaterThanThreeCharacters() {
        String expectedPrefix = "abcd";
        String prefix = OutputFileName.prependedPrefix(expectedPrefix);
        assertThat(prefix).isEqualTo(expectedPrefix);
    }
}
