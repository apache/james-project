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
package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.james.jmap.model.MessageContentExtractor.MessageContent;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MessageBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;

public class MessageContentExtractorTest {
    private static final String BINARY_CONTENT = "binary";
    private static final String TEXT_CONTENT = "text content";
    private static final String HTML_CONTENT = "<b>html</b> content";

    private MessageContentExtractor testee;

    private BodyPart htmlPart;
    private BodyPart textPart;

    @Before
    public void setup() throws IOException {
        testee = new MessageContentExtractor();
        textPart = BodyPartBuilder.create().setBody(TEXT_CONTENT, "plain", Charsets.UTF_8).build();
        htmlPart = BodyPartBuilder.create().setBody(HTML_CONTENT, "html", Charsets.UTF_8).build();
    }

    @Test
    public void extractShouldReturnEmptyWhenBinaryContentOnly() throws IOException {
        Message message = MessageBuilder.create()
                .setBody(BasicBodyFactory.INSTANCE.binaryBody(BINARY_CONTENT, Charsets.UTF_8))
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    public void extractShouldReturnTextOnlyWhenTextOnlyBody() throws IOException {
        Message message = MessageBuilder.create()
                .setBody(TEXT_CONTENT, Charsets.UTF_8)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    public void extractShouldReturnHtmlOnlyWhenHtmlOnlyBody() throws IOException {
        Message message = MessageBuilder.create()
                .setBody(HTML_CONTENT, "html", Charsets.UTF_8)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldReturnHtmlAndTextWhenMultipartAlternative() throws IOException {
        Multipart multipart = MultipartBuilder.create("alternative")
                .addBodyPart(textPart)
                .addBodyPart(htmlPart)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldReturnHtmlWhenMultipartAlternativeWithoutPlainPart() throws IOException {
        Multipart multipart = MultipartBuilder.create("alternative")
                .addBodyPart(htmlPart)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldReturnTextWhenMultipartAlternativeWithoutHtmlPart() throws IOException {
        Multipart multipart = MultipartBuilder.create("alternative")
                .addBodyPart(textPart)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    public void extractShouldReturnFirstPartOnlyWhenMultipartMixedAndFirstPartIsText() throws IOException {
        Multipart multipart = MultipartBuilder.create("mixed")
                .addBodyPart(textPart)
                .addBodyPart(htmlPart)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    public void extractShouldReturnFirstPartOnlyWhenMultipartMixedAndFirstPartIsHtml() throws IOException {
        Multipart multipart = MultipartBuilder.create("mixed")
                .addBodyPart(htmlPart)
                .addBodyPart(textPart)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldReturnHtmlAndTextWhenMultipartMixedAndFirstPartIsMultipartAlternative() throws IOException {
        BodyPart multipartAlternative = BodyPartBuilder.create()
            .setBody(MultipartBuilder.create("alternative")
                    .addBodyPart(htmlPart)
                    .addBodyPart(textPart)
                    .build())
            .build();
        Multipart multipartMixed = MultipartBuilder.create("mixed")
                .addBodyPart(multipartAlternative)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipartMixed)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }
}
