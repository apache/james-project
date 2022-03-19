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

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.mailet.TextCalendarBodyToAttachment;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.amqp.AmqpExtension;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

public class ICSAttachmentWorkflowTest {
    private static final String FROM = "fromUser@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;

    private static final String MAIL_ATTRIBUTE = "ical.attachments";
    private static final String PARSED_ICAL_MAIL_ATTRIBUTE = "ical.parsed";
    private static final String JSON_MAIL_ATTRIBUTE = "ical.json";
    private static final String EXCHANGE_NAME = "myExchange";
    private static final String ROUTING_KEY = "myRoutingKey";

    private static final String ICS_UID = "f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc";
    private static final String ICS_DTSTAMP = "20170106T115036Z";
    private static final String ICS_SEQUENCE = "0";
    private static final String ICS_METHOD = "REQUEST";
    private static final String ICS_1 = "BEGIN:VCALENDAR\r\n" +
            "PRODID:-//Aliasource Groupe LINAGORA//OBM Calendar 3.2.1-rc2//FR\r\n" +
            "CALSCALE:GREGORIAN\r\n" +
            "X-OBM-TIME:1483703436\r\n" +
            "VERSION:2.0\r\n" +
            "METHOD:" + ICS_METHOD + "\r\n" +
            "BEGIN:VEVENT\r\n" +
            "CREATED:20170106T115035Z\r\n" +
            "LAST-MODIFIED:20170106T115036Z\r\n" +
            "DTSTAMP:" + ICS_DTSTAMP + "\r\n" +
            "DTSTART:20170111T090000Z\r\n" +
            "DURATION:PT1H30M\r\n" +
            "TRANSP:OPAQUE\r\n" +
            "SEQUENCE:" + ICS_SEQUENCE + "\r\n" +
            "SUMMARY:Sprint planning #23\r\n" +
            "DESCRIPTION:\r\n" +
            "CLASS:PUBLIC\r\n" +
            "PRIORITY:5\r\n" +
            "ORGANIZER;X-OBM-ID=128;CN=Raphael OUAZANA:MAILTO:ouazana@linagora.com\r\n" +
            "X-OBM-DOMAIN:linagora.com\r\n" +
            "X-OBM-DOMAIN-UUID:02874f7c-d10e-102f-acda-0015176f7922\r\n" +
            "LOCATION:Hangout\r\n" +
            "CATEGORIES:\r\n" +
            "X-OBM-COLOR:\r\n" +
            "UID:" + ICS_UID + "\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Matthieu EXT_BAECHLER;PARTSTAT=NEEDS-ACTION;X-OBM-ID=302:MAILTO:baechler@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Laura ROYET;PARTSTAT=NEEDS-ACTION;X-OBM-ID=723:MAILTO:royet@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Raphael OUAZANA;PARTSTAT=ACCEPTED;X-OBM-ID=128:MAILTO:ouazana@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Luc DUZAN;PARTSTAT=NEEDS-ACTION;X-OBM-ID=715:MAILTO:duzan@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=RESOURCE;CN=Salle de reunion Lyon;PARTSTAT=ACCEPTED;X-OBM-ID=66:MAILTO:noreply@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Antoine DUPRAT;PARTSTAT=NEEDS-ACTION;X-OBM-ID=453:MAILTO:duprat@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Benoit TELLIER;PARTSTAT=NEEDS-ACTION;X-OBM-ID=623:MAILTO:tellier@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Quynh Quynh N NGUYEN;PARTSTAT=NEEDS-ACTION;X-OBM-ID=769:MAILTO:nguyen@linagora.com\r\n" +
            "END:VEVENT\r\n" +
            "END:VCALENDAR\r\n";

    private static final String ICS_2 = "BEGIN:VCALENDAR\n" +
            "PRODID:-//Aliasource Groupe LINAGORA//OBM Calendar 3.2.1-rc2//FR\n" +
            "CALSCALE:GREGORIAN\n" +
            "X-OBM-TIME:1483703436\n" +
            "VERSION:2.0\n" +
            "METHOD:REQUEST\n" +
            "BEGIN:VEVENT\n" +
            "CREATED:20170106T115035Z\n" +
            "LAST-MODIFIED:20170106T115036Z\n" +
            "DTSTAMP:20170106T115037Z\n" +
            "DTSTART:20170111T090000Z\n" +
            "DURATION:PT1H30M\n" +
            "TRANSP:OPAQUE\n" +
            "SEQUENCE:1\n" +
            "SUMMARY:Sprint planning #23\n" +
            "DESCRIPTION:\n" +
            "CLASS:PUBLIC\n" +
            "PRIORITY:5\n" +
            "ORGANIZER;X-OBM-ID=128;CN=Raphael OUAZANA:MAILTO:ouazana@linagora.com\n" +
            "X-OBM-DOMAIN:linagora.com\n" +
            "X-OBM-DOMAIN-UUID:02874f7c-d10e-102f-acda-0015176f7922\n" +
            "LOCATION:Hangout\n" +
            "CATEGORIES:\n" +
            "X-OBM-COLOR:\n" +
            "UID:f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047fe\n" +
            " b2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bd\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Matthieu EXT_BAECHLER;PARTSTAT=NEE\n" +
            " DS-ACTION;X-OBM-ID=302:MAILTO:baechler@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Laura ROYET;PARTSTAT=NEEDS-ACTION;\n" +
            " X-OBM-ID=723:MAILTO:royet@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Raphael OUAZANA;PARTSTAT=ACCEPTED;\n" +
            " X-OBM-ID=128:MAILTO:ouazana@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Luc DUZAN;PARTSTAT=NEEDS-ACTION;X-\n" +
            " OBM-ID=715:MAILTO:duzan@linagora.com\n" +
            "ATTENDEE;CUTYPE=RESOURCE;CN=Salle de reunion Lyon;PARTSTAT=ACCEPTED;X-OBM-\n" +
            " ID=66:MAILTO:noreply@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Antoine DUPRAT;PARTSTAT=NEEDS-ACTI\n" +
            " ON;X-OBM-ID=453:MAILTO:duprat@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Benoît TELLIER;PARTSTAT=NEEDS-ACTI\n" +
            " ON;X-OBM-ID=623:MAILTO:tellier@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Quynh Quynh N NGUYEN;PARTSTAT=NEED\n" +
            " S-ACTION;X-OBM-ID=769:MAILTO:nguyen@linagora.com\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

    private static final String ICS_3 = "BEGIN:VCALENDAR\n" +
            "PRODID:-//Aliasource Groupe LINAGORA//OBM Calendar 3.2.1-rc2//FR\n" +
            "CALSCALE:GREGORIAN\n" +
            "X-OBM-TIME:1483703436\n" +
            "VERSION:2.0\n" +
            "METHOD:REQUEST\n" +
            "BEGIN:VEVENT\n" +
            "CREATED:20170106T115035Z\n" +
            "LAST-MODIFIED:20170106T115036Z\n" +
            "DTSTAMP:20170106T115038Z\n" +
            "DTSTART:20170111T090000Z\n" +
            "DURATION:PT1H30M\n" +
            "TRANSP:OPAQUE\n" +
            "SEQUENCE:2\n" +
            "SUMMARY:Sprint planning #23\n" +
            "DESCRIPTION:\n" +
            "CLASS:PUBLIC\n" +
            "PRIORITY:5\n" +
            "ORGANIZER;X-OBM-ID=128;CN=Raphael OUAZANA:MAILTO:ouazana@linagora.com\n" +
            "X-OBM-DOMAIN:linagora.com\n" +
            "X-OBM-DOMAIN-UUID:02874f7c-d10e-102f-acda-0015176f7922\n" +
            "LOCATION:Hangout\n" +
            "CATEGORIES:\n" +
            "X-OBM-COLOR:\n" +
            "UID:f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047fe\n" +
            " b2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962be\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Matthieu EXT_BAECHLER;PARTSTAT=NEE\n" +
            " DS-ACTION;X-OBM-ID=302:MAILTO:baechler@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Laura ROYET;PARTSTAT=NEEDS-ACTION;\n" +
            " X-OBM-ID=723:MAILTO:royet@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Raphael OUAZANA;PARTSTAT=ACCEPTED;\n" +
            " X-OBM-ID=128:MAILTO:ouazana@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Luc DUZAN;PARTSTAT=NEEDS-ACTION;X-\n" +
            " OBM-ID=715:MAILTO:duzan@linagora.com\n" +
            "ATTENDEE;CUTYPE=RESOURCE;CN=Salle de reunion Lyon;PARTSTAT=ACCEPTED;X-OBM-\n" +
            " ID=66:MAILTO:noreply@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Antoine DUPRAT;PARTSTAT=NEEDS-ACTI\n" +
            " ON;X-OBM-ID=453:MAILTO:duprat@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Benoît TELLIER;PARTSTAT=NEEDS-ACTI\n" +
            " ON;X-OBM-ID=623:MAILTO:tellier@linagora.com\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Quynh Quynh N NGUYEN;PARTSTAT=NEED\n" +
            " S-ACTION;X-OBM-ID=769:MAILTO:nguyen@linagora.com\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

    private static final String ICS_BASE64 = "Content-Type: application/ics;\n" +
        " name=\"invite.ics\"\n" +
        "Content-Transfer-Encoding: base64\n" +
        "Content-Disposition: attachment;\n" +
        " filename=\"invite.ics\"\n" +
        "\n" +
        "QkVHSU46VkNBTEVOREFSDQpQUk9ESUQ6LS8vR29vZ2xlIEluYy8vR29vZ2xlIENhbGVuZGFy\n" +
        "IDcwLjkwNTQvL0VODQpWRVJTSU9OOjIuMA0KQ0FMU0NBTEU6R1JFR09SSUFODQpNRVRIT0Q6\n" +
        "UkVRVUVTVA0KQkVHSU46VkVWRU5UDQpEVFNUQVJUOjIwMTcwMTIwVDEzMDAwMFoNCkRURU5E\n" +
        "OjIwMTcwMTIwVDE0MDAwMFoNCkRUU1RBTVA6MjAxNzAxMTlUMTkxODIzWg0KT1JHQU5JWkVS\n" +
        "O0NOPUFudG9pbmUgRHVwcmF0Om1haWx0bzphbnRkdXByYXRAZ21haWwuY29tDQpVSUQ6YWg4\n" +
        "Nms1bTM0MmJtY3JiZTlraGtraGxuMDBAZ29vZ2xlLmNvbQ0KQVRURU5ERUU7Q1VUWVBFPUlO\n" +
        "RElWSURVQUw7Uk9MRT1SRVEtUEFSVElDSVBBTlQ7UEFSVFNUQVQ9TkVFRFMtQUNUSU9OO1JT\n" +
        "VlA9DQogVFJVRTtDTj1hZHVwcmF0QGxpbmFnb3JhLmNvbTtYLU5VTS1HVUVTVFM9MDptYWls\n" +
        "dG86YWR1cHJhdEBsaW5hZ29yYS5jb20NCkFUVEVOREVFO0NVVFlQRT1JTkRJVklEVUFMO1JP\n" +
        "TEU9UkVRLVBBUlRJQ0lQQU5UO1BBUlRTVEFUPUFDQ0VQVEVEO1JTVlA9VFJVRQ0KIDtDTj1B\n" +
        "bnRvaW5lIER1cHJhdDtYLU5VTS1HVUVTVFM9MDptYWlsdG86YW50ZHVwcmF0QGdtYWlsLmNv\n" +
        "bQ0KQ1JFQVRFRDoyMDE3MDExOVQxOTE4MjNaDQpERVNDUklQVElPTjpBZmZpY2hleiB2b3Ry\n" +
        "ZSDDqXbDqW5lbWVudCBzdXIgbGEgcGFnZSBodHRwczovL3d3dy5nb29nbGUuY29tL2NhbA0K\n" +
        "IGVuZGFyL2V2ZW50P2FjdGlvbj1WSUVXJmVpZD1ZV2c0Tm1zMWJUTTBNbUp0WTNKaVpUbHJh\n" +
        "R3RyYUd4dU1EQWdZV1IxY0hKaGRFQg0KIHNhVzVoWjI5eVlTNWpiMjAmdG9rPU1Ua2pZVzUw\n" +
        "WkhWd2NtRjBRR2R0WVdsc0xtTnZiVGcxT1RNNU5XTTRNR1JsWW1FMVlUSTROeg0KIFJqTjJV\n" +
        "eU5qVTBNMll5Wm1RNE56UmtOVGhoWVRRJmN0ej1FdXJvcGUvUGFyaXMmaGw9ZnIuDQpMQVNU\n" +
        "LU1PRElGSUVEOjIwMTcwMTE5VDE5MTgyM1oNCkxPQ0FUSU9OOg0KU0VRVUVOQ0U6MA0KU1RB\n" +
        "VFVTOkNPTkZJUk1FRA0KU1VNTUFSWToNClRSQU5TUDpPUEFRVUUNCkVORDpWRVZFTlQNCkVO\n" +
        "RDpWQ0FMRU5EQVINCg==";
    private static final String ICS_BASE64_UID = "ah86k5m342bmcrbe9khkkhln00@google.com";
    private static final String ICS_BASE64_DTSTAMP = "20170119T191823Z";
    private static final String ICS_YAHOO = "BEGIN:VCALENDAR\r\n" +
            "PRODID://Yahoo//Calendar//EN\r\n" +
            "VERSION:2.0\r\n" +
            "METHOD:REQUEST\r\n" +
            "BEGIN:VEVENT\r\n" +
            "SUMMARY:Test from Yahoo\r\n" +
            "CLASS:PUBLIC\r\n" +
            "DTSTART;TZID=Europe/Brussels:20170127T150000\r\n" +
            "DTEND;TZID=Europe/Brussels:20170127T160000\r\n" +
            "LOCATION:Somewhere\r\n" +
            "PRIORITY:0\r\n" +
            "SEQUENCE:0\r\n" +
            "STATUS:CONFIRMED\r\n" +
            "UID:5014513f-1026-4b58-82cf-80d4fc060bbe\r\n" +
            "DTSTAMP:20170123T121635Z\r\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;ROLE=REQ_PARTICIPANT;RSVP=TRUE;SCHEDULE-STAT\r\n" +
            " US=1.1:mailto:ddolcimascolo@linagora.com\r\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;ROLE=REQ_PARTICIPANT;RSVP=TRUE;SCHEDULE-STAT\r\n" +
            " US=1.1:mailto:rouazana@linagora.com\r\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;ROLE=REQ_PARTICIPANT;RSVP=TRUE;SCHEDULE-STAT\r\n" +
            " US=1.1:mailto:aduprat@linagora.com\r\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;ROLE=REQ_PARTICIPANT;RSVP=TRUE;SCHEDULE-STAT\r\n" +
            " US=1.1:mailto:btellier@linagora.com\r\n" +
            "ORGANIZER;CN=OBM Linagora;SENT-BY=\"mailto:obmlinagora@yahoo.fr\":mailto:obml\r\n" +
            " inagora@yahoo.fr\r\n" +
            "X-YAHOO-YID:obmlinagora\r\n" +
            "TRANSP:OPAQUE\r\n" +
            "STATUS:CONFIRMED\r\n" +
            "X-YAHOO-USER-STATUS:BUSY\r\n" +
            "X-YAHOO-EVENT-STATUS:BUSY\r\n" +
            "END:VEVENT\r\n" +
            "BEGIN:VTIMEZONE\r\n" +
            "TZID:Europe/Brussels\r\n" +
            "TZURL:http://tzurl.org/zoneinfo/Europe/Brussels\r\n" +
            "X-LIC-LOCATION:Europe/Brussels\r\n" +
            "BEGIN:DAYLIGHT\r\n" +
            "TZOFFSETFROM:+0100\r\n" +
            "TZOFFSETTO:+0200\r\n" +
            "TZNAME:CEST\r\n" +
            "DTSTART:19810329T020000\r\n" +
            "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU\r\n" +
            "END:DAYLIGHT\r\n" +
            "BEGIN:STANDARD\r\n" +
            "TZOFFSETFROM:+0200\r\n" +
            "TZOFFSETTO:+0100\r\n" +
            "TZNAME:CET\r\n" +
            "DTSTART:19961027T030000\r\n" +
            "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU\r\n" +
            "END:STANDARD\r\n" +
            "BEGIN:STANDARD\r\n" +
            "TZOFFSETFROM:+001730\r\n" +
            "TZOFFSETTO:+001730\r\n" +
            "TZNAME:BMT\r\n" +
            "DTSTART:18800101T000000\r\n" +
            "RDATE:18800101T000000\r\n" +
            "END:STANDARD\r\n" +
            "BEGIN:STANDARD\r\n" +
            "TZOFFSETFROM:+001730\r\n" +
            "TZOFFSETTO:+0000\r\n" +
            "TZNAME:WET\r\n" +
            "DTSTART:18920501T120000\r\n" +
            "RDATE:18920501T120000\r\n" +
            "END:STANDARD\r\n" +
            "BEGIN:STANDARD\r\n" +
            "TZOFFSETFROM:+0000\r\n" +
            "TZOFFSETTO:+0100\r\n" +
            "TZNAME:CET\r\n" +
            "DTSTART:19141108T000000\r\n" +
            "RDATE:19141108T000000\r\n" +
            "END:STANDARD\r\n" +
            "BEGIN:DAYLIGHT\r\n" +
            "TZOFFSETFROM:+0100\r\n" +
            "TZOFFSETTO:+0200\r\n" +
            "TZNAME:CEST\r\n" +
            "DTSTART:19160501T000000\r\n" +
            "RDATE:19160501T000000\r\n" +
            "RDATE:19170416T020000\r\n" +
            "RDATE:19180415T020000\r\n" +
            "RDATE:19400520T030000\r\n" +
            "RDATE:19430329T020000\r\n" +
            "RDATE:19440403T020000\r\n" +
            "RDATE:19450402T020000\r\n" +
            "RDATE:19460519T020000\r\n" +
            "RDATE:19770403T020000\r\n" +
            "RDATE:19780402T020000\r\n" +
            "RDATE:19790401T020000\r\n" +
            "RDATE:19800406T020000\r\n" +
            "END:DAYLIGHT\r\n" +
            "BEGIN:STANDARD\r\n" +
            "TZOFFSETFROM:+0200\r\n" +
            "TZOFFSETTO:+0100\r\n" +
            "TZNAME:CET\r\n" +
            "DTSTART:19161001T010000\r\n" +
            "RDATE:19161001T010000\r\n" +
            "RDATE:19170917T030000\r\n" +
            "RDATE:19180916T030000\r\n" +
            "RDATE:19421102T030000\r\n" +
            "RDATE:19431004T030000\r\n" +
            "RDATE:19440917T030000\r\n" +
            "RDATE:19450916T030000\r\n" +
            "RDATE:19461007T030000\r\n" +
            "RDATE:19770925T030000\r\n" +
            "RDATE:19781001T030000\r\n" +
            "RDATE:19790930T030000\r\n" +
            "RDATE:19800928T030000\r\n" +
            "RDATE:19810927T030000\r\n" +
            "RDATE:19820926T030000\r\n" +
            "RDATE:19830925T030000\r\n" +
            "RDATE:19840930T030000\r\n" +
            "RDATE:19850929T030000\r\n" +
            "RDATE:19860928T030000\r\n" +
            "RDATE:19870927T030000\r\n" +
            "RDATE:19880925T030000\r\n" +
            "RDATE:19890924T030000\r\n" +
            "RDATE:19900930T030000\r\n" +
            "RDATE:19910929T030000\r\n" +
            "RDATE:19920927T030000\r\n" +
            "RDATE:19930926T030000\r\n" +
            "RDATE:19940925T030000\r\n" +
            "RDATE:19950924T030000\r\n" +
            "END:STANDARD\r\n" +
            "BEGIN:STANDARD\r\n" +
            "TZOFFSETFROM:+0100\r\n" +
            "TZOFFSETTO:+0000\r\n" +
            "TZNAME:WET\r\n" +
            "DTSTART:19181111T120000\r\n" +
            "RDATE:19181111T120000\r\n" +
            "RDATE:19191005T000000\r\n" +
            "RDATE:19201024T000000\r\n" +
            "RDATE:19211026T000000\r\n" +
            "RDATE:19221008T000000\r\n" +
            "RDATE:19231007T000000\r\n" +
            "RDATE:19241005T000000\r\n" +
            "RDATE:19251004T000000\r\n" +
            "RDATE:19261003T000000\r\n" +
            "RDATE:19271002T000000\r\n" +
            "RDATE:19281007T030000\r\n" +
            "RDATE:19291006T030000\r\n" +
            "RDATE:19301005T030000\r\n" +
            "RDATE:19311004T030000\r\n" +
            "RDATE:19321002T030000\r\n" +
            "RDATE:19331008T030000\r\n" +
            "RDATE:19341007T030000\r\n" +
            "RDATE:19351006T030000\r\n" +
            "RDATE:19361004T030000\r\n" +
            "RDATE:19371003T030000\r\n" +
            "RDATE:19381002T030000\r\n" +
            "RDATE:19391119T030000\r\n" +
            "END:STANDARD\r\n" +
            "BEGIN:DAYLIGHT\r\n" +
            "TZOFFSETFROM:+0000\r\n" +
            "TZOFFSETTO:+0100\r\n" +
            "TZNAME:WEST\r\n" +
            "DTSTART:19190301T230000\r\n" +
            "RDATE:19190301T230000\r\n" +
            "RDATE:19200214T230000\r\n" +
            "RDATE:19210314T230000\r\n" +
            "RDATE:19220325T230000\r\n" +
            "RDATE:19230421T230000\r\n" +
            "RDATE:19240329T230000\r\n" +
            "RDATE:19250404T230000\r\n" +
            "RDATE:19260417T230000\r\n" +
            "RDATE:19270409T230000\r\n" +
            "RDATE:19280414T230000\r\n" +
            "RDATE:19290421T020000\r\n" +
            "RDATE:19300413T020000\r\n" +
            "RDATE:19310419T020000\r\n" +
            "RDATE:19320403T020000\r\n" +
            "RDATE:19330326T020000\r\n" +
            "RDATE:19340408T020000\r\n" +
            "RDATE:19350331T020000\r\n" +
            "RDATE:19360419T020000\r\n" +
            "RDATE:19370404T020000\r\n" +
            "RDATE:19380327T020000\r\n" +
            "RDATE:19390416T020000\r\n" +
            "RDATE:19400225T020000\r\n" +
            "END:DAYLIGHT\r\n" +
            "BEGIN:DAYLIGHT\r\n" +
            "TZOFFSETFROM:+0200\r\n" +
            "TZOFFSETTO:+0200\r\n" +
            "TZNAME:CEST\r\n" +
            "DTSTART:19440903T000000\r\n" +
            "RDATE:19440903T000000\r\n" +
            "END:DAYLIGHT\r\n" +
            "BEGIN:STANDARD\r\n" +
            "TZOFFSETFROM:+0100\r\n" +
            "TZOFFSETTO:+0100\r\n" +
            "TZNAME:CET\r\n" +
            "DTSTART:19770101T000000\r\n" +
            "RDATE:19770101T000000\r\n" +
            "END:STANDARD\r\n" +
            "END:VTIMEZONE\r\n" +
            "END:VCALENDAR\r\n" +
            "";


    @RegisterExtension
    public static AmqpExtension amqpExtension = new AmqpExtension(EXCHANGE_NAME, ROUTING_KEY);
    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private MimeMessage messageWithoutICSAttached;
    private MimeMessage messageWithICSAttached;
    private MimeMessage messageWithICSBase64Attached;
    private MimeMessage messageWithThreeICSAttached;
    private MimeMessage yahooInvitationMessage;

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.defaultMailetContainerConfiguration()
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(StripAttachment.class)
                    .addProperty("attribute", MAIL_ATTRIBUTE)
                    .addProperty("pattern", ".*"))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(MimeDecodingMailet.class)
                    .addProperty("attribute", MAIL_ATTRIBUTE))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ICalendarParser.class)
                    .addProperty("sourceAttribute", MAIL_ATTRIBUTE)
                    .addProperty("destinationAttribute", PARSED_ICAL_MAIL_ATTRIBUTE))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ICALToHeader.class)
                    .addProperty("attribute", PARSED_ICAL_MAIL_ATTRIBUTE))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ICALToJsonAttribute.class)
                    .addProperty("source", PARSED_ICAL_MAIL_ATTRIBUTE)
                    .addProperty("rawSource", MAIL_ATTRIBUTE)
                    .addProperty("destination", JSON_MAIL_ATTRIBUTE))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(TextCalendarBodyToAttachment.class))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(AmqpForwardAttribute.class)
                    .addProperty("uri", amqpExtension.getAmqpUri())
                    .addProperty("exchange", EXCHANGE_NAME)
                    .addProperty("attribute", JSON_MAIL_ATTRIBUTE)
                    .addProperty("routing_key", ROUTING_KEY))
                .addMailetsFrom(CommonProcessors.deliverOnlyTransport()));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(FROM, PASSWORD);

        messageWithoutICSAttached = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text"),
                MimeMessageBuilder.bodyPartBuilder()
                    .data("My attachment")
                    .filename("test.txt")
                    .disposition("attachment"))
            .setSubject("test")
            .build();

        messageWithICSAttached = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text"),
                MimeMessageBuilder.bodyPartBuilder()
                    .data(ICS_1.getBytes(StandardCharsets.UTF_8))
                    .filename("meeting.ics")
                    .disposition("attachment"))
            .setSubject("test")
            .build();

        messageWithICSBase64Attached = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .build(),
                MimeMessageBuilder.bodyPartFromBytes(ICS_BASE64.getBytes(StandardCharsets.UTF_8)))
            .setSubject("test")
            .build();

        yahooInvitationMessage = MimeMessageUtil.mimeMessageFromStream(ClassLoader.getSystemResourceAsStream("eml/yahooInvitation.eml"));

        messageWithThreeICSAttached = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text"),
                MimeMessageBuilder.bodyPartBuilder()
                    .data(ICS_1.getBytes(StandardCharsets.UTF_8))
                    .filename("test1.txt")
                    .disposition("attachment"),
                MimeMessageBuilder.bodyPartBuilder()
                    .data(ICS_2.getBytes(StandardCharsets.UTF_8))
                    .filename("test2.txt")
                    .disposition("attachment"),
                MimeMessageBuilder.bodyPartBuilder()
                    .data(ICS_3.getBytes(StandardCharsets.UTF_8))
                    .filename("test3.txt")
                    .disposition("attachment"))
            .setSubject("test")
            .build();
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void calendarAttachmentShouldNotBePublishedInMQWhenNoICalAttachment() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(messageWithoutICSAttached)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(amqpExtension.readContent()).isEmpty();
    }

    @Test
    void calendarAttachmentShouldBePublishedInMQWhenMatchingWorkflowConfiguration() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(messageWithICSAttached)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        Optional<String> content = amqpExtension.readContent();
        assertThat(content).isPresent();
        DocumentContext jsonPath = toJsonPath(content.get());
        assertThat(jsonPath.<String>read("ical")).isEqualTo(ICS_1);
        assertThat(jsonPath.<String>read("sender")).isEqualTo(FROM);
        assertThat(jsonPath.<String>read("recipient")).isEqualTo(RECIPIENT);
        assertThat(jsonPath.<String>read("uid")).isEqualTo(ICS_UID);
        assertThat(jsonPath.<String>read("sequence")).isEqualTo(ICS_SEQUENCE);
        assertThat(jsonPath.<String>read("dtstamp")).isEqualTo(ICS_DTSTAMP);
        assertThat(jsonPath.<String>read("method")).isEqualTo(ICS_METHOD);
        assertThat(jsonPath.<String>read("recurrence-id")).isNull();
    }

    private DocumentContext toJsonPath(String content) {
        return JsonPath.using(Configuration.defaultConfiguration()
                .addOptions(Option.SUPPRESS_EXCEPTIONS))
            .parse(content);
    }

    @Test
    void headersShouldNotBeAddedInMailWhenNoICalAttachment() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(messageWithoutICSAttached)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String receivedHeaders = testIMAPClient.readFirstMessageHeaders();
        assertThat(receivedHeaders).doesNotContain("X-MEETING-UID");
        assertThat(receivedHeaders).doesNotContain("X-MEETING-METHOD");
        assertThat(receivedHeaders).doesNotContain("X-MEETING-RECURRENCE-ID");
        assertThat(receivedHeaders).doesNotContain("X-MEETING-SEQUENCE");
        assertThat(receivedHeaders).doesNotContain("X-MEETING-DTSTAMP");
    }

    @Test
    void headersShouldBeAddedInMailWhenOneICalAttachment() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(messageWithICSAttached)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String receivedHeaders = testIMAPClient.readFirstMessageHeaders();
        assertThat(receivedHeaders).contains("X-MEETING-UID: " + ICS_UID);
        assertThat(receivedHeaders).contains("X-MEETING-METHOD: " + ICS_METHOD);
        assertThat(receivedHeaders).contains("X-MEETING-SEQUENCE: " + ICS_SEQUENCE);
        assertThat(receivedHeaders).contains("X-MEETING-DTSTAMP: " + ICS_DTSTAMP);
    }

    @Test
    void headersShouldBeAddedInMailWhenOneBase64ICalAttachment() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(messageWithICSBase64Attached)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String receivedHeaders = testIMAPClient.readFirstMessageHeaders();
        assertThat(receivedHeaders).contains("X-MEETING-UID: " + ICS_BASE64_UID);
        assertThat(receivedHeaders).contains("X-MEETING-METHOD: " + ICS_METHOD);
        assertThat(receivedHeaders).contains("X-MEETING-SEQUENCE: " + ICS_SEQUENCE);
        assertThat(receivedHeaders).contains("X-MEETING-DTSTAMP: " + ICS_BASE64_DTSTAMP);
    }

    @Test
    void base64CalendarAttachmentShouldBePublishedInMQWhenMatchingWorkflowConfiguration() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(messageWithICSBase64Attached)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        Optional<String> content = amqpExtension.readContent();
        assertThat(content).isPresent();
        DocumentContext jsonPath = toJsonPath(content.get());
        assertThat(jsonPath.<String>read("sender")).isEqualTo(FROM);
        assertThat(jsonPath.<String>read("recipient")).isEqualTo(RECIPIENT);
        assertThat(jsonPath.<String>read("uid")).isEqualTo(ICS_BASE64_UID);
        assertThat(jsonPath.<String>read("sequence")).isEqualTo(ICS_SEQUENCE);
        assertThat(jsonPath.<String>read("dtstamp")).isEqualTo(ICS_BASE64_DTSTAMP);
        assertThat(jsonPath.<String>read("method")).isEqualTo(ICS_METHOD);
        assertThat(jsonPath.<String>read("recurrence-id")).isNull();
    }

    @Test
    void yahooBase64CalendarAttachmentShouldBePublishedInMQWhenMatchingWorkflowConfiguration() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(yahooInvitationMessage)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        Optional<String> content = amqpExtension.readContent();
        assertThat(content).isPresent();
        DocumentContext jsonPath = toJsonPath(content.get());
        assertThat(jsonPath.<String>read("sender")).isEqualTo("obmlinagora@yahoo.fr");
        assertThat(jsonPath.<String>read("recipient")).isEqualTo(RECIPIENT);
        assertThat(jsonPath.<String>read("uid")).isEqualTo("5014513f-1026-4b58-82cf-80d4fc060bbe");
        assertThat(jsonPath.<String>read("sequence")).isEqualTo("0");
        assertThat(jsonPath.<String>read("dtstamp")).isEqualTo("20170123T121635Z");
        assertThat(jsonPath.<String>read("method")).isEqualTo("REQUEST");
        assertThat(jsonPath.<String>read("ical")).isEqualTo(ICS_YAHOO);
        assertThat(jsonPath.<String>read("recurrence-id")).isNull();
    }

    @Test
    void headersShouldBeFilledOnlyWithOneICalAttachmentWhenMailHasSeveral() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(messageWithThreeICSAttached)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String receivedHeaders = testIMAPClient.readFirstMessageHeaders();
        assertThat(receivedHeaders).contains("X-MEETING-UID: " + ICS_UID);
        assertThat(receivedHeaders).contains("X-MEETING-METHOD: " + ICS_METHOD);
        assertThat(receivedHeaders).contains("X-MEETING-SEQUENCE: " + ICS_SEQUENCE);
        assertThat(receivedHeaders).contains("X-MEETING-DTSTAMP: " + ICS_DTSTAMP);
    }

    @Test
    void pipelineShouldSendSeveralJSONOverRabbitMQWhenSeveralAttachments() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(messageWithThreeICSAttached)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        Optional<String> content1 = amqpExtension.readContent();
        assertThat(content1).isPresent();
        DocumentContext jsonPath1 = toJsonPath(content1.get());

        Optional<String> content2 = amqpExtension.readContent();
        assertThat(content2).isPresent();
        DocumentContext jsonPath2 = toJsonPath(content2.get());

        Optional<String> content3 = amqpExtension.readContent();
        assertThat(content3).isPresent();
        DocumentContext jsonPath3 = toJsonPath(content3.get());

        assertThat(
            ImmutableList.of(jsonPath1.read("uid"),
                jsonPath2.read("uid"),
                jsonPath3.read("uid")))
            .containsOnly(ICS_UID,
                "f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bd",
                "f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962be");

        assertThat(amqpExtension.readContent()).isEmpty();
    }

    @Test
    void mailShouldNotContainCalendarContentInTextBodyButAttachment() throws Exception {
        MimeMessage calendarMessage = MimeMessageUtil.mimeMessageFromStream(ClassLoader.getSystemResourceAsStream("eml/calendar.eml"));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(calendarMessage)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage())
            .containsSubsequence("Content-Type: multipart/mixed", "Content-Disposition: attachment");
    }
}
