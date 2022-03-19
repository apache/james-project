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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.junit.TemporaryFolderExtension;
import org.apache.james.junit.TemporaryFolderExtension.TemporaryFolder;
import org.apache.james.transport.mailets.StripAttachment.OutputFileName;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ConsumerChainer;

@ExtendWith(TemporaryFolderExtension.class)
class StripAttachmentTest {

    @SuppressWarnings("unchecked")
    private static Class<Collection<AttributeValue<String>>> COLLECTION_STRING_CLASS = (Class<Collection<AttributeValue<String>>>) (Object) Collection.class;
    @SuppressWarnings("unchecked")
    private static Class<Map<String, AttributeValue<?>>> MAP_STRING_BYTES_CLASS = (Class<Map<String, AttributeValue<?>>>) (Object) Map.class;

    private static final String EXPECTED_ATTACHMENT_CONTENT = "#¤ãàé";
    private static final Optional<String> ABSENT_MIME_TYPE = Optional.empty();
    private static final String CONTENT_TRANSFER_ENCODING_VALUE = "8bit";

    private static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_DEFAULT = "application/octet-stream; charset=utf-8";
    private static final String TEXT_CALENDAR_CHARSET_UTF_8 = "text/calendar; charset=utf-8";
    private static final String TEXT_HTML_CHARSET_UTF_8 = "text/html; charset=utf-8";

    private static final MimeMessageBuilder.Header[] TEXT_HEADERS = {
        new MimeMessageBuilder.Header(CONTENT_TRANSFER_ENCODING, CONTENT_TRANSFER_ENCODING_VALUE),
        new MimeMessageBuilder.Header(CONTENT_TYPE, CONTENT_TYPE_DEFAULT)
    };

    private static final MimeMessageBuilder.Header[] HTML_HEADERS = {
        new MimeMessageBuilder.Header(CONTENT_TRANSFER_ENCODING, CONTENT_TRANSFER_ENCODING_VALUE),
        new MimeMessageBuilder.Header(CONTENT_TYPE, TEXT_HTML_CHARSET_UTF_8)
    };

    private static final MimeMessageBuilder.Header[] CALENDAR_HEADERS = {
        new MimeMessageBuilder.Header(CONTENT_TRANSFER_ENCODING, CONTENT_TRANSFER_ENCODING_VALUE),
        new MimeMessageBuilder.Header(CONTENT_TYPE, TEXT_CALENDAR_CHARSET_UTF_8)
    };

    @Test
    void serviceShouldNotModifyMailWhenNotMultipart(TemporaryFolder temporaryFolder) throws MessagingException, IOException {
        Mailet mailet = initMailet(temporaryFolder);
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText("simple text");

        MimeMessageBuilder expectedMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText("simple text");

        Mail mail = FakeMail.fromMessage(message);
        Mail expectedMail = FakeMail.fromMessage(expectedMessage);

        mailet.service(mail);

        assertThat(mail)
            .usingRecursiveComparison()
            .ignoringFields("msg")
            .isEqualTo(expectedMail);
        assertThat(mail.getMessage().getContent()).isEqualTo("simple text");
    }
    
    @Test
    void serviceShouldSaveAttachmentInAFolderWhenPatternMatch(TemporaryFolder temporaryFolder) throws MessagingException {
        Mailet mailet = initMailet(temporaryFolder);

        String expectedAttachmentContent = EXPECTED_ATTACHMENT_CONTENT;
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text"),
                createAttachmentBodyPart(expectedAttachmentContent, "10.tmp", TEXT_HEADERS),
                createAttachmentBodyPart("\u0014£áâä", "temp.zip", TEXT_HEADERS));

        Mail mail = FakeMail.fromMessage(message);

        mailet.service(mail);

        Optional<Collection<AttributeValue<String>>> savedAttachments = AttributeUtils.getValueAndCastFromMail(mail, StripAttachment.SAVED_ATTACHMENTS, COLLECTION_STRING_CLASS);
        assertThat(savedAttachments)
            .isPresent()
            .hasValueSatisfying(attachments -> {
                assertThat(attachments).hasSize(1);

                String attachmentFilename = attachments.iterator().next().value();
                assertThat(new File(temporaryFolder.getFolderPath() + attachmentFilename)).hasContent(expectedAttachmentContent);
            });
    }

    @Test
    void serviceShouldRemoveWhenMimeTypeMatches() throws MessagingException {
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("mimeType", "text/calendar")
                .setProperty("remove", "matched")
                .build();
        Mailet mailet = new StripAttachment();
        mailet.init(mci);

        String expectedFileName = "10.ical";
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text"),
                createAttachmentBodyPart("content", expectedFileName, CALENDAR_HEADERS),
                createAttachmentBodyPart("other content", "11.ical", TEXT_HEADERS),
                createAttachmentBodyPart("<p>html</p>", "index.html", HTML_HEADERS));


        Mail mail = FakeMail.fromMessage(message);

        mailet.service(mail);

        Optional<Collection<AttributeValue<String>>> removedAttachments = AttributeUtils.getValueAndCastFromMail(mail, StripAttachment.REMOVED_ATTACHMENTS, COLLECTION_STRING_CLASS);
        assertThat(removedAttachments)
            .isPresent()
            .hasValueSatisfying(attachments ->
                assertThat(attachments).containsOnly(AttributeValue.of(expectedFileName)));
    }

    private MimeMessageBuilder.BodyPartBuilder createAttachmentBodyPart(String body, String fileName, MimeMessageBuilder.Header... headers) {
        return MimeMessageBuilder.bodyPartBuilder()
            .data(body)
            .addHeaders(headers)
            .disposition(MimeBodyPart.ATTACHMENT)
            .filename(fileName);
    }

    @Test
    void serviceShouldSaveAttachmentInAFolderWhenNotPatternDoesntMatch(TemporaryFolder temporaryFolder) throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("directory", temporaryFolder.getFolderPath())
                .setProperty("remove", "all")
                .setProperty("notpattern", "^(winmail\\.dat$)")
                .build();
        mailet.init(mci);

        String expectedAttachmentContent = EXPECTED_ATTACHMENT_CONTENT;
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text"),
                createAttachmentBodyPart(expectedAttachmentContent, "temp_filname.tmp", TEXT_HEADERS),
                createAttachmentBodyPart("\u0014£áâä", "winmail.dat", TEXT_HEADERS));

        Mail mail = FakeMail.fromMessage(message);

        mailet.service(mail);

        Optional<Collection<AttributeValue<String>>> savedAttachments = AttributeUtils.getValueAndCastFromMail(mail, StripAttachment.SAVED_ATTACHMENTS, COLLECTION_STRING_CLASS);
        assertThat(savedAttachments)
            .isPresent()
            .hasValueSatisfying(attachments -> {
                assertThat(attachments).hasSize(2);

                String attachmentFilename = retrieveFilenameStartingWith(attachments, "temp_filname");
                assertThat(attachmentFilename).isNotNull();
                assertThat(new File(temporaryFolder.getFolderPath() + attachmentFilename)).hasContent(expectedAttachmentContent);
            });
    }

    private String retrieveFilenameStartingWith(Collection<AttributeValue<String>> savedAttachments, String filename) {
        return savedAttachments.stream()
                .map(AttributeValue::value)
                .filter(attachmentFilename -> attachmentFilename.startsWith(filename))
                .findFirst()
                .get();
    }

    @Test
    void serviceShouldDecodeFilenameAndSaveAttachmentInAFolderWhenPatternMatchAndDecodeFilenameTrue(TemporaryFolder temporaryFolder) throws MessagingException {
        Mailet mailet = initMailet(temporaryFolder);

        String expectedAttachmentContent = EXPECTED_ATTACHMENT_CONTENT;
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text"),
                createAttachmentBodyPart(expectedAttachmentContent,
                    "=?iso-8859-15?Q?=E9_++++Pubblicit=E0_=E9_vietata____Milano9052.tmp?=", TEXT_HEADERS),
                createAttachmentBodyPart("\u0014£áâä", "temp.zip", TEXT_HEADERS));

        Mail mail = FakeMail.fromMessage(message);

        mailet.service(mail);

        Optional<Collection<AttributeValue<String>>> savedAttachments = AttributeUtils.getValueAndCastFromMail(mail, StripAttachment.SAVED_ATTACHMENTS, COLLECTION_STRING_CLASS);
        assertThat(savedAttachments)
            .isPresent()
            .hasValueSatisfying(attachments -> {
                assertThat(attachments).hasSize(1);

                String name = attachments.iterator().next().value();

                assertThat(name.startsWith("e_Pubblicita_e_vietata_Milano9052")).isTrue();

                assertThat(new File(temporaryFolder.getFolderPath() + name)).hasContent(expectedAttachmentContent);
            });

    }

    @Test
    void serviceShouldSaveFilenameAttachmentAndFileContentInCustomAttribute(TemporaryFolder temporaryFolder) throws MessagingException, IOException {
        StripAttachment mailet = new StripAttachment();

        String customAttribute = "my.custom.attribute";
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "matched")
                .setProperty("directory", temporaryFolder.getFolderPath())
                .setProperty("pattern", ".*\\.tmp")
                .setProperty("attribute", customAttribute)
                .build();
        mailet.init(mci);

        String expectedKey = "10.tmp";
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text"),
                createAttachmentBodyPart(EXPECTED_ATTACHMENT_CONTENT, expectedKey, TEXT_HEADERS),
                createAttachmentBodyPart("\u0014£áâä", "temp.zip", TEXT_HEADERS));

        Mail mail = FakeMail.fromMessage(message);

        mailet.service(mail);

        Optional<Map<String, AttributeValue<?>>> savedValue = AttributeUtils.getValueAndCastFromMail(mail, AttributeName.of(customAttribute), MAP_STRING_BYTES_CLASS);
        ConsumerChainer<Map<String, AttributeValue<?>>> assertValue = Throwing.consumer(saved -> {
            assertThat(saved)
                    .hasSize(1)
                    .containsKeys(expectedKey);

            MimeBodyPart savedBodyPart = new MimeBodyPart(new ByteArrayInputStream((byte[]) saved.get(expectedKey).getValue()));
            String content = IOUtils.toString(savedBodyPart.getInputStream(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo(EXPECTED_ATTACHMENT_CONTENT);
        });
        assertThat(savedValue)
                .isPresent()
                .hasValueSatisfying(assertValue.sneakyThrow());
    }

    @Test
    void serviceShouldDecodeHeaderFilenames() throws MessagingException, IOException {
        StripAttachment mailet = new StripAttachment();

        String customAttribute = "my.custom.attribute";
        FakeMailetConfig mci = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("pattern", ".*\\.tmp")
            .setProperty("attribute", customAttribute)
            .build();
        mailet.init(mci);

        String expectedKey = "invite.tmp";
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text"),
                createAttachmentBodyPart(EXPECTED_ATTACHMENT_CONTENT, "=?US-ASCII?Q?" + expectedKey + "?=", TEXT_HEADERS));

        Mail mail = FakeMail.fromMessage(message);

        mailet.service(mail);

        Optional<Map<String, AttributeValue<?>>> savedValue = AttributeUtils.getValueAndCastFromMail(mail, AttributeName.of(customAttribute), MAP_STRING_BYTES_CLASS);
        ConsumerChainer<Map<String, AttributeValue<?>>> assertValue = Throwing.consumer(saved -> {
            assertThat(saved)
                    .hasSize(1)
                    .containsKeys(expectedKey);

            MimeBodyPart savedBodyPart = new MimeBodyPart(new ByteArrayInputStream((byte[]) saved.get(expectedKey).getValue()));
            String content = IOUtils.toString(savedBodyPart.getInputStream(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo(EXPECTED_ATTACHMENT_CONTENT);
        });
        assertThat(savedValue)
            .isPresent()
            .hasValueSatisfying(assertValue.sneakyThrow());
    }

    @Test
    void initShouldThrowWhenPatternAndNotPatternAndMimeTypeAreNull() {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();

        assertThatThrownBy(() -> mailet.init(mci))
            .isInstanceOf(MailetException.class)
            .hasMessage("At least one of 'pattern', 'notpattern' or 'mimeType' parameter should be provided.");
    }

    @Test
    void initShouldThrowWhenMimeTypeIsEmpty() {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("mimeType", "")
                .build();

        assertThatThrownBy(() -> mailet.init(mci))
            .isInstanceOf(MailetException.class)
            .hasMessage("At least one of 'pattern', 'notpattern' or 'mimeType' parameter should be provided.");
    }

    @Test
    void initShouldWorkWhenPatternIsDefinedAndValid() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("pattern", ".*\\.tmp")
                .build();

        mailet.init(mci);
    }

    @Test
    void initShouldWorkWhenNotPatternIsDefinedAndValid() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("notpattern", ".*\\.tmp")
                .build();

        mailet.init(mci);
    }

    @Test
    void initShouldWorkWhenMimeTypeIsDefined() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("mimeType", "text/calendar")
                .build();

        mailet.init(mci);
    }

    @Test
    void initShouldThrowWhenWrongPattern() {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("pattern", ".****\\.tmp")
                .build();

        assertThatThrownBy(() -> mailet.init(mci))
            .isInstanceOf(MailetException.class)
            .hasMessage("Could not compile regex [.****\\.tmp].");
    }

    @Test
    void initShouldThrowWhenWrongNotPattern() {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("notpattern", ".****\\.tmp")
                .build();

        assertThatThrownBy(() -> mailet.init(mci))
            .isInstanceOf(MailetException.class)
            .hasMessage("Could not compile regex [.****\\.tmp].");
    }

    @Test
    void initShouldThrowWhenRemoveParameterIsUnknown() {
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "unknown")
                .setProperty("pattern", ".*\\.tmp")
                .build();

        assertThatThrownBy(() -> mailet.init(mci))
            .isInstanceOf(MailetException.class)
            .hasMessage("Unknown remove parameter value 'unknown' waiting for 'matched', 'all' or 'no'.");
    }

    @Test
    void initShouldSetRemoveParameterWhenEqualsMatched() throws MessagingException {
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
    void initShouldSetRemoveParameterWhenEqualsAll() throws MessagingException {
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
    void initShouldSetRemoveParameterWhenEqualsNo() throws MessagingException {
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
    void initShouldSetRemoveParameterDefaultValueWhenNotGiven() throws MessagingException {
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("pattern", ".*\\.tmp")
                .build();

        mailet.init(mci);
        assertThat(mailet.removeAttachments).isEqualTo("no");
    }

    @Test
    void serviceShouldThrowWhenUnretrievableMessage(TemporaryFolder temporaryFolder) throws MessagingException {
        Mailet mailet = initMailet(temporaryFolder);
        
        Mail mail = mock(Mail.class);
        when(mail.getMessage())
            .thenThrow(new MessagingException("Test exception"));

        assertThatThrownBy(() -> mailet.service(mail))
            .isInstanceOf(MailetException.class)
            .hasMessage("Could not retrieve message from Mail object");
    }

    @Test
    void serviceShouldThrowWhenUnretrievableContentTypeMessage(TemporaryFolder temporaryFolder) throws MessagingException {
        Mailet mailet = initMailet(temporaryFolder);

        MimeMessage message = mock(MimeMessage.class);
        Mail mail = mock(Mail.class);
        when(mail.getMessage())
            .thenReturn(message);
        when(message.isMimeType("multipart/*"))
            .thenThrow(new MessagingException("Test exception"));

        assertThatThrownBy(() -> mailet.service(mail))
            .isInstanceOf(MailetException.class)
            .hasMessage("Could not retrieve contenttype of MimePart.");
    }

    @Test
    void getMailetInfoShouldReturn() {
        StripAttachment mailet = new StripAttachment();

        assertThat(mailet.getMailetInfo()).isEqualTo("StripAttachment");
    }

    @Test
    void processMultipartPartMessageShouldReturnFalseWhenPartIsNotMultipart() throws Exception {
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
    void processMultipartPartMessageShouldReturnTrueWhenAtLeastOneMultipartShouldHaveBeenRemoved() throws Exception {
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
                .filename("removeMe.tmp"))
            .build();
        Mail mail = mock(Mail.class);
        //When
        boolean actual = mailet.processMultipartPartMessage(mimeMessage, mail);
        //Then
        assertThat(actual).isTrue();
    }

    @Test
    void processMultipartPartMessageShouldReturnTrueWhenAtLeastOneMultipartShouldHaveBeenRemovedAndPartialRemove() throws Exception {
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
                .filename("removeMe.tmp"))
            .build();

        Mail mail = mock(Mail.class);
        //When
        boolean actual = mailet.processMultipartPartMessage(mimeMessage, mail);
        //Then
        assertThat(actual).isTrue();
    }

    @Test
    void processMultipartPartMessageShouldPutTwoPartsInDefaultAttributeWhenTwoPartsMatch(TemporaryFolder temporaryFolder) throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "matched")
                .setProperty("directory", temporaryFolder.getFolderPath())
                .setProperty("pattern", ".*")
                .build();
        mailet.init(mci);

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .filename("removeMe.tmp"),
                MimeMessageBuilder.bodyPartBuilder()
                    .filename("removeMe.tmp"))
            .build();

        Mail mail = FakeMail.builder().name("mail").build();
        //When
        boolean actual = mailet.processMultipartPartMessage(mimeMessage, mail);
        //Then
        assertThat(actual).isTrue();
        Optional<Collection<AttributeValue<String>>> removedAttachments = AttributeUtils.getValueAndCastFromMail(mail, StripAttachment.SAVED_ATTACHMENTS, COLLECTION_STRING_CLASS);
        assertThat(removedAttachments)
            .isPresent()
            .hasValueSatisfying(attachments ->
                    assertThat(attachments).hasSize(2));
    }

    @Test
    void processMultipartPartMessageShouldPutTwoPartsInCustomAttributeWhenTwoPartsMatch(TemporaryFolder temporaryFolder) throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        String customAttribute = "my.custom.attribute";
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "matched")
                .setProperty("directory", temporaryFolder.getFolderPath())
                .setProperty("pattern", ".*")
                .setProperty("attribute", customAttribute)
                .build();
        mailet.init(mci);

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .filename("removeMe1.tmp"),
                MimeMessageBuilder.bodyPartBuilder()
                    .filename("removeMe2.tmp"))
            .build();
        
        Mail mail = FakeMail.builder().name("mail").build();
        
        //When
        boolean actual = mailet.processMultipartPartMessage(mimeMessage, mail);
        
        //Then
        assertThat(actual).isTrue();
        Optional<Map<String,  AttributeValue<?>>> savedValue = AttributeUtils.getValueAndCastFromMail(mail, AttributeName.of(customAttribute), MAP_STRING_BYTES_CLASS);
        assertThat(savedValue)
                .isPresent()
                .hasValueSatisfying(saved ->
                    Assertions.assertThat(saved)
                            .hasSize(2));
    }

    @Test
    void processMultipartPartMessageShouldReturnTrueWhenAtLeastOneSubMultipartShouldHaveBeenRemoved() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "all")
                .setProperty("pattern", ".*\\.tmp")
                .build();
        mailet.init(mci);

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithSubMessage(
                MimeMessageBuilder.mimeMessageBuilder()
                    .setMultipartWithBodyParts(
                        MimeMessageBuilder.bodyPartBuilder()
                            .filename("removeMe.tmp")))
            .build();
        Mail mail = mock(Mail.class);
        //When
        boolean actual = mailet.processMultipartPartMessage(message, mail);
        //Then
        assertThat(actual).isTrue();
    }

    @Test
    void processMultipartPartMessageShouldReturnFalseWhenNoPartHasBeenRemovedInSubMultipart() throws Exception {
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
                            .filename("dontRemoveMe.other")))
            .build();
        Mail mail = mock(Mail.class);
        //When
        boolean actual = mailet.processMultipartPartMessage(message, mail);
        //Then
        assertThat(actual).isFalse();
    }

    @Test
    void processMultipartPartMessageShouldRemovePartWhenOnePartShouldHaveBeenRemoved() throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "all")
                .setProperty("pattern", ".*")
                .build();
        mailet.init(mci);

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setContent(MimeMessageBuilder.multipartBuilder()
                .addBody(MimeMessageBuilder.bodyPartBuilder()
                    .filename("removeMe.tmp")))
            .build();

        Mail mail = mock(Mail.class);
        //When
        mailet.processMultipartPartMessage(mimeMessage, mail);
        //Then
        assertThat(mimeMessage.getContent()).isInstanceOf(MimeMultipart.class);
        MimeMultipart multipart = (MimeMultipart) mimeMessage.getContent();
        assertThat(multipart.getCount()).isZero();
    }

    @Test
    void processMultipartPartMessageShouldSetFilenameToMatchingAttachmentsWhenAttachmentWithoutFilename(TemporaryFolder temporaryFolder) throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("remove", "matched")
                .setProperty("directory", temporaryFolder.getFolderPath())
                .setProperty("pattern", ".*")
                .build();
        mailet.init(mci);

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .build())
            .build();

        Mail mail = FakeMail.builder().name("mail").build();
        //When
        boolean actual = mailet.processMultipartPartMessage(mimeMessage, mail);
        //Then
        assertThat(actual).isTrue();
        Optional<Collection<AttributeValue<String>>> removedAttachments = AttributeUtils.getValueAndCastFromMail(mail, StripAttachment.SAVED_ATTACHMENTS, COLLECTION_STRING_CLASS);
        assertThat(removedAttachments)
            .isPresent()
            .hasValueSatisfying(attachments ->
                    assertThat(attachments).hasSize(1));
    }

    @Test
    void saveAttachmentShouldUsePartNameIfNoFilename(TemporaryFolder temporaryFolder) throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("directory", temporaryFolder.getFolderPath())
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
    void saveAttachmentShouldReturnAbsentWhenNoFilenameAtAll(TemporaryFolder temporaryFolder) throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("directory", temporaryFolder.getFolderPath())
                .setProperty("pattern", ".*\\.tmp")
                .build();
        mailet.init(mci);
        Part part = MimeMessageBuilder.bodyPartBuilder().build();

        Optional<String> maybeFilename = mailet.saveAttachmentToFile(part, ABSENT_MIME_TYPE);
        assertThat(maybeFilename).isEmpty();
    }
    
    @Test
    void saveAttachmentShouldAddBinExtensionWhenNoFileNameExtension(TemporaryFolder temporaryFolder) throws Exception {
        //Given
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("directory", temporaryFolder.getFolderPath())
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
    
    private Mailet initMailet(TemporaryFolder temporaryFolder) throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("directory", temporaryFolder.getFolderPath())
                .setProperty("remove", "all")
                .setProperty("pattern", ".*\\.tmp")
                .setProperty("decodeFilename", "true")
                .setProperty("replaceFilenamePattern",
                        "/[ÀÁÂÃÄÅ]/A//,"
                            + "/[Æ]/AE//,"
                            + "/[ÈÉÊË]/E//,"
                            + "/[ÌÍÎÏ]/I//,"
                            + "/[ÒÓÔÕÖ]/O//,"
                            + "/[×]/x//,"
                            + "/[ÙÚÛÜ]/U//,"
                            + "/[àáâãäå]/a//,"
                            + "/[æ]/ae//,"
                            + "/[èéêë]/e/r/,"
                            + "/[ìíîï]/i//,"
                            + "/[òóôõö]/o//,"
                            + "/[ùúûü]/u//,"
                            + "/[^A-Za-z0-9._-]+/_/r/")
                .build();

        mailet.init(mci);
        return mailet;
    }

    @Test
    void fileNameMatchesShouldThrowWhenPatternIsNull() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("pattern", ".*pattern.*")
                .build();
        mailet.init(mci);

        assertThatThrownBy(() -> mailet.fileNameMatches(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void fileNameMatchesShouldReturnFalseWhenPatternDoesntMatch() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("pattern", ".*pattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("not matching")).isFalse();
    }

    @Test
    void fileNameMatchesShouldReturnTrueWhenPatternMatches() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("pattern", ".*pattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("I've got a pattern.")).isTrue();
    }

    @Test
    void fileNameMatchesShouldReturnFalseWhenNotPatternMatches() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("notpattern", ".*pattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("I've got a pattern.")).isFalse();
    }

    @Test
    void fileNameMatchesShouldReturnTrueWhenNotPatternDoesntMatch() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("notpattern", ".*pattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("not matching")).isTrue();
    }

    @Test
    void fileNameMatchesShouldReturnFalseWhenPatternAndNotPatternAreTheSame() throws Exception {
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
    void fileNameMatchesShouldReturnTrueWhenPatternMatchesAndNotPatternDoesntMatch() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("pattern", ".*pattern.*")
                .setProperty("notpattern", ".*notpattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("I've got a pattern.")).isTrue();
    }

    @Test
    void fileNameMatchesShouldReturnTrueWhenPatternDoesntMatchesAndNotPatternDoesntMatch() throws Exception {
        StripAttachment mailet = new StripAttachment();
        FakeMailetConfig mci = FakeMailetConfig.builder()
                .setProperty("pattern", ".*pattern.*")
                .setProperty("notpattern", ".*notpattern.*")
                .build();
        mailet.init(mci);
        
        assertThat(mailet.fileNameMatches("o.")).isTrue();
    }

    @Test
    void prependedPrefixShouldAddUnderscoreWhenPrefixIsLessThanThreeCharacters() {
        String prefix = OutputFileName.prependedPrefix("a");
        assertThat(prefix).isEqualTo("__a");
    }

    @Test
    void prependedPrefixShouldReturnPrefixWhenPrefixIsGreaterThanThreeCharacters() {
        String expectedPrefix = "abcd";
        String prefix = OutputFileName.prependedPrefix(expectedPrefix);
        assertThat(prefix).isEqualTo(expectedPrefix);
    }

    @Test
    void getFilenameShouldReturnRandomFilenameWhenExceptionOccured() throws Exception {
        BodyPart bodyPart = mock(BodyPart.class);
        when(bodyPart.getFileName())
            .thenThrow(new MessagingException());

        StripAttachment mailet = new StripAttachment();
        String filename = mailet.getFilename(bodyPart);

        assertThat(filename).isNotNull();
    }
}
