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
package org.apache.james.mailbox.store.search.comparator;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.util.date.ImapDateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * {@link Comparator} which works like stated in RFC5256 2.2 Sent Date
 */
public class SentDateComparator extends AbstractHeaderComparator {

    public static final Comparator<MailboxMessage> SENTDATE = new SentDateComparator();
    private static final Logger LOGGER = LoggerFactory.getLogger(SentDateComparator.class);
    // Some sent e-mail have this form : Wed,  3 Jun 2015 09:05:46 +0000 (UTC)
    // Java 8 Time library RFC_1123_DATE_TIME corresponds to Wed,  3 Jun 2015 09:05:46 +0000 only
    // This REGEXP is here to match ( in order to remove ) the possible invalid end of a header date
    // Example of matching patterns :
    //  (UTC)
    //  (CEST)
    private static final Pattern DATE_SANITIZING_PATTERN = Pattern.compile(" *\\(.*\\) *");

    public static Optional<ZonedDateTime> toISODate(String value) {
        try {
            return Optional.of(ZonedDateTime.parse(
                sanitizeDateStringHeaderValue(value),
                ImapDateTimeFormatter.rfc5322()));
        } catch (Exception e) {
            LOGGER.info("Can not parse receive date {}", value);
            return Optional.empty();
        }
    }

     @VisibleForTesting
     static String sanitizeDateStringHeaderValue(String value) {
        // Some sent e-mail have this form : Wed,  3 Jun 2015 09:05:46 +0000 (UTC)
        // Java 8 Time library RFC_1123_DATE_TIME corresponds to Wed,  3 Jun 2015 09:05:46 +0000 only
        // This method is here to convert the first date into something parsable by RFC_1123_DATE_TIME DateTimeFormatter
        Matcher sanitizerMatcher = DATE_SANITIZING_PATTERN.matcher(value);
        if (sanitizerMatcher.find()) {
            return value.substring(0, sanitizerMatcher.start());
        }
        return value;
    }

    @Override
    public int compare(MailboxMessage o1, MailboxMessage o2) {
        Instant date1 = getSentDate(o1);
        Instant date2 = getSentDate(o2);
        return date1.compareTo(date2);
    }
    
    private Instant getSentDate(MailboxMessage message) {
        final String value = getHeaderValue("Date", message);
        return toISODate(value)
            .map(ZonedDateTime::toInstant)
            .orElse(message.getInternalDate().toInstant());
    }
}
