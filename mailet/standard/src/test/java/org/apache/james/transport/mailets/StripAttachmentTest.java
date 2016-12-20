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
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.InternetHeaders;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;

public class StripAttachmentTest {

    private static final String EXPECTED_ATTACHMENT_CONTENT = "\u0023\u00A4\u00E3\u00E0\u00E9";
    private static final Optional<String> ABSENT_MIME_TYPE = Optional.<String> absent();
    private static final String MIME_HEADER_DEFAULT = "Content-Transfer-Encoding: 8bit\r\nContent-Type: application/octet-stream; charset=utf-8\r\n\r\n";
    private static final String MIME_HEADER_TEXT_CALENDAR = "Content-Transfer-Encoding: 8bit\r\nContent-Type: text/calendar; charset=utf-8\r\n\r\n";
    private static final String MIME_HEADER_TEXT_HTML = "Content-Transfer-Encoding: 8bit\r\nContent-Type: text/html; charset=utf-8\r\n\r\n";

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
        MimeMessage message = mimeMessage();

        MimeBodyPart part = new MimeBodyPart();
        part.setText("simple text");
        
        message.setSubject("test");
        message.setContent(part, "text/plain");
        message.saveChanges();

        MimeMessage expectedMessage = mimeMessage();
        message.setSubject("test");
        message.setContent(part, "text/plain");
        message.saveChanges();

        Mail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();
        Mail expectedMail = FakeMail.builder()
                .mimeMessage(expectedMessage)
                .build();

        mailet.service(mail);

        assertThat(mail).isEqualToComparingFieldByField(expectedMail);
        assertThat(mail.getMessage()).isEqualToComparingFieldByField(expectedMessage);
        assertThat(mail.getMessage().getContent()).isEqualTo(part);
    }
    
    @Test
    public void serviceShouldSaveAttachmentInAFolderWhenPatternMatch() throws MessagingException, IOException {
        Mailet mailet = initMailet();
        MimeMessage message = mimeMessage();

        MimeMultipart multiPart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("simple text");
        multiPart.addBodyPart(textPart);
        String expectedAttachmentContent = EXPECTED_ATTACHMENT_CONTENT;
        multiPart.addBodyPart(createAttachmentBodyPart(expectedAttachmentContent, "10.tmp", MIME_HEADER_DEFAULT));
        multiPart.addBodyPart(createAttachmentBodyPart("\u0014\u00A3\u00E1\u00E2\u00E4", "temp.zip", MIME_HEADER_DEFAULT));
        
        message.setSubject("test");
        message.setContent(multiPart);
        message.saveChanges();

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

        MimeMultipart multiPart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("simple text");
        multiPart.addBodyPart(textPart);
        String expectedFileName = "10.ical";
        multiPart.addBodyPart(createAttachmentBodyPart("content", expectedFileName, MIME_HEADER_TEXT_CALENDAR));
        multiPart.addBodyPart(createAttachmentBodyPart("other content", "11.ical", MIME_HEADER_DEFAULT));
        multiPart.addBodyPart(createAttachmentBodyPart("<p>html</p>", "index.html", MIME_HEADER_TEXT_HTML));

        MimeMessage message = mimeMessage();
        message.setSubject("test");
        message.setContent(multiPart);
        message.saveChanges();

        Mail mail = FakeMail.builder()
                .mimeMessage(message)
                .build();

        mailet.service(mail);

        @SuppressWarnings("unchecked")
        List<String> removedAttachments = (List<String>) mail.getAttribute(StripAttachment.REMOVED_ATTACHMENTS_ATTRIBUTE_KEY);
        assertThat(removedAttachments).containsOnly(expectedFileName);
    }

    private MimeMessage mimeMessage() {
        return new MimeMessage(Session
                .getDefaultInstance(new Properties()));
    }

    private MimeBodyPart createAttachmentBodyPart(String body, String fileName, String mimeHeaders) throws MessagingException, UnsupportedEncodingException {
        MimeBodyPart part = createBodyPart(body, mimeHeaders);
        part.setDisposition("attachment");
        part.setFileName(fileName);
        return part;
    }

    private MimeBodyPart createBodyPart(String body, String mimeHeaders) throws MessagingException, UnsupportedEncodingException {
        byte[] content = (mimeHeaders + body).getBytes("UTF-8");
        return new MimeBodyPart(new ByteArrayInputStream(content));
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

        MimeMessage message = mimeMessage();

        MimeMultipart multiPart = new MimeMultipart();
        MimeBodyPart part = new MimeBodyPart();
        part.setText("simple text");
        multiPart.addBodyPart(part);
        String expectedAttachmentContent = EXPECTED_ATTACHMENT_CONTENT;
        multiPart.addBodyPart(createAttachmentBodyPart(expectedAttachmentContent, "temp.tmp", MIME_HEADER_DEFAULT));
        multiPart.addBodyPart(createAttachmentBodyPart("\u0014\u00A3\u00E1\u00E2\u00E4", "winmail.dat", MIME_HEADER_DEFAULT));
        
        message.setSubject("test");
        message.setContent(multiPart);
        message.saveChanges();

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
    public void serviceShouldDecodeFilenameAndSaveAttachmentInAFolderWhenPatternMatchAndDecodeFilenameTrue() throws MessagingException, IOException {
        Mailet mailet = initMailet();

        MimeMessage message = mimeMessage();

        MimeMultipart multiPart = new MimeMultipart();
        MimeBodyPart part = new MimeBodyPart();
        part.setText("simple text");
        multiPart.addBodyPart(part);
        String expectedAttachmentContent = EXPECTED_ATTACHMENT_CONTENT;
        multiPart.addBodyPart(createAttachmentBodyPart(expectedAttachmentContent, "=?iso-8859-15?Q?=E9_++++Pubblicit=E0_=E9_vietata____Milano9052.tmp?=", MIME_HEADER_DEFAULT));
        multiPart.addBodyPart(createAttachmentBodyPart("\u0014\u00A3\u00E1\u00E2\u00E4", "temp.zip", MIME_HEADER_DEFAULT));
        
        message.setSubject("test");
        message.setContent(multiPart);
        message.saveChanges();

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
        
        MimeMessage message = mimeMessage();

        MimeMultipart multiPart = new MimeMultipart();
        MimeBodyPart part = new MimeBodyPart();
        part.setText("simple text");
        multiPart.addBodyPart(part);
        String expectedKey = "10.tmp";
        multiPart.addBodyPart(createAttachmentBodyPart(EXPECTED_ATTACHMENT_CONTENT, expectedKey, MIME_HEADER_DEFAULT));
        multiPart.addBodyPart(createAttachmentBodyPart("\u0014\u00A3\u00E1\u00E2\u00E4", "temp.zip", MIME_HEADER_DEFAULT));
        
        message.setSubject("test");
        message.setContent(multiPart);
        message.saveChanges();

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
        MimeMultipart multiPart = new MimeMultipart();
        
        MimeBodyPart part = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));
        part.setFileName("removeMe.tmp");
        multiPart.addBodyPart(part);
        MimeMessage message = mimeMessage();
        message.setContent(multiPart);
        message.saveChanges();
        Mail mail = mock(Mail.class);
        //When
        boolean actual = mailet.processMultipartPartMessage(message, mail);
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
        MimeMultipart multiPart = new MimeMultipart();
        
        MimeBodyPart part = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));
        part.setFileName("removeMe.tmp");
        multiPart.addBodyPart(part);
        MimeMessage message = mimeMessage();
        message.setContent(multiPart);
        message.saveChanges();
        Mail mail = mock(Mail.class);
        //When
        boolean actual = mailet.processMultipartPartMessage(message, mail);
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
        MimeMultipart multiPart = new MimeMultipart();
        
        MimeBodyPart part = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));
        part.setFileName("removeMe.tmp");
        multiPart.addBodyPart(part);
        multiPart.addBodyPart(part);
        MimeMessage message = mimeMessage();
        message.setContent(multiPart);
        message.saveChanges();
        Mail mail = FakeMail.builder().build();
        //When
        boolean actual = mailet.processMultipartPartMessage(message, mail);
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
        
        // Message with two matching attachments
        MimeMultipart multiPart = new MimeMultipart();
        MimeBodyPart attachmentPart1 = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));
        attachmentPart1.setFileName("removeMe1.tmp");
        multiPart.addBodyPart(attachmentPart1);
        MimeBodyPart attachmentPart2 = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));
        attachmentPart2.setFileName("removeMe2.tmp");
        multiPart.addBodyPart(attachmentPart2);
        
        MimeMessage message = mimeMessage();
        message.setContent(multiPart);
        message.saveChanges();
        
        Mail mail = FakeMail.builder().build();
        
        //When
        boolean actual = mailet.processMultipartPartMessage(message, mail);
        
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

        MimeBodyPart attachmentPart = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));
        attachmentPart.setFileName("removeMe.tmp");
        
        MimeMultipart subMultiPart = new MimeMultipart();
        subMultiPart.addBodyPart(attachmentPart);
        
        MimeMessage subMessage = mimeMessage();
        subMessage.setContent(subMultiPart);
        subMessage.saveChanges();
        
        MimeMultipart multiPartMixed = new MimeMultipart();
        multiPartMixed.addBodyPart(new MimeBodyPart(new InternetHeaders(new ByteArrayInputStream("Content-Type: multipart/mixed".getBytes(Charsets.US_ASCII)))
                , IOUtils.toByteArray(subMessage.getInputStream())));

        MimeMessage message = mimeMessage();
        message.setContent(multiPartMixed);
        message.saveChanges();
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
        MimeMultipart mainMultiPart = new MimeMultipart();
        MimeMessage subMessage = mimeMessage();
        MimeMultipart subMultiPart = new MimeMultipart();
        MimeBodyPart part = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));
        part.setFileName("dontRemoveMe.other");
        subMultiPart.addBodyPart(part);
        subMessage.setContent(subMultiPart);
        subMessage.saveChanges();
        mainMultiPart.addBodyPart(new MimeBodyPart(new InternetHeaders(new ByteArrayInputStream("Content-Type: multipart/mixed".getBytes(Charsets.US_ASCII)))
                ,
                IOUtils.toByteArray(subMessage.getInputStream())));
        MimeMessage message = mimeMessage();
        message.setContent(mainMultiPart);
        message.saveChanges();
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
        MimeMultipart multiPart = new MimeMultipart();
        
        MimeBodyPart part = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));
        part.setFileName("removeMe.tmp");
        multiPart.addBodyPart(part);
        MimeMessage message = mimeMessage();
        message.setContent(multiPart);
        message.saveChanges();
        Mail mail = mock(Mail.class);
        //When
        mailet.processMultipartPartMessage(message, mail);
        //Then
        assertThat(multiPart.getCount()).isZero();
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
        Part part = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));
        part.setFileName("example.tmp");
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
        Part part = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));

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
        Part part = new MimeBodyPart(new ByteArrayInputStream(new byte[0]));
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
