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

import java.io.StringReader;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import javax.inject.Inject;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.mime4j.dom.datetime.DateTime;
import org.apache.james.mime4j.field.datetime.parser.DateTimeParser;
import org.apache.james.mime4j.field.datetime.parser.ParseException;
import org.apache.james.util.DurationParser;
import org.apache.mailet.Mail;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.GenericMailet;


/**
 * <p>Adds an Expires header to the message, or enforces the period of an existing one.</p>
 *
 * <p>Sample configuration:</p>
 *
 * <pre><code>
 * &lt;mailet match="All" class="Expires"&gt;
 *   &lt;minAge&gt;1d&lt;/minAge&gt;
 *   &lt;maxAge&gt;1w&lt;/maxAge&gt;
 *   &lt;defaultAge&gt;4w&lt;/defaultAge&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 *
 * Note that the Expires header is informational only. But some variants of James
 * will let you delete expired messages through the WebAdmin interface:
 * <code>
 * curl -XDELETE http://ip:port/messages?byExpiresHeader
 * </code>
 */
public class Expires extends GenericMailet {
    
    public static final String EXPIRES = "Expires";
    
    private final Clock clock;

    private Optional<Duration> minAge = Optional.empty();
    private Optional<Duration> maxAge = Optional.empty();
    private Optional<Duration> defaultAge = Optional.empty();
    
    @Inject
    public Expires(Clock clock) {
        this.clock = clock;
    }
    
    @Override
    public void init() throws MessagingException {
        minAge = parseDuration("minAge");
        maxAge = parseDuration("maxAge");
        defaultAge = parseDuration("defaultAge");

        if (minAge.isEmpty() && maxAge.isEmpty() && defaultAge.isEmpty()) {
            throw new MessagingException("Please configure at least one of minAge, maxAge, defaultAge");
        }

        if (isAfter(minAge, maxAge)) {
            throw new MessagingException("minAge must be before maxAge");
        }
        if (isAfter(defaultAge, maxAge)) {
            throw new MessagingException("defaultAge must be before maxAge");
        }
        if (isAfter(minAge, defaultAge)) {
            throw new MessagingException("minAge must be before defaultAge");
        }
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        ZonedDateTime now = ZonedDateTime.now(clock);
        MimeMessage message = mail.getMessage();
        Optional<ZonedDateTime> expires = parseExpiresHeader(message);
        if (expires.isPresent()) {
            if (minAge.isPresent() && expires.get().isBefore(now.plus(minAge.get()))) {
                setExpiresAfter(message, now, minAge.get());
            } else
            if (maxAge.isPresent() && expires.get().isAfter(now.plus(maxAge.get()))) {
                setExpiresAfter(message, now, maxAge.get());
            }
        } else if (defaultAge.isPresent()) {
            setExpiresAfter(message, now, defaultAge.get());
        }
    }

    @Override
    public String getMailetInfo() {
        return "Expire Mailet";
    }

    private Optional<Duration> parseDuration(String param) {
        return Optional.ofNullable(getInitParameter(param))
            .map(duration -> DurationParser.parse(duration, ChronoUnit.DAYS));
    }
    
    private boolean isAfter(Optional<Duration> a, Optional<Duration> b) {
        return a.isPresent() && b.isPresent() && a.get().compareTo(b.get()) > 0;
    }
    
    private Optional<ZonedDateTime> parseExpiresHeader(MimeMessage message) {
        try {
            String[] expires = message.getHeader(EXPIRES);
            if (expires == null || expires.length == 0) {
                return Optional.empty();
            } else {
                DateTime dt = new DateTimeParser(new StringReader(expires[0])).parseAll();
                return Optional.of(ZonedDateTime.of(
                    dt.getYear(), dt.getMonth(), dt.getDay(),
                    dt.getHour(), dt.getMinute(), dt.getSecond(), 0,
                    ZoneOffset.ofHoursMinutes(dt.getTimeZone() / 100, dt.getTimeZone() % 100)));
            }
        } catch (MessagingException | ParseException e) {
            return Optional.empty();
        }
    }
    
    private void setExpiresAfter(MimeMessage message, ZonedDateTime now, Duration age) throws MessagingException {
        message.setHeader(EXPIRES, DateFormats.RFC822_DATE_FORMAT.format(now.plus(age)));
        message.saveChanges();
    }
}
