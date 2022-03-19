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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class ContentReplacerTest {

    @Test
    public void applyPatternsShouldModifyWhenMatching() {
        ContentReplacer testee = new ContentReplacer(false);

        ImmutableList<ReplacingPattern> patterns = ImmutableList.of(new ReplacingPattern(Pattern.compile("test"), false, "TEST"),
            new ReplacingPattern(Pattern.compile("a"), true, "e"),
            new ReplacingPattern(Pattern.compile("o"), false, "o"));
        String value = testee.applyPatterns(patterns, "test aa o");

        assertThat(value).isEqualTo("TEST ee o");
    }

    @Test
    public void applyPatternsShouldNotRepeatWhenNotAskedFor() {
        ContentReplacer testee = new ContentReplacer(false);

        ImmutableList<ReplacingPattern> patterns = ImmutableList.of(new ReplacingPattern(Pattern.compile("test"), false, "TEST"));
        String value = testee.applyPatterns(patterns, "test test");

        assertThat(value).isEqualTo("TEST test");
    }

    @Test
    public void applyPatternsShouldRepeatWhenAskedFor() {
        ContentReplacer testee = new ContentReplacer(false);

        ImmutableList<ReplacingPattern> patterns = ImmutableList.of(new ReplacingPattern(Pattern.compile("test"), true, "TEST"));
        String value = testee.applyPatterns(patterns, "test test");

        assertThat(value).isEqualTo("TEST TEST");
    }

    @Test
    public void applyPatternShouldModifyWhenMatchingBody() throws Exception {
        ContentReplacer testee = new ContentReplacer(false);

        Mail mail = mock(Mail.class);
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mail.getMessage())
            .thenReturn(mimeMessage);
        when(mimeMessage.getContent())
            .thenReturn("test aa o");
        when(mimeMessage.getContentType())
            .thenReturn("text/plain");

        ImmutableList<ReplacingPattern> patterns = ImmutableList.of(new ReplacingPattern(Pattern.compile("test"), false, "TEST"),
            new ReplacingPattern(Pattern.compile("a"), true, "e"),
            new ReplacingPattern(Pattern.compile("o"), false, "o"));
        ReplaceConfig replaceConfig = ReplaceConfig.builder()
                .addAllBodyReplacingUnits(patterns)
                .build();
        testee.replaceMailContentAndSubject(mail, replaceConfig, Optional.of(StandardCharsets.UTF_8));

        verify(mimeMessage).setContent("TEST ee o", "text/plain; charset=UTF-8");
    }

    @Test
    public void applyPatternShouldModifyWhenMatchingSubject() throws Exception {
        ContentReplacer testee = new ContentReplacer(false);

        Mail mail = mock(Mail.class);
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mail.getMessage())
            .thenReturn(mimeMessage);
        when(mimeMessage.getSubject())
            .thenReturn("test aa o");
        when(mimeMessage.getContentType())
            .thenReturn("text/plain");

        ImmutableList<ReplacingPattern> patterns = ImmutableList.of(new ReplacingPattern(Pattern.compile("test"), false, "TEST"),
            new ReplacingPattern(Pattern.compile("a"), true, "e"),
            new ReplacingPattern(Pattern.compile("o"), false, "o"));
        ReplaceConfig replaceConfig = ReplaceConfig.builder()
                .addAllSubjectReplacingUnits(patterns)
                .build();
        testee.replaceMailContentAndSubject(mail, replaceConfig, Optional.of(StandardCharsets.UTF_8));

        verify(mimeMessage).setSubject("TEST ee o", StandardCharsets.UTF_8.name());
    }

    @Test
    public void applyPatternShouldKeepPreviousCharsetWhenNoneSet() throws Exception {
        ContentReplacer testee = new ContentReplacer(false);

        Mail mail = mock(Mail.class);
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mail.getMessage())
            .thenReturn(mimeMessage);
        when(mimeMessage.getSubject())
            .thenReturn("test aa o");
        when(mimeMessage.getContentType())
            .thenReturn("text/plain; charset= UTF-8");

        ImmutableList<ReplacingPattern> patterns = ImmutableList.of(new ReplacingPattern(Pattern.compile("test"), false, "TEST"),
            new ReplacingPattern(Pattern.compile("a"), true, "e"),
            new ReplacingPattern(Pattern.compile("o"), false, "o"));
        ReplaceConfig replaceConfig = ReplaceConfig.builder()
                .addAllSubjectReplacingUnits(patterns)
                .build();
        testee.replaceMailContentAndSubject(mail, replaceConfig, Optional.empty());

        verify(mimeMessage).setSubject("TEST ee o", StandardCharsets.UTF_8.name());
    }
}
