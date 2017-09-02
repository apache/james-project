/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.transport.mailets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.stream.Stream;

import javax.mail.MessagingException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.util.CompatibilityHints;

/**
 * <p>
 * This mailet can be combined with the Strip attachment mailet.
 * </p>
 * <p>
 * The ICS body part byte array is arranged as map then this mailet should look for ICS and parse it with Ical4J then store it as a mail attribute
 * </p>
 * <p>
 * Configuration: The mailet contains 2 mandatory attributes
 * </p>
 * <p>
 *
 * <pre>
 *   &lt;mailet match=&quot;All&quot; class=&quot;ICalendarParser&quot; &gt;
 *     &lt;sourceAttribute&gt;source.attribute.name&lt;/sourceAttribute&gt;  &lt;!-- The attribute which contains output value of StripAttachment mailet -- &gt;
 *     &lt;destAttribute&gt;dest.attribute.name&lt;/destAttribute&gt;  &lt;!-- The attribute store the map of Calendar -- &gt;
 *   &lt;/mailet &gt;
 *
 * </pre>
 *
 * </p>
 */
public class ICalendarParser extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(ICalendarParser.class);

    public static final String SOURCE_ATTRIBUTE_PARAMETER_NAME = "sourceAttribute";
    public static final String DESTINATION_ATTRIBUTE_PARAMETER_NAME = "destinationAttribute";

    public static final String SOURCE_ATTRIBUTE_PARAMETER_DEFAULT_VALUE = "icsAttachments";
    public static final String DESTINATION_ATTRIBUTE_PARAMETER_DEFAULT_VALUE = "calendars";

    static {
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_VALIDATION, true);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_NOTES_COMPATIBILITY, true);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_VCARD_COMPATIBILITY, true);
    }

    private String sourceAttributeName;
    private String destinationAttributeName;

    @Override
    public void init() throws MessagingException {
        sourceAttributeName = getInitParameter(SOURCE_ATTRIBUTE_PARAMETER_NAME, SOURCE_ATTRIBUTE_PARAMETER_DEFAULT_VALUE);
        if (Strings.isNullOrEmpty(sourceAttributeName)) {
            throw new MessagingException("source attribute cannot be empty");
        }
        destinationAttributeName = getInitParameter(DESTINATION_ATTRIBUTE_PARAMETER_NAME, DESTINATION_ATTRIBUTE_PARAMETER_DEFAULT_VALUE);
        if (Strings.isNullOrEmpty(destinationAttributeName)) {
            throw new MessagingException("destination attribute cannot be empty");
        }
    }

    @VisibleForTesting
    public String getSourceAttributeName() {
        return sourceAttributeName;
    }

    @VisibleForTesting
    public String getDestinationAttributeName() {
        return destinationAttributeName;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void service(Mail mail) throws MessagingException {
        Object icsAttachmentsObj = mail.getAttribute(sourceAttributeName);
        if (icsAttachmentsObj == null || !(icsAttachmentsObj instanceof Map)) {
            return;
        }

        Map<String, byte[]> icsAttachments = (Map<String, byte[]>) icsAttachmentsObj;
        Map<String, Calendar> calendars = icsAttachments.entrySet()
            .stream()
            .flatMap(entry -> createCalendar(entry.getKey(), entry.getValue()))
            .collect(Guavate.toImmutableMap(Pair::getKey, Pair::getValue));

        mail.setAttribute(destinationAttributeName, (Serializable) calendars);
    }

    @Override
    public String getMailetInfo() {
        return "Calendar Parser";
    }

    private Stream<Pair<String, Calendar>> createCalendar(String key, byte[] icsContent) {
        CalendarBuilder builder = new CalendarBuilder();
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(icsContent);
            return Stream.of(Pair.of(key, builder.build(inputStream)));
        } catch (IOException e) {
            LOGGER.error("Error while reading input: " + icsContent, e);
            return Stream.of();
        } catch (ParserException e) {
            LOGGER.error("Error while parsing ICal object: " + icsContent, e);
            return Stream.of();
        }
    }
}
