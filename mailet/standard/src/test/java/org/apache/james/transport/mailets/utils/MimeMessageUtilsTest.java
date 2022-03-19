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

package org.apache.james.transport.mailets.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

public class MimeMessageUtilsTest {

    @Test
    public void subjectWithPrefixShouldReturnSubjectWithPrefixWhenSubjectIsPresent() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("subject");

        Optional<String> subjectWithPrefix = new MimeMessageUtils(message).subjectWithPrefix("my");

        assertThat(subjectWithPrefix).contains("my subject");
    }

    @Test
    public void subjectWithPrefixShouldReturnPrefixAsSubjectWhenSubjectIsAbsent() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));

        Optional<String> subjectWithPrefix = new MimeMessageUtils(message).subjectWithPrefix("my");

        assertThat(subjectWithPrefix).contains("my");
    }

    @Test
    public void subjectWithPrefixShouldReturnAbsentWhenNullPrefix() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("original subject");
        FakeMail oldMail = FakeMail.from(message);
        String subjectPrefix = null;
        String subject = null;

        Optional<String> subjectWithPrefix = new MimeMessageUtils(message).subjectWithPrefix(subjectPrefix, oldMail, subject);

        assertThat(subjectWithPrefix).isEmpty();
    }

    @Test
    public void subjectWithPrefixShouldReturnAbsentWhenEmptyPrefix() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("original subject");
        FakeMail oldMail = FakeMail.from(message);
        String subjectPrefix = "";
        String subject = null;

        Optional<String> subjectWithPrefix = new MimeMessageUtils(message).subjectWithPrefix(subjectPrefix, oldMail, subject);

        assertThat(subjectWithPrefix).isEmpty();
    }


    @Test
    public void buildNewSubjectShouldPrefixOriginalSubjectWhenSubjectIsNull() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        String prefix = "prefix";
        String originalSubject = "original subject";
        Optional<String> newSubject = new MimeMessageUtils(message).buildNewSubject(prefix, originalSubject, null);

        assertThat(newSubject).contains(prefix + " " + originalSubject);
    }

    @Test
    public void buildNewSubjectShouldPrefixNewSubjectWhenSubjectIsGiven() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        String prefix = "prefix";
        String originalSubject = "original subject";
        String subject = "new subject";
        Optional<String> newSubject = new MimeMessageUtils(message).buildNewSubject(prefix, originalSubject, subject);

        assertThat(newSubject).contains(prefix + " " + subject);
    }

    @Test
    public void buildNewSubjectShouldReplaceSubjectWhenPrefixIsNull() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        String prefix = null;
        String originalSubject = "original subject";
        String subject = "new subject";
        Optional<String> newSubject = new MimeMessageUtils(message).buildNewSubject(prefix, originalSubject, subject);

        assertThat(newSubject).contains(subject);
    }

    @Test
    public void buildNewSubjectShouldReplaceSubjectWhenPrefixIsEmpty() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        String prefix = "";
        String originalSubject = "original subject";
        String subject = "new subject";
        Optional<String> newSubject = new MimeMessageUtils(message).buildNewSubject(prefix, originalSubject, subject);

        assertThat(newSubject).contains(subject);
    }

    @Test
    public void buildNewSubjectShouldReplaceSubjectWithPrefixWhenSubjectIsEmpty() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        String prefix = "prefix";
        String originalSubject = "original subject";
        String subject = "";
        Optional<String> newSubject = new MimeMessageUtils(message).buildNewSubject(prefix, originalSubject, subject);

        assertThat(newSubject).contains(prefix);
    }

    @Test
    public void getMessageHeadersShouldReturnEmptyStringWhenNoHeaders() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        String messageHeaders = new MimeMessageUtils(message).getMessageHeaders();

        assertThat(messageHeaders).isEmpty();
    }

    @Test
    public void getMessageHeadersShouldHeadersWhenSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.addHeader("first", "value");
        message.addHeader("second", "value2");
        String messageHeaders = new MimeMessageUtils(message).getMessageHeaders();

        String expectedHeaders = "first: value\r\nsecond: value2\r\n";
        assertThat(messageHeaders).isEqualTo(expectedHeaders);
    }

    @Test
    public void toHeaderListShouldReturnMessageIdAndMimeVersionByDefault() throws Exception {
        assertThat(
            new MimeMessageUtils(MimeMessageBuilder.mimeMessageBuilder()
                .build())
                .toHeaderList())
            .extracting("name")
            .contains("Message-Id", "MIME-Version");
    }

    @Test
    public void toHeaderListShouldReturnAllMessageHeaders() throws Exception {
        String headerName = "X-OPENPAAS-FEATURE-1";
        assertThat(
            new MimeMessageUtils(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(headerName, "value")
                .build())
                .toHeaderList())
            .extracting("name")
            .containsOnly("Message-Id", "MIME-Version", headerName, "Date", "Content-Type", "Content-Transfer-Encoding");
    }
}
