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

import static org.apache.james.mime4j.codec.DecodeMonitor.SILENT;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mime4j.field.DateTimeFieldLenientImpl;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.util.OptionalUtils;
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
            // Fallback if possible to mime4j parsing - zoneId information is lost
            return OptionalUtils.executeIfEmpty(
                Optional.ofNullable(DateTimeFieldLenientImpl.PARSER
                    .parse(new RawField("Date", value), SILENT))
                    .flatMap(field -> Optional.ofNullable(field.getDate()))
                    .map(Date::toInstant)
                    .map(instant -> ZonedDateTime.ofInstant(instant, ZoneId.of("UTC"))),
                () -> LOGGER.info("Can not parse receive date {}", value));
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
        return parseSentDate(o1).compareTo(parseSentDate(o2));
    }

    private Instant parseSentDate(MailboxMessage message) {
        String value = getHeaderValue("Date", message);
        RawField field = new RawField("Date", value);
        return Optional.ofNullable(DateTimeFieldLenientImpl.PARSER.parse(field, SILENT)
                .getDate())
            .map(Date::toInstant)
            .orElse(message.getInternalDate().toInstant());
    }
}
