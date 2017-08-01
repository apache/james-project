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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMatcher;

/**
 * use: <pre><code>&lt;mailet match="HasHeader={&lt;header&gt;[=value]}+" class="..." /&gt;</code></pre>
 * <p/>
 * <p>This matcher checks if the header named is present. If complements the
 * AddHeader mailet.</p>
 */
public class HasHeader extends GenericMatcher {

    private static String sanitizeHeaderField(String headerName) {
        return DecoderUtil.decodeEncodedWords(
            MimeUtil.unfold(headerName),
            DecodeMonitor.SILENT);
    }

    private static final String CONDITION_SEPARATOR = "+";
    private static final String HEADER_VALUE_SEPARATOR = "=";

    private interface HeaderCondition {
        boolean isMatching(MimeMessage mimeMessage) throws MessagingException;
    }

    private static class HeaderNameCondition implements HeaderCondition {
        private final String headerName;

        public HeaderNameCondition(String headerName) {
            this.headerName = headerName;
        }

        @Override
        public boolean isMatching(MimeMessage mimeMessage) throws MessagingException {
            String[] headerArray = mimeMessage.getHeader(headerName);
            return headerArray != null && headerArray.length > 0;
        }
    }

    private static class HeaderValueCondition implements HeaderCondition {
        private final String headerName;
        private final String headerValue;

        public HeaderValueCondition(String headerName, String headerValue) {
            this.headerName = headerName;
            this.headerValue = headerValue;
        }

        @Override
        public boolean isMatching(MimeMessage mimeMessage) throws MessagingException {
            String[] headerArray = mimeMessage.getHeader(headerName);
            if (headerArray != null && headerArray.length > 0) {
                return Arrays.stream(headerArray)
                    .anyMatch(value -> headerValue.equals(sanitizeHeaderField(value)));
            }
            return false;
        }
    }

    private List<HeaderCondition> headerConditions;

    public void init() throws MessagingException {
        headerConditions = new ArrayList<>();
        StringTokenizer conditionTokenizer = new StringTokenizer(getCondition(), CONDITION_SEPARATOR);
        while (conditionTokenizer.hasMoreTokens()) {
            headerConditions.add(parseHeaderCondition(conditionTokenizer.nextToken().trim()));
        }
    }

    private HeaderCondition parseHeaderCondition(String element) throws MessagingException {
        StringTokenizer valueSeparatorTokenizer = new StringTokenizer(element, HEADER_VALUE_SEPARATOR, false);
        if (!valueSeparatorTokenizer.hasMoreElements()) {
            throw new MessagingException("Missing headerName");
        }
        String headerName = valueSeparatorTokenizer.nextToken().trim();
        if (valueSeparatorTokenizer.hasMoreTokens()) {
           return new HeaderValueCondition(headerName, valueSeparatorTokenizer.nextToken().trim());
        } else {
            return new HeaderNameCondition(headerName);
        }
    }

    public Collection<MailAddress> match(Mail mail) throws javax.mail.MessagingException {
        for (HeaderCondition headerCondition : headerConditions) {
            if (!headerCondition.isMatching(mail.getMessage())) {
                return null;
            }
        }
        return mail.getRecipients();
    }
} 

