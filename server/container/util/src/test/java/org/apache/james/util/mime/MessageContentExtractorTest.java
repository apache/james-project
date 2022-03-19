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
package org.apache.james.util.mime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.util.mime.MessageContentExtractor.MessageContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class MessageContentExtractorTest {
    private static final String BINARY_CONTENT = "binary";
    private static final String TEXT_CONTENT = "text content";
    private static final String HTML_CONTENT = "<b>html</b> content";
    private static final String TEXT_CONTENT2 = "other text content";
    private static final String HTML_CONTENT2 = "other <b>html</b> content";
    private static final String ATTACHMENT_CONTENT = "attachment content";
    private static final String ANY_VALUE = "anyValue";
    private static final Field CONTENT_ID_FIELD = new Field() {
        @Override
        public String getName() {
            return MessageContentExtractor.CONTENT_ID;
        }

        @Override
        public String getBody() {
            return ANY_VALUE;
        }

        @Override
        public ByteSequence getRaw() {
            return ByteSequence.EMPTY;
        }
    };

    private MessageContentExtractor testee;

    private BodyPartBuilder htmlPart;
    private BodyPartBuilder textPart;
    private BodyPartBuilder textAttachment;
    private BodyPartBuilder inlineText;
    private BodyPartBuilder inlineImage;

    @BeforeEach
    void setup() throws IOException {
        testee = new MessageContentExtractor();
        textPart = BodyPartBuilder.create().setBody(TEXT_CONTENT, "plain", StandardCharsets.UTF_8);
        htmlPart = BodyPartBuilder.create().setBody(HTML_CONTENT, "html", StandardCharsets.UTF_8);
        textAttachment = BodyPartBuilder.create()
                .setBody(ATTACHMENT_CONTENT, "plain", StandardCharsets.UTF_8)
                .setContentDisposition("attachment");
        inlineText = BodyPartBuilder.create()
                .setBody(ATTACHMENT_CONTENT, "plain", StandardCharsets.UTF_8)
                .setContentDisposition("inline");
        inlineImage = BodyPartBuilder.create()
                .setBody(new byte[0], "image/png")
                .setContentDisposition("inline");
    }

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(MessageContent.class).verify();
    }

    @Test
    void extractShouldReturnEmptyWhenBinaryContentOnly() throws IOException {
        Message message = Message.Builder.of()
                .setBody(BasicBodyFactory.INSTANCE.binaryBody(BINARY_CONTENT, StandardCharsets.UTF_8))
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    void extractShouldReturnTextOnlyWhenTextOnlyBody() throws IOException {
        Message message = Message.Builder.of()
                .setBody(TEXT_CONTENT, StandardCharsets.UTF_8)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    void extractShouldReturnHtmlOnlyWhenHtmlOnlyBody() throws IOException {
        Message message = Message.Builder.of()
                .setBody(HTML_CONTENT, "html", StandardCharsets.UTF_8)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    void extractShouldReturnHtmlAndTextWhenMultipartAlternative() throws IOException {
        Multipart multipart = MultipartBuilder.create("alternative")
                .addBodyPart(textPart)
                .addBodyPart(htmlPart)
                .build();
        Message message = Message.Builder.of()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    void extractShouldReturnHtmlWhenMultipartAlternativeWithoutPlainPart() throws IOException {
        Multipart multipart = MultipartBuilder.create("alternative")
                .addBodyPart(htmlPart)
                .build();
        Message message = Message.Builder.of()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    void extractShouldReturnTextWhenMultipartAlternativeWithoutHtmlPart() throws IOException {
        Multipart multipart = MultipartBuilder.create("alternative")
                .addBodyPart(textPart)
                .build();
        Message message = Message.Builder.of()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    void extractShouldReturnFirstNonAttachmentPartWhenMultipartMixed() throws IOException {
        Multipart multipart = MultipartBuilder.create("mixed")
                .addBodyPart(textAttachment)
                .addBodyPart(htmlPart)
                .addBodyPart(textPart)
                .build();
        Message message = Message.Builder.of()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
        assertThat(actual.getTextBody()).isEmpty();
    }

    @Test
    void extractShouldReturnInlinedTextBodyWithoutCIDWhenNoOtherValidParts() throws IOException {
        String textBody = "body 1";
        Multipart multipart = MultipartBuilder.create("report")
            .addBodyPart(BodyPartBuilder.create()
                .setBody(textBody, "plain", StandardCharsets.UTF_8)
                .setContentDisposition("inline"))
            .addBodyPart(BodyPartBuilder.create()
                .setBody("body 2", "rfc822-headers", StandardCharsets.UTF_8)
                .setContentDisposition("inline"))
            .build();
        Message message = Message.Builder.of()
            .setBody(multipart)
            .build();

        MessageContent actual = testee.extract(message);

        assertThat(actual.getTextBody()).contains(textBody);
    }

    @Test
    void extractShouldReturnEmptyWhenMultipartMixedAndFirstPartIsATextAttachment() throws IOException {
        Multipart multipart = MultipartBuilder.create("mixed")
                .addBodyPart(textAttachment)
                .build();
        Message message = Message.Builder.of()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    void extractShouldReturnFirstPartOnlyWhenMultipartMixedAndFirstPartIsHtml() throws IOException {
        Multipart multipart = MultipartBuilder.create("mixed")
                .addBodyPart(htmlPart)
                .addBodyPart(textPart)
                .build();
        Message message = Message.Builder.of()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    void extractShouldReturnHtmlAndTextWhenMultipartMixedAndFirstPartIsMultipartAlternative() throws IOException {
        BodyPart multipartAlternative = BodyPartBuilder.create()
            .setBody(MultipartBuilder.create("alternative")
                    .addBodyPart(htmlPart)
                    .addBodyPart(textPart)
                    .build())
            .build();
        Multipart multipartMixed = MultipartBuilder.create("mixed")
                .addBodyPart(multipartAlternative)
                .build();
        Message message = Message.Builder.of()
                .setBody(multipartMixed)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    void extractShouldReturnHtmlWhenMultipartRelated() throws IOException {
        Multipart multipart = MultipartBuilder.create("related")
                .addBodyPart(htmlPart)
                .build();
        Message message = Message.Builder.of()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    void extractShouldReturnHtmlAndTextWhenMultipartAlternativeAndFirstPartIsMultipartRelated() throws IOException {
        BodyPart multipartRelated = BodyPartBuilder.create()
            .setBody(MultipartBuilder.create("related")
                    .addBodyPart(htmlPart)
                    .build())
            .build();
        Multipart multipartAlternative = MultipartBuilder.create("alternative")
                .addBodyPart(multipartRelated)
                .build();
        Message message = Message.Builder.of()
                .setBody(multipartAlternative)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    void extractShouldRetrieveHtmlBodyWithOneInlinedHTMLAttachmentWithoutCid() throws IOException {
        //Given
        BodyPart inlinedHTMLPart = BodyPartBuilder.create()
            .setBody(HTML_CONTENT, "html", StandardCharsets.UTF_8)
            .build();
        HeaderImpl inlinedHeader = new HeaderImpl();
        inlinedHeader.addField(Fields.contentDisposition(MimeMessage.INLINE));
        inlinedHeader.addField(Fields.contentType("text/html; charset=utf-8"));
        inlinedHTMLPart.setHeader(inlinedHeader);
        Multipart multipartAlternative = MultipartBuilder.create("alternative")
            .addBodyPart(inlinedHTMLPart)
            .build();
        Message message = Message.Builder.of()
            .setBody(multipartAlternative)
            .build();

        //When
        MessageContent actual = testee.extract(message);

        //Then
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    void extractShouldNotRetrieveHtmlBodyWithOneInlinedHTMLAttachmentWithCid() throws IOException {
        //Given
        BodyPart inlinedHTMLPart = BodyPartBuilder.create()
            .setBody(HTML_CONTENT, "html", StandardCharsets.UTF_8)
            .build();
        HeaderImpl inlinedHeader = new HeaderImpl();
        inlinedHeader.addField(Fields.contentDisposition(MimeMessage.INLINE));
        inlinedHeader.addField(Fields.contentType("text/html; charset=utf-8"));
        inlinedHeader.addField(CONTENT_ID_FIELD);
        inlinedHTMLPart.setHeader(inlinedHeader);
        Multipart multipartAlternative = MultipartBuilder.create("alternative")
            .addBodyPart(inlinedHTMLPart)
            .build();
        Message message = Message.Builder.of()
            .setBody(multipartAlternative)
            .build();

        //When
        MessageContent actual = testee.extract(message);

        //Then
        assertThat(actual.getHtmlBody()).isEmpty();
    }


    @Test
    void extractShouldRetrieveTextBodyWithOneInlinedTextAttachmentWithoutCid() throws IOException {
        //Given
        BodyPart inlinedTextPart = BodyPartBuilder.create()
            .setBody(TEXT_CONTENT, "text", StandardCharsets.UTF_8)
            .build();
        HeaderImpl inlinedHeader = new HeaderImpl();
        inlinedHeader.addField(Fields.contentDisposition(MimeMessage.INLINE));
        inlinedHeader.addField(Fields.contentType("text/plain; charset=utf-8"));
        inlinedTextPart.setHeader(inlinedHeader);
        Multipart multipartAlternative = MultipartBuilder.create("alternative")
            .addBodyPart(inlinedTextPart)
            .build();
        Message message = Message.Builder.of()
            .setBody(multipartAlternative)
            .build();

        //When
        MessageContent actual = testee.extract(message);

        //Then
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
    }

    @Test
    void extractShouldNotRetrieveTextBodyWithOneInlinedTextAttachmentWithCid() throws IOException {
        //Given
        BodyPart inlinedTextPart = BodyPartBuilder.create()
            .setBody(TEXT_CONTENT, "text", StandardCharsets.UTF_8)
            .build();
        HeaderImpl inlinedHeader = new HeaderImpl();
        inlinedHeader.addField(Fields.contentDisposition(MimeMessage.INLINE));
        inlinedHeader.addField(Fields.contentType("text/plain; charset=utf-8"));
        inlinedHeader.addField(CONTENT_ID_FIELD);
        inlinedTextPart.setHeader(inlinedHeader);
        Multipart multipartAlternative = MultipartBuilder.create("alternative")
            .addBodyPart(inlinedTextPart)
            .build();
        Message message = Message.Builder.of()
            .setBody(multipartAlternative)
            .build();

        //When
        MessageContent actual = testee.extract(message);

        //Then
        assertThat(actual.getTextBody()).isEmpty();
    }

    @Test
    void extractShouldRetrieveTextAndHtmlBodyWhenOneInlinedTextAttachmentAndMainContentInMultipart() throws IOException {
        BodyPart multipartAlternative = BodyPartBuilder.create()
                .setBody(MultipartBuilder.create("alternative")
                        .addBodyPart(textPart)
                        .addBodyPart(htmlPart)
                        .build())
                .build();

        Multipart multipartMixed = MultipartBuilder.create("mixed")
                .addBodyPart(multipartAlternative)
                .addBodyPart(inlineText)
                .build();

        Message message = Message.Builder.of()
                .setBody(multipartMixed)
                .build();

        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    void extractShouldRetrieveTextBodyAndHtmlBodyWhenTextBodyInMainMultipartAndHtmlBodyInInnerMultipart() throws IOException {
        BodyPart multipartRelated = BodyPartBuilder.create()
                .setBody(MultipartBuilder.create("related")
                        .addBodyPart(htmlPart)
                        .addBodyPart(inlineImage)
                        .build())
                .build();

        Multipart multipartAlternative = MultipartBuilder.create("alternative")
                .addBodyPart(textPart)
                .addBodyPart(multipartRelated)
                .build();

        Message message = Message.Builder.of()
                .setBody(multipartAlternative)
                .build();

        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    void mergeMessageContentShouldReturnEmptyWhenAllEmpty() {
        MessageContent messageContent1 = MessageContent.empty();
        MessageContent messageContent2 = MessageContent.empty();
        MessageContent expected = MessageContent.empty();

        MessageContent actual = messageContent1.merge(messageContent2);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void mergeMessageContentShouldReturnFirstWhenSecondEmpty() {
        MessageContent messageContent1 = new MessageContent(Optional.of(TEXT_CONTENT), Optional.of(HTML_CONTENT));
        MessageContent messageContent2 = MessageContent.empty();
        MessageContent expected = messageContent1;

        MessageContent actual = messageContent1.merge(messageContent2);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void mergeMessageContentShouldReturnSecondWhenFirstEmpty() {
        MessageContent messageContent1 = MessageContent.empty();
        MessageContent messageContent2 = new MessageContent(Optional.of(TEXT_CONTENT), Optional.of(HTML_CONTENT));
        MessageContent expected = messageContent2;

        MessageContent actual = messageContent1.merge(messageContent2);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void mergeMessageContentShouldReturnMixWhenFirstTextOnlyAndSecondHtmlOnly() {
        MessageContent messageContent1 = MessageContent.ofTextOnly(Optional.of(TEXT_CONTENT));
        MessageContent messageContent2 = MessageContent.ofHtmlOnly(Optional.of(HTML_CONTENT));
        MessageContent expected = new MessageContent(Optional.of(TEXT_CONTENT), Optional.of(HTML_CONTENT));

        MessageContent actual = messageContent1.merge(messageContent2);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void mergeMessageContentShouldReturnMixWhenFirstHtmlOnlyAndSecondTextOnly() {
        MessageContent messageContent1 = MessageContent.ofHtmlOnly(Optional.of(HTML_CONTENT));
        MessageContent messageContent2 = MessageContent.ofTextOnly(Optional.of(TEXT_CONTENT));
        MessageContent expected = new MessageContent(Optional.of(TEXT_CONTENT), Optional.of(HTML_CONTENT));

        MessageContent actual = messageContent1.merge(messageContent2);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void mergeMessageContentShouldReturnFirstWhenTwiceAreComplete() {
        MessageContent messageContent1 = new MessageContent(Optional.of(TEXT_CONTENT), Optional.of(HTML_CONTENT));
        MessageContent messageContent2 = new MessageContent(Optional.of(TEXT_CONTENT2), Optional.of(HTML_CONTENT2));
        MessageContent expected = messageContent1;

        MessageContent actual = messageContent1.merge(messageContent2);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractShouldRespectCharsetWhenOtherThanUTF8() throws IOException {
        String text = "éééé\r\nèèèè\r\nàààà";
        Message message = Message.Builder.of()
                .setBody(text, Charset.forName("windows-1252"))
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(text);
    }

    @Test
    void extractShouldRespectCharsetWhenUTF8() throws IOException {
        String text = "éééé\r\nèèèè\r\nàààà";
        Message message = Message.Builder.of()
                .setBody(text, StandardCharsets.UTF_8)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(text);
    }

    @Test
    void extractShouldUseUSASCIIWhenNoCharset() throws IOException {
        String text = "éééé\r\nèèèè\r\nàààà";
        Message message = Message.Builder.of()
                .setBody(text, null)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains("????\r\n????\r\n????");
    }
}
