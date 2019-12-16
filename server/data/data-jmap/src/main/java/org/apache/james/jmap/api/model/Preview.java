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

package org.apache.james.jmap.api.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class Preview {
    public static class Factory {
        private final MessageContentExtractor messageContentExtractor;
        private final HtmlTextExtractor htmlTextExtractor;

        @Inject
        public Factory(MessageContentExtractor messageContentExtractor, HtmlTextExtractor htmlTextExtractor) {
            this.messageContentExtractor = messageContentExtractor;
            this.htmlTextExtractor = htmlTextExtractor;
        }

        public Preview fromMessageResult(MessageResult messageResult) throws MailboxException, IOException {
            try (InputStream inputStream = messageResult.getFullContent().getInputStream()) {
                return fromInputStream(inputStream);
            }
        }

        public Preview fromMessageAsString(String messageAsString) throws IOException {
            return fromInputStream(new ByteArrayInputStream(messageAsString.getBytes(StandardCharsets.UTF_8)));
        }

        private Preview fromInputStream(InputStream inputStream) throws IOException {
            return fromMime4JMessage(parse(inputStream));
        }

        private Preview fromMime4JMessage(Message mimeMessage) throws IOException {
            MessageContentExtractor.MessageContent messageContent = messageContentExtractor.extract(mimeMessage);
            return messageContent.extractMainTextContent(htmlTextExtractor)
                .map(Preview::compute)
                .orElse(Preview.EMPTY);
        }

        private Message parse(InputStream inputStream) throws IOException {
            return Message.Builder.of()
                .use(MimeConfig.PERMISSIVE)
                .parse(inputStream)
                .build();
        }
    }

    public static final Preview EMPTY = Preview.from("");

    private static final int MAX_LENGTH = 256;

    public static Preview from(String value) {
        return new Preview(value);
    }

    public static Preview compute(String textBody) {
        return Preview.from(
            truncateToMaxLength(
                StringUtils.normalizeSpace(textBody)));
    }

    private static String truncateToMaxLength(String body) {
        return StringUtils.left(body, MAX_LENGTH);
    }

    private final String value;

    @VisibleForTesting
    Preview(String value) {
        Preconditions.checkNotNull(value);
        Preconditions.checkArgument(value.length() <= MAX_LENGTH,
            String.format("the preview value '%s' has length longer than %d", value, MAX_LENGTH));

        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Preview) {
            Preview preview = (Preview) o;

            return Objects.equals(this.value, preview.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", value)
            .toString();
    }
}
