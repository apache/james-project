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


import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.base.test.FakeMail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.TextNode;
import org.junit.jupiter.api.Test;

class ToPlainTextTest {
    private ToPlainText toPlainText = new ToPlainText(html -> {
        final StringBuilder stringBuilder = new StringBuilder();
        Jsoup.parse(html).body().traverse((node, i) -> {
            if (node instanceof TextNode) {
                TextNode textNode = (TextNode) node;
                stringBuilder.append(textNode.getWholeText());
            }
        });
        return stringBuilder.toString();
    });

    @Test
    void shouldNotAlterTextPlainMails() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail1")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("Nothing relevant", "text/plain"))
            .build();

        toPlainText.service(mail);

        assertThat(mail.getMessage().getContentType()).startsWith("text/plain");
        assertThat(IOUtils.toString(mail.getMessage().getInputStream())).isEqualTo("Nothing relevant");
    }

    @Test
    void shouldAlterTextHtmlMails() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail1")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("<p>Nothing relevant</p>", "text/html"))
            .build();

        toPlainText.service(mail);

        assertThat(mail.getMessage().getContentType()).startsWith("text/plain");
        assertThat(IOUtils.toString(mail.getMessage().getInputStream())).isEqualTo("Nothing relevant");
    }

    @Test
    void shouldNotAlterTextPlainMailsNestedInMultipart() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail1")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setMultipartWithBodyParts(MimeMessageBuilder.bodyPartBuilder()
                    .data("Nothing relevant")
                    .addHeader("Content-Type", "text/plain")))
            .build();

        toPlainText.service(mail);

        assertThat(IOUtils.toString(mail.getMessage().getInputStream())).contains("text/plain");
        assertThat(IOUtils.toString(mail.getMessage().getInputStream())).contains("Nothing relevant");
    }

    @Test
    void shouldSimplifyTopLevelMultiparts() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail1")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setContent(MimeMessageBuilder.multipartBuilder()
                    .subType("alternative")
                    .addBody(MimeMessageBuilder.bodyPartBuilder()
                        .data("<p>Nothing relevant</p>")
                        .addHeader("Content-Type", "text/html"))
                    .addBody(MimeMessageBuilder.bodyPartBuilder()
                        .data("Nothing relevant")
                        .addHeader("Content-Type", "text/plain"))))
            .build();

        toPlainText.service(mail);

        assertThat(IOUtils.toString(mail.getMessage().getInputStream())).contains("text/plain");
        assertThat(IOUtils.toString(mail.getMessage().getInputStream())).contains("Nothing relevant");
        assertThat(IOUtils.toString(mail.getMessage().getInputStream())).doesNotContain("<p>Nothing relevant</p>");
    }

    @Test
    void shouldSimplifyNestedMultiparts() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail1")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setContent(MimeMessageBuilder.multipartBuilder()
                    .subType("related")
                    .addBody(MimeMessageBuilder.bodyPartBuilder()
                        .data(MimeMessageBuilder.multipartBuilder()
                            .subType("alternative")
                            .addBody(MimeMessageBuilder.bodyPartBuilder()
                                .data("<p>Nothing relevant</p>")
                                .addHeader("Content-Type", "text/html"))
                            .addBody(MimeMessageBuilder.bodyPartBuilder()
                                .data("Nothing relevant")
                                .addHeader("Content-Type", "text/plain"))
                            .build()))))
            .build();

        toPlainText.service(mail);

        assertThat(IOUtils.toString(mail.getMessage().getInputStream())).contains("text/plain");
        assertThat(IOUtils.toString(mail.getMessage().getInputStream())).contains("Nothing relevant");
        assertThat(IOUtils.toString(mail.getMessage().getInputStream())).doesNotContain("<p>Nothing relevant</p>");
    }
}