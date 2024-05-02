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

package org.apache.james.transport.matchers;

import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.Test;

class AttachmentFileNameIsTest {
    @Test
    void shouldMatchWhenMultipartMixedAndRightFileName() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setMultipartWithBodyParts(
                    MimeMessageBuilder.bodyPartBuilder()
                        .disposition("attachment")
                        .filename("xxx.zip")))
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("xxx.zip")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldNotMatchWhenMultipartMixedAndWrongFileName() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setMultipartWithBodyParts(
                    MimeMessageBuilder.bodyPartBuilder()
                        .disposition("attachment")
                        .filename("xxx.zip")))
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("yyy.zip")
            .build());

        assertThat(testee.match(mail))
            .isNull();
    }

    @Test
    void shouldMatchRecursively() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setContent(MimeMessageBuilder.multipartBuilder()
                    .addBodies(MimeMessageBuilder.bodyPartBuilder()
                        .data(MimeMessageBuilder.multipartBuilder()
                            .addBody(MimeMessageBuilder.bodyPartBuilder()
                                .disposition("attachment")
                                .filename("xxx.zip"))
                            .build()
                        )))
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("xxx.zip")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldIgnoreMultipartAlternative() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setContent(MimeMessageBuilder.multipartBuilder()
                    .subType("alternative")
                    .addBody(MimeMessageBuilder.bodyPartBuilder()
                        .disposition("attachment")
                        .filename("xxx.zip")))
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("xxx.zip")
            .build());

        assertThat(testee.match(mail))
            .isNull();
    }

    @Test
    void shouldMatchSingleBody() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain; name=\"file.txt\"")
                .addHeader("Content-Disposition", "attachment")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("file.txt")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldSupportWildcardPrefix() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain; name=\"file.txt\"")
                .addHeader("Content-Disposition", "attachment")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("*.txt")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void doNotSupportSuffix() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain; name=\"file.txt\"")
                .addHeader("Content-Disposition", "attachment")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("file*")
            .build());

        assertThat(testee.match(mail))
            .isNull();
    }

    @Test
    void supportComaSeparatedValues() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain; name=\"file.txt\"")
                .addHeader("Content-Disposition", "attachment")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("any.zip,*.txt")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void supportSpaceSeparatedValues() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain; name=\"file.txt\"")
                .addHeader("Content-Disposition", "attachment")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("any.zip,*.txt")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void supportComaSpaceSeparatedValues() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain; name=\"file.txt\"")
                .addHeader("Content-Disposition", "attachment")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("any.zip, *.txt")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldNotMatchInNestedMessages() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setMultipartWithSubMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("abc", "text/plain; name=\"file.txt\"")
                    .addHeader("Content-Disposition", "attachment"))
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("file.txt")
            .build());

        assertThat(testee.match(mail))
            .isNull();
    }

    @Test
    void shouldMatchNestedMessages() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setMultipartWithSubMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("abc", "text/plain; name=\"file.txt\"")
                    .addHeader("Content-Disposition", "attachment"))
                .addHeader("Content-Disposition", "attachment; filename=\"msg.eml\"")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("msg.eml")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldMatchInline() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain; name=\"file.txt\"")
                .addHeader("Content-Disposition", "inline")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("file.txt")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldMatchWhenFileNameIsOnContentDisposition() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain")
                .addHeader("Content-Disposition", "attachment; filename=\"file.txt\"")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("file.txt")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldBeCaseInsensitive() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain")
                .addHeader("Content-Disposition", "attachment; filename=\"FiLe.txt\"")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("fIlE.txt")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldSupportMultilineFilename() throws Exception {
        /*Content-Type: text/plain;
        name*0=fiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii;
        name*1=iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiile;
        name*2=.txt; charset=us-ascii
        */
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain;\r\n name=\"fiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiile.txt\"")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("fiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiile.txt")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldSupportTrimming() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain")
                .addHeader("Content-Disposition", "attachment; filename=\"  file.txt\"")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("file.txt")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldSupportQEncoding() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain")
                .addHeader("Content-Disposition", "attachment; filename=\"=?US-ASCII?Q?IHE=5FXDM.zip?=\"")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("IHE_XDM.zip")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void conditionShouldSupportQEncoding() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("abc", "text/plain")
                .addHeader("Content-Disposition", "attachment; filename=\"=?ISO-8859-1?Q?2023_avis_d'=E9ch=E9ance_vakant_facture.pdf?=\"")
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("=?ISO-8859-1?Q?2023_avis_d'=E9ch=E9ance_vakant_facture.pdf?=")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldLookupIntoZipEntryWhenRequested() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setMultipartWithBodyParts(MimeMessageBuilder.bodyPartBuilder()
                    .filename("sonde.zip")
                    .data(ClassLoaderUtils.getSystemResourceAsByteArray("sonde.zip")))
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("-z sonde.txt")
            .build());

        assertThat(testee.match(mail))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void zipNestingIsNotSupported() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setMultipartWithBodyParts(MimeMessageBuilder.bodyPartBuilder()
                    .filename("sonde.zip")
                    .data(ClassLoaderUtils.getSystemResourceAsByteArray("nested.zip")))
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("-z sonde.txt")
            .build());

        assertThat(testee.match(mail))
            .isNull();
    }

    @Test
    void shouldLookupIntoZipEntryOnlyWhenRequested() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setMultipartWithBodyParts(MimeMessageBuilder.bodyPartBuilder()
                    .filename("sonde.zip")
                    .data(ClassLoaderUtils.getSystemResourceAsByteArray("sonde.zip")))
                .build())
            .build();

        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("sonde.txt")
            .build());

        assertThat(testee.match(mail))
            .isNull();
    }

    @Test
    void shouldSupportDebugMode() throws Exception {
        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("-d file.txt")
            .build());

        assertThat(testee.isDebug).isTrue();
    }

    @Test
    void debugModeShouldBeFalseByDefault() throws Exception {
        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("file.txt")
            .build());

        assertThat(testee.isDebug).isFalse();
    }

    @Test
    void shouldSupportUnzipMode() throws Exception {
        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("-z file.txt")
            .build());

        assertThat(testee.unzipIsRequested).isTrue();
    }

    @Test
    void unzipModeShouldBeFalseByDefault() throws Exception {
        AttachmentFileNameIs testee = new AttachmentFileNameIs();

        testee.init(FakeMatcherConfig.builder()
            .matcherName("AttachmentFileNameIs")
            .condition("file.txt")
            .build());

        assertThat(testee.unzipIsRequested).isFalse();
    }
}