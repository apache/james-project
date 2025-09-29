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

package org.apache.james.jmap.mailet.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.mailet.Mail;

public interface FilteringHeaders {
    class MailFilteringHeaders implements FilteringHeaders {
        private final Mail mail;

        public MailFilteringHeaders(Mail mail) {
            this.mail = mail;
        }

        @Override
        public String[] getHeader(String name) throws MessagingException {
            return mail.getMessage().getHeader(name);
        }

        @Override
        public String getSubject() throws MessagingException {
            return mail.getMessage().getSubject();
        }

        @Override
        public Stream<String> getInternalDate() {
            throw new NotImplementedException("Not implemented");
        }

        @Override
        public Stream<String> getSavedDate() {
            throw new NotImplementedException("Not implemented");
        }
    }

    class MessageResultFilteringHeaders implements FilteringHeaders {
        private final Headers headers;
        private final MessageResult messageResult;

        public MessageResultFilteringHeaders(MessageResult messageResult) throws MailboxException {
            this.messageResult = messageResult;
            this.headers = this.messageResult.getHeaders();
        }

        @Override
        public String[] getHeader(String name) throws MailboxException {
            return getMatchingHeaders(name);
        }

        @Override
        public String getSubject() throws MailboxException {
            return Arrays.stream(getMatchingHeaders("Subject"))
                .findFirst()
                .orElse(null);
        }

        private String[] getMatchingHeaders(String name) throws MailboxException {
            final List<String> results = new ArrayList<>();
            if (name != null) {
                Iterator<Header> iterator = headers.headers();
                while (iterator.hasNext()) {
                    Header header = iterator.next();
                    final String headerName = header.getName();
                    if (name.equalsIgnoreCase(headerName)) {
                        results.add(header.getValue());
                    }
                }
            }
            return results.toArray(new String[0]);
        }

        @Override
        public Stream<String> getInternalDate() {
            return Stream.of(MimeUtil.formatDate(messageResult.getInternalDate(), TimeZone.getDefault()));
        }

        @Override
        public Stream<String> getSavedDate() {
            return Stream.of(messageResult.getSaveDate().map(date -> MimeUtil.formatDate(date, TimeZone.getDefault()))
                .orElse(MimeUtil.formatDate(new Date(), TimeZone.getDefault())));
        }
    }

    String[] getHeader(String name) throws Exception;

    String getSubject() throws Exception;

    Stream<String> getInternalDate();

    Stream<String> getSavedDate();
}
