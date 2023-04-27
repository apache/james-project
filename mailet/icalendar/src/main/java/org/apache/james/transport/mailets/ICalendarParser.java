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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import javax.mail.MessagingException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarParserFactory;
import net.fortuna.ical4j.data.ContentHandlerContext;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;

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
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, AttributeValue<byte[]>>> MAP_STRING_BYTES_CLASS = (Class<Map<String, AttributeValue<byte[]>>>) (Object) Map.class;

    public static final String SOURCE_ATTRIBUTE_PARAMETER_NAME = "sourceAttribute";
    public static final String DESTINATION_ATTRIBUTE_PARAMETER_NAME = "destinationAttribute";

    public static final String SOURCE_ATTRIBUTE_PARAMETER_DEFAULT_VALUE = "icsAttachments";
    public static final String DESTINATION_ATTRIBUTE_PARAMETER_DEFAULT_VALUE = "calendars";

    static {
        ICal4JConfigurator.configure();
    }

    private AttributeName sourceAttributeName;
    private AttributeName destinationAttributeName;

    @Override
    public void init() throws MessagingException {
        String sourceAttributeNameRaw = getInitParameter(SOURCE_ATTRIBUTE_PARAMETER_NAME, SOURCE_ATTRIBUTE_PARAMETER_DEFAULT_VALUE);
        if (Strings.isNullOrEmpty(sourceAttributeNameRaw)) {
            throw new MessagingException("source attribute cannot be empty");
        }
        sourceAttributeName = AttributeName.of(sourceAttributeNameRaw);

        String destinationAttributeNameRaw = getInitParameter(DESTINATION_ATTRIBUTE_PARAMETER_NAME, DESTINATION_ATTRIBUTE_PARAMETER_DEFAULT_VALUE);
        if (Strings.isNullOrEmpty(destinationAttributeNameRaw)) {
            throw new MessagingException("destination attribute cannot be empty");
        }
        destinationAttributeName = AttributeName.of(destinationAttributeNameRaw);
    }

    @VisibleForTesting
    AttributeName getSourceAttributeName() {
        return sourceAttributeName;
    }

    @VisibleForTesting
    AttributeName getDestinationAttributeName() {
        return destinationAttributeName;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        AttributeUtils
            .getValueAndCastFromMail(mail, sourceAttributeName, MAP_STRING_BYTES_CLASS)
            .ifPresent(icsAttachments -> addCalendarsToAttribute(mail, icsAttachments));
    }

    private void addCalendarsToAttribute(Mail mail, Map<String, AttributeValue<byte[]>> icsAttachments) {
        Map<String, AttributeValue<?>> calendars = icsAttachments.entrySet()
            .stream()
            .flatMap(entry -> createCalendar(entry.getKey(), entry.getValue().getValue()))
            .collect(ImmutableMap.toImmutableMap(Pair::getKey, pair -> AttributeValue.ofUnserializable(pair.getValue())));

        mail.setAttribute(new Attribute(destinationAttributeName, AttributeValue.of(calendars)));
    }

    @Override
    public String getMailetInfo() {
        return "Calendar Parser";
    }

    private Stream<Pair<String, Calendar>> createCalendar(String key, byte[] icsContent) {
        CalendarBuilder builder = new CalendarBuilder(
            CalendarParserFactory.getInstance().get(),
            new ContentHandlerContext().withSupressInvalidProperties(true),
            TimeZoneRegistryFactory.getInstance().createRegistry());
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(icsContent);
            return Stream.of(Pair.of(key, builder.build(inputStream)));
        } catch (IOException e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Error while reading input: " + new String(icsContent, StandardCharsets.UTF_8), e);
            }
            return Stream.of();
        } catch (ParserException e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Error while parsing ICal object: " + new String(icsContent, StandardCharsets.UTF_8), e);
            }
            return Stream.of();
        }
    }
}
