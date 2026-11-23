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

package org.apache.james.server.core;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.stream.RawFieldParser;
import org.apache.james.mime4j.util.ContentUtil;

/**
 * Checks done on messages submitted by authenticated users, before their sender fields are checked.
 *
 * Mail readers and the checks may otherwise read different From or Sender fields: Jakarta Mail joins several From
 * fields and returns the first Sender field only, and mime4j names a field "From(x):" From while Jakarta Mail names
 * it "From(x)".
 */
public class SubmittedHeaderFields {
    private static final List<String> SENDER_FIELDS = List.of("From", "Sender");

    public enum Violation {
        SEVERAL_FROM("More than one From field"),
        SEVERAL_SENDER("More than one Sender field"),
        INVALID_FIELD_NAME("Invalid header field name");

        private final String description;

        Violation(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    /**
     * @return why the message is refused, if it is
     */
    public static Optional<Violation> violation(MimeMessage message) throws MessagingException {
        if (count(message, "From") > 1) {
            return Optional.of(Violation.SEVERAL_FROM);
        }
        if (count(message, "Sender") > 1) {
            return Optional.of(Violation.SEVERAL_SENDER);
        }
        for (String line : Collections.list(message.getAllHeaderLines())) {
            if (!hasValidName(line)) {
                return Optional.of(Violation.INVALID_FIELD_NAME);
            }
        }
        return Optional.empty();
    }

    private static int count(MimeMessage message, String name) throws MessagingException {
        return Optional.ofNullable(message.getHeader(name))
            .map(values -> values.length)
            .orElse(0);
    }

    // RFC 5322 section 3.6.8: field-name = 1*ftext, immediately followed by ':'.
    private static boolean hasValidName(String line) {
        int colon = line.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        boolean parentheses = false;
        for (int i = 0; i < colon; i++) {
            char c = line.charAt(i);
            if (c < 33 || c > 126) {
                return false;
            }
            parentheses |= c == '(' || c == ')';
        }
        return !parentheses || !readsAsSenderField(line);
    }

    // Parentheses are allowed by ftext, but mime4j reads them as a comment while Jakarta Mail reads them as part of
    // the name: "From(x)" is a From field for mime4j only.
    private static boolean readsAsSenderField(String line) {
        try {
            String mime4jName = RawFieldParser.DEFAULT.parseField(ContentUtil.encode(line)).getName();
            return SENDER_FIELDS.stream().anyMatch(mime4jName::equalsIgnoreCase);
        } catch (MimeException | RuntimeException e) {
            return true;
        }
    }
}
