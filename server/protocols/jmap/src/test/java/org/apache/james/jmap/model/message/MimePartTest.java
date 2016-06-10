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

package org.apache.james.jmap.model.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class MimePartTest {

    @Test
    public void isHTMLShouldReturnTrueWhenHTMLSubType() {
        MimePart mimePart = MimePart.builder()
            .addSubType("html")
            .build();
        assertThat(mimePart.isHTML()).isTrue();
    }

    @Test
    public void isHTMLShouldReturnFalseWhenOtherSubType() {
        MimePart mimePart = MimePart.builder()
            .addSubType("other")
            .build();
        assertThat(mimePart.isHTML()).isFalse();
    }

    @Test
    public void isPlainShouldReturnTrueWhenPlainSubType() {
        MimePart mimePart = MimePart.builder()
            .addSubType("plain")
            .build();
        assertThat(mimePart.isPlain()).isTrue();
    }

    @Test
    public void isPlainShouldReturnFalseWhenOtherSubType() {
        MimePart mimePart = MimePart.builder()
            .addSubType("other")
            .build();
        assertThat(mimePart.isPlain()).isFalse();
    }

    @Test
    public void retrieveTextHtmlBodyShouldReturnEmptyWhenOtherSubType() {
        MimePart mimePart = MimePart.builder()
            .addSubType("other")
            .build();
        assertThat(mimePart.retrieveTextHtmlBody()).isEmpty();
    }

    @Test
    public void retrieveTextHtmlBodyShouldReturnHtmlBodyWhenHtmlSubType() {
        String expectedContent = "<b>content</b>";
        MimePart htmlMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("html")
                .addBodyContent(IOUtils.toInputStream(expectedContent))
                .build();

        assertThat(htmlMimePart.retrieveTextHtmlBody()).contains(expectedContent);
    }

    @Test
    public void retrieveTextHtmlBodyShouldReturnHtmlBodyFromAttachmentsWhenHtmlSubTypeInAttachments() {
        String expectedContent = "<b>content</b>";
        MimePart htmlMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("html")
                .addBodyContent(IOUtils.toInputStream(expectedContent))
                .build();

        MimePart mimePart = MimePart.builder()
                .addChild(htmlMimePart)
                .build();

        assertThat(mimePart.retrieveTextHtmlBody()).contains(expectedContent);
    }

    @Test
    public void retrieveTextHtmlBodyShouldReturnHtmlBodyWhenMultipleAttachments() {
        String expectedContent = "<b>content</b>";
        MimePart htmlMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("html")
                .addBodyContent(IOUtils.toInputStream(expectedContent))
                .build();
        MimePart plainMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("plain")
                .addBodyContent(IOUtils.toInputStream("content"))
                .build();

        MimePart mimePart = MimePart.builder()
                .addChild(plainMimePart)
                .addChild(htmlMimePart)
                .build();

        assertThat(mimePart.retrieveTextHtmlBody()).contains(expectedContent);
    }

    @Test
    public void retrieveTextHtmlBodyShouldReturnFirstHtmlBodyWhenMultipleHtml() {
        String expectedContent = "<b>first</b>";
        MimePart firstMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("html")
                .addBodyContent(IOUtils.toInputStream(expectedContent))
                .build();
        MimePart secondMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("html")
                .addBodyContent(IOUtils.toInputStream("<b>second</b>"))
                .build();

        MimePart mimePart = MimePart.builder()
                .addChild(firstMimePart)
                .addChild(secondMimePart)
                .build();

        assertThat(mimePart.retrieveTextHtmlBody()).contains(expectedContent);
    }

    @Test
    public void retrieveTextHtmlBodyShouldReturnEmptyWhenMultipleAttachmentsAndNoHtmlContent() {
        MimePart htmlMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("html")
                .build();
        MimePart plainMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("plain")
                .addBodyContent(IOUtils.toInputStream("content"))
                .build();

        MimePart mimePart = MimePart.builder()
                .addChild(plainMimePart)
                .addChild(htmlMimePart)
                .build();

        assertThat(mimePart.retrieveTextHtmlBody()).isEmpty();
    }

    @Test
    public void retrieveTextPlainMimePartShouldReturnTextBodyWhenPlainSubType() {
        String expectedContent = "content";
        MimePart plainMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("plain")
                .addBodyContent(IOUtils.toInputStream(expectedContent))
                .build();
        assertThat(plainMimePart.retrieveTextPlainBody()).contains(expectedContent);
    }

    @Test
    public void retrieveTextPlainMimePartShouldReturnTextBodyFromAttachmentsWhenPlainSubTypeInAttachments() {
        String expectedContent = "content";
        MimePart plainMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("plain")
                .addBodyContent(IOUtils.toInputStream(expectedContent))
                .build();
        MimePart mimePart = MimePart.builder()
                .addChild(plainMimePart)
                .build();
        assertThat(mimePart.retrieveTextPlainBody()).contains(expectedContent);
    }

    @Test
    public void retrieveTextPlainBodyShouldReturnTextBodyWhenMultipleAttachments() {
        String expectedContent = "content";
        MimePart plainMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("plain")
                .addBodyContent(IOUtils.toInputStream(expectedContent))
                .build();
        MimePart htmlMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("html")
                .addBodyContent(IOUtils.toInputStream("<b>content</b>"))
                .build();

        MimePart mimePart = MimePart.builder()
                .addChild(htmlMimePart)
                .addChild(plainMimePart)
                .build();

        assertThat(mimePart.retrieveTextPlainBody()).contains(expectedContent);
    }

    @Test
    public void retrieveTextPlainBodyShouldReturnTheFirstTextBodyWhenMultipleText() {
        String expectedContent = "first";
        MimePart firstMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("plain")
                .addBodyContent(IOUtils.toInputStream(expectedContent))
                .build();
        MimePart secondMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("plain")
                .addBodyContent(IOUtils.toInputStream("second"))
                .build();

        MimePart mimePart = MimePart.builder()
                .addChild(firstMimePart)
                .addChild(secondMimePart)
                .build();

        assertThat(mimePart.retrieveTextPlainBody()).contains(expectedContent);
    }

    @Test
    public void retrieveTextPlainBodyShouldReturnEmptyWhenMultipleAttachmentsAndNoTextContent() {
        MimePart plainMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("plain")
                .build();
        MimePart htmlMimePart = MimePart.builder()
                .addMediaType("text")
                .addSubType("html")
                .addBodyContent(IOUtils.toInputStream("<b>content</b>"))
                .build();

        MimePart mimePart = MimePart.builder()
                .addChild(htmlMimePart)
                .addChild(plainMimePart)
                .build();

        assertThat(mimePart.retrieveTextPlainBody()).isEmpty();
    }
}
