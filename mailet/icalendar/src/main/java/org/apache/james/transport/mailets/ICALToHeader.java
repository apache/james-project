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

import java.util.Map;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;

/**
 * ICALToHeader takes a Map of filenames to ICAL4J calendars, will pick the first Calendar,
 * and add it to the headers of the e-mail.
 *
 * The following headers will be added : X_MEETING_UID, X_MEETING_METHOD, X_MEETING_RECURRENCE_ID, X_MEETING_SEQUENCE,
 * X_MEETING_DTSTAMP
 *
 * The only configuration parameter for this mailet is the attribute the ICAL4J Calendar map should be attached to,
 * named <b>attribute</b>.
 *
 * Configuration example :
 *
 * <pre>
 *     <code>
 *         &lt;mailet match=??? class=ICALToHeader&gt;
 *             &lt;attribute&gt;icalendars&lt;/attribute&gt;
 *         &lt;/mailet&gt;
 *     </code>
 * </pre>
 */
public class ICALToHeader extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(ICALToHeader.class);
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, AttributeValue<?>>> MAP_STRING_CALENDAR = (Class<Map<String, AttributeValue<?>>>) (Object) Map.class;

    public static final String ATTRIBUTE_PROPERTY = "attribute";
    public static final String ATTRIBUTE_DEFAULT_NAME = "icalendar";
    public static final AttributeName ATTRIBUTE_DEFAULT = AttributeName.of(ATTRIBUTE_DEFAULT_NAME);

    public static final String X_MEETING_UID_HEADER = "X-MEETING-UID";
    public static final String X_MEETING_METHOD_HEADER = "X-MEETING-METHOD";
    public static final String X_MEETING_RECURRENCE_ID_HEADER = "X-MEETING-RECURRENCE-ID";
    public static final String X_MEETING_SEQUENCE_HEADER = "X-MEETING-SEQUENCE";
    public static final String X_MEETING_DTSTAMP_HEADER = "X-MEETING-DTSTAMP";

    static {
        ICal4JConfigurator.configure();
    }

    private AttributeName attribute;

    @Override
    public String getMailetInfo() {
        return "ICALToHeader Mailet";
    }

    @Override
    public void init() throws MessagingException {
        String attributeRaw = getInitParameter(ATTRIBUTE_PROPERTY, ATTRIBUTE_DEFAULT_NAME);
        if (Strings.isNullOrEmpty(attributeRaw)) {
            throw new MessagingException("Attribute " + attributeRaw + " can not be empty or null");
        }
        attribute = AttributeName.of(attributeRaw);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            AttributeUtils
                .getValueAndCastFromMail(mail, attribute, MAP_STRING_CALENDAR)
                .ifPresent(calendarMap -> processCalendars(calendarMap, mail));
        } catch (ClassCastException e) {
            LOGGER.error("Received a mail with {} not being an ICAL object for mail {}", attribute, mail.getName(), e);
        }
    }

    private void processCalendars(Map<String, AttributeValue<?>> calendarMap, Mail mail) {
        calendarMap
            .values()
            .stream()
            .findAny()
            .map(AttributeValue::getValue)
            .filter(Calendar.class::isInstance)
            .map(Calendar.class::cast)
            .ifPresent(Throwing.<Calendar>consumer(calendar -> writeToHeaders(calendar, mail))
                    .sneakyThrow());
    }

    @VisibleForTesting
    AttributeName getAttribute() {
        return attribute;
    }

    private void writeToHeaders(Calendar calendar, Mail mail) throws MessagingException {
        MimeMessage mimeMessage = mail.getMessage();
        VEvent vevent = (VEvent) calendar.getComponent("VEVENT");
        addIfPresent(mimeMessage, X_MEETING_METHOD_HEADER, calendar.getMethod());
        addIfPresent(mimeMessage, X_MEETING_UID_HEADER, vevent.getUid());
        addIfPresent(mimeMessage, X_MEETING_RECURRENCE_ID_HEADER, vevent.getRecurrenceId());
        addIfPresent(mimeMessage, X_MEETING_SEQUENCE_HEADER, vevent.getSequence());
        addIfPresent(mimeMessage, X_MEETING_DTSTAMP_HEADER, vevent.getDateStamp());
    }

    private void addIfPresent(MimeMessage mimeMessage, String headerName, Property property) {
        if (property != null) {
            try {
                mimeMessage.addHeader(headerName, property.getValue());
            } catch (MessagingException e) {
                LOGGER.error("Could not add header {} with value {}", headerName, property.getValue(), e);
            }
        }
    }
}
