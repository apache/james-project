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

import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.Mail;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class MimeMessageUtils {

    private final MimeMessage message;

    public MimeMessageUtils(MimeMessage message) {
        this.message = message;
    }

    public Optional<String> subjectWithPrefix(String subjectPrefix) throws MessagingException {
        return prefixSubject(message.getSubject(), subjectPrefix);
    }

    private Optional<String> prefixSubject(String subject, String subjectPrefix) {
        if (!Strings.isNullOrEmpty(subject)) {
            return Optional.of(Joiner.on(' ').join(subjectPrefix, subject));
        } else {
            return Optional.of(subjectPrefix);
        }
    }

    public Optional<String> subjectWithPrefix(String subjectPrefix, Mail originalMail, String subject) throws MessagingException {
        return buildNewSubject(subjectPrefix, originalMail.getMessage().getSubject(), subject);
    }

    @VisibleForTesting Optional<String> buildNewSubject(String subjectPrefix, String originalSubject, String subject) {
        String nullablePrefix = Strings.emptyToNull(subjectPrefix);
        if (nullablePrefix == null && subject == null) {
            return Optional.empty();
        }
        if (nullablePrefix == null) {
            return Optional.of(subject);
        }
        String chosenSubject = chooseSubject(subject, originalSubject);
        return prefixSubject(chosenSubject, nullablePrefix);
    }

    private String chooseSubject(String newSubject, String originalSubject) {
        return Optional.ofNullable(newSubject).orElse(originalSubject);
    }

    /**
     * Utility method for obtaining a string representation of a Message's
     * headers
     */
    public String getMessageHeaders() throws MessagingException {
        Enumeration<String> heads = message.getAllHeaderLines();
        StringBuilder headBuffer = new StringBuilder(1024);
        while (heads.hasMoreElements()) {
            headBuffer.append(heads.nextElement()).append("\r\n");
        }
        return headBuffer.toString();
    }

    public List<Header> toHeaderList() throws MessagingException {
        ImmutableList.Builder<Header> headers = ImmutableList.builder();
        Enumeration<Header> allHeaders = message.getAllHeaders();
        while (allHeaders.hasMoreElements()) {
            headers.add(allHeaders.nextElement());
        }
        return headers.build();
    }
}
