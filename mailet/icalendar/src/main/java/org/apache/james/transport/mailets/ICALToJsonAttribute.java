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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.transport.mailets.model.ICALAttributeDTO;
import org.apache.james.util.StreamUtils;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import net.fortuna.ical4j.model.Calendar;

/**
 * ICALToJsonAttribute takes a map of ICAL4J objects attached as attribute, and output the map of corresponding json bytes as
 * an other attribute, with unique String keys.
 *
 * The JSON contains the following fields :
 *
 * <ul>
 *     <li><b>ical</b> : the raw ical string, in UTF-8</li>
 *     <li><b>sender</b> : the sender of the mail (compulsory, mail without sender will be discarded)</li>
 *     <li><b>recipient</b> : the recipient of the mail. If the mail have several recipients, each recipient will have
 *     its own JSON.</li>
 *     <li><b>uid</b> : the UID of the ical (optional)</li>
 *     <li><b>sequence</b> : the sequence of the ical (optional)</li>
 *     <li><b>dtstamp</b> : the date stamp of the ical (optional)</li>
 *     <li><b>method</b> : the method of the ical (optional)</li>
 *     <li><b>recurrence-id</b> : the recurrence-id of the ical (optional)</li>
 * </ul>
 *
 * Example are included in test call ICalToJsonAttributeTest.
 *
 *  Configuration example :
 *
 * <pre>
 *     <code>
 *         &lt;mailet match=??? class=ICALToJsonAttribute&gt;
 *             &lt;sourceAttribute&gt;icalendars&lt;/sourceAttribute&gt;
 *             &lt;destinationAttribute&gt;icalendarJson&lt;/destinationAttribute&gt;
 *         &lt;/mailet&gt;
 *     </code>
 * </pre>
 */
public class ICALToJsonAttribute extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(ICALToJsonAttribute.class);
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, AttributeValue<byte[]>>> MAP_STRING_BYTES_CLASS = (Class<Map<String, AttributeValue<byte[]>>>) (Object) Map.class;
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, AttributeValue<Calendar>>> MAP_STRING_CALENDAR_CLASS = (Class<Map<String, AttributeValue<Calendar>>>) (Object) Map.class;

    public static final String SOURCE_ATTRIBUTE_NAME = "source";
    public static final String RAW_SOURCE_ATTRIBUTE_NAME = "rawSource";
    public static final String DESTINATION_ATTRIBUTE_NAME = "destination";
    public static final String DEFAULT_SOURCE_ATTRIBUTE_NAME = "icalendar";
    public static final String DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME = "attachments";
    public static final String DEFAULT_DESTINATION_ATTRIBUTE_NAME = "icalendarJson";
    public static final AttributeName DEFAULT_SOURCE = AttributeName.of(DEFAULT_SOURCE_ATTRIBUTE_NAME);
    public static final AttributeName DEFAULT_RAW_SOURCE = AttributeName.of(DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME);
    public static final AttributeName DEFAULT_DESTINATION = AttributeName.of(DEFAULT_DESTINATION_ATTRIBUTE_NAME);
    public static final String REPLY_TO_HEADER_NAME = "Reply-To";

    static {
        ICal4JConfigurator.configure();
    }

    private final ObjectMapper objectMapper;
    private AttributeName sourceAttributeName;
    private AttributeName rawSourceAttributeName;
    private AttributeName destinationAttributeName;

    public ICALToJsonAttribute() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module());
    }

    @VisibleForTesting
    AttributeName getSourceAttributeName() {
        return sourceAttributeName;
    }

    @VisibleForTesting
    AttributeName getRawSourceAttributeName() {
        return rawSourceAttributeName;
    }

    @VisibleForTesting
    AttributeName getDestinationAttributeName() {
        return destinationAttributeName;
    }

    @Override
    public String getMailetInfo() {
        return "ICALToJson Mailet";
    }

    @Override
    public void init() throws MessagingException {
        String sourceAttributeNameRaw = getInitParameter(SOURCE_ATTRIBUTE_NAME, DEFAULT_SOURCE_ATTRIBUTE_NAME);
        String rawSourceAttributeNameRaw = getInitParameter(RAW_SOURCE_ATTRIBUTE_NAME, DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME);
        String destinationAttributeNameRaw = getInitParameter(DESTINATION_ATTRIBUTE_NAME, DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        if (Strings.isNullOrEmpty(sourceAttributeNameRaw)) {
            throw new MessagingException(SOURCE_ATTRIBUTE_NAME + " configuration parameter can not be null or empty");
        }
        sourceAttributeName = AttributeName.of(sourceAttributeNameRaw);
        if (Strings.isNullOrEmpty(rawSourceAttributeNameRaw)) {
            throw new MessagingException(RAW_SOURCE_ATTRIBUTE_NAME + " configuration parameter can not be null or empty");
        }
        rawSourceAttributeName = AttributeName.of(rawSourceAttributeNameRaw);
        if (Strings.isNullOrEmpty(destinationAttributeNameRaw)) {
            throw new MessagingException(DESTINATION_ATTRIBUTE_NAME + " configuration parameter can not be null or empty");
        }
        destinationAttributeName = AttributeName.of(destinationAttributeNameRaw);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            AttributeUtils.getValueAndCastFromMail(mail, sourceAttributeName, MAP_STRING_CALENDAR_CLASS)
                .ifPresent(calendars -> AttributeUtils.getValueAndCastFromMail(mail, rawSourceAttributeName, MAP_STRING_BYTES_CLASS)
                    .ifPresent(Throwing.<Map<String, AttributeValue<byte[]>>>consumer(rawCalendars ->
                        setAttribute(mail, calendars, rawCalendars)).sneakyThrow()));
        } catch (ClassCastException e) {
            LOGGER.error("Received a mail with {} not being an ICAL object for mail {}", sourceAttributeName, mail.getName(), e);
        }
    }

    private void setAttribute(Mail mail, Map<String, AttributeValue<Calendar>> calendars, Map<String, AttributeValue<byte[]>> rawCalendars) throws MessagingException {
        Optional<MailAddress> sender = retrieveSender(mail);
        if (!sender.isPresent()) {
            LOGGER.info("Skipping {} because no sender and no from", mail.getName());
            return;
        }

        MailAddress transportSender = sender.get();
        MailAddress replyTo = fetchReplyTo(mail).orElse(transportSender);

        Map<String, AttributeValue<?>> jsonsInByteForm = calendars.entrySet()
            .stream()
            .flatMap(calendar -> toJson(calendar, rawCalendars, mail, transportSender, replyTo))
            .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));
        if (!jsonsInByteForm.isEmpty()) {
            mail.setAttribute(new Attribute(destinationAttributeName, AttributeValue.of(jsonsInByteForm)));
        }
    }

    private Optional<MailAddress> fetchReplyTo(Mail mail) throws MessagingException {
        return Optional.ofNullable(mail.getMessage())
            .flatMap(Throwing.<MimeMessage, Optional<String[]>>function(mimeMessage ->
                    Optional.ofNullable(mimeMessage.getHeader(REPLY_TO_HEADER_NAME))
                ).sneakyThrow())
            .filter(headers -> headers.length > 0)
            .map(headers -> headers[0])
            .flatMap(this::retrieveReplyTo);
    }

    private Optional<MailAddress> retrieveReplyTo(String headerValue) {
        return LenientAddressParser.DEFAULT
            .parseAddressList(headerValue)
            .flatten()
            .stream()
            .flatMap(this::convertMailboxToMailAddress)
            .findFirst();

    }

    private Stream<MailAddress> convertMailboxToMailAddress(Mailbox mailbox) {
        try {
            return Stream.of(new MailAddress(mailbox.getAddress()));
        } catch (AddressException e) {
            return Stream.empty();
        }
    }

    private Stream<Pair<String, AttributeValue<byte[]>>> toJson(Map.Entry<String, AttributeValue<Calendar>> entry,
                                                Map<String, AttributeValue<byte[]>> rawCalendars,
                                                Mail mail,
                                                MailAddress sender,
                                                MailAddress replyTo) {
        return mail.getRecipients()
            .stream()
            .flatMap(recipient -> toICAL(entry, rawCalendars, recipient, sender, replyTo))
            .flatMap(ical -> toJson(ical, mail.getName()))
            .map(json -> Pair.of(UUID.randomUUID().toString(),
                AttributeValue.of(json.getBytes(StandardCharsets.UTF_8))));
    }

    private Stream<String> toJson(ICALAttributeDTO ical, String mailName) {
        try {
            return Stream.of(objectMapper.writeValueAsString(ical));
        } catch (JsonProcessingException e) {
            LOGGER.error("Error while serializing Calendar for mail {}", mailName, e);
            return Stream.of();
        } catch (Exception e) {
            LOGGER.error("Exception caught while attaching ICAL to the email as JSON for mail {}", mailName, e);
            return Stream.of();
        }
    }

    private Stream<ICALAttributeDTO> toICAL(Map.Entry<String, AttributeValue<Calendar>> entry,
                                            Map<String, AttributeValue<byte[]>> rawCalendars,
                                            MailAddress recipient,
                                            MailAddress sender,
                                            MailAddress replyTo) {
        Calendar calendar = entry.getValue().getValue();
        Optional<byte[]> rawICal = Optional.ofNullable(rawCalendars.get(entry.getKey())).map(AttributeValue::getValue);
        if (!rawICal.isPresent()) {
            LOGGER.debug("Cannot find matching raw ICAL from key: {}", entry.getKey());
            return Stream.of();
        }
        if (calendar.getComponent("VEVENT") == null) {
            LOGGER.debug("Calendar {} do not contains VEVENT", entry.getKey());
            return Stream.of();
        }
        try {
            return Stream.of(ICALAttributeDTO.builder()
                .from(calendar, rawICal.get())
                .sender(sender)
                .recipient(recipient)
                .replyTo(replyTo));
        } catch (Exception e) {
            LOGGER.error("Exception while converting calendar to ICAL", e);
            return Stream.of();
        }
    }

    private Optional<MailAddress> retrieveSender(Mail mail) throws MessagingException {
        Optional<MailAddress> fromMime = StreamUtils.ofOptional(
            Optional.ofNullable(mail.getMessage())
                .map(Throwing.function(MimeMessage::getFrom).orReturn(new Address[]{})))
            .map(InternetAddress.class::cast)
            .map(InternetAddress::getAddress)
            .map(MaybeSender::getMailSender)
            .flatMap(MaybeSender::asStream)
            .findFirst();

        return fromMime.or(() -> mail.getMaybeSender().asOptional());
    }
}
