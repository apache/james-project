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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import javax.mail.internet.MimeMessage;

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.utils.IMAPMessageReader;
import org.apache.james.mailets.utils.SMTPMessageSender;
import org.apache.james.transport.mailets.amqp.AmqpRule;
import org.apache.james.util.streams.SwarmGenericContainer;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

public class ICSAttachmentWorkflowTest {

    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int IMAP_PORT = 1143;
    private static final int SMTP_PORT = 1025;
    private static final String PASSWORD = "secret";

    private static final String JAMES_APACHE_ORG = "james.org";

    private static final String FROM = "fromUser@" + JAMES_APACHE_ORG;
    private static final String RECIPIENT = "touser@" + JAMES_APACHE_ORG;

    private static final String MAIL_ATTRIBUTE = "my.attribute";
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

    public SwarmGenericContainer rabbitMqContainer = new SwarmGenericContainer("rabbitmq:3")
            .withAffinityToContainer();
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    public AmqpRule amqpRule = new AmqpRule(rabbitMqContainer, EXCHANGE_NAME, ROUTING_KEY);

    @Rule
    public final RuleChain chain = RuleChain.outerRule(temporaryFolder).around(rabbitMqContainer).around(amqpRule);

    private ConditionFactory calmlyAwait;
    private TemporaryJamesServer jamesServer;
    private MimeMessage messageWithoutICSAttached;
    private MimeMessage messageWithICSAttached;

    @Before
    public void setup() throws Exception {
        MailetContainer mailetContainer = MailetContainer.builder()
            .postmaster("postmaster@" + JAMES_APACHE_ORG)
            .threads(5)
            .addProcessor(CommonProcessors.root())
            .addProcessor(CommonProcessors.error())
            .addProcessor(ProcessorConfiguration.builder()
                    .state("transport")
                    .enableJmx(true)
                    .addMailet(MailetConfiguration.builder()
                            .match("All")
                            .clazz("RemoveMimeHeader")
                            .addProperty("name", "bcc")
                            .build())
                    .addMailet(MailetConfiguration.builder()
                            .match("All")
                            .clazz("StripAttachment")
                            .addProperty("attribute", MAIL_ATTRIBUTE)
                            .addProperty("pattern", ".*")
                            .build())
                    .addMailet(MailetConfiguration.builder()
                            .match("All")
                            .clazz("MimeDecodingMailet")
                            .addProperty("attribute", MAIL_ATTRIBUTE)
                            .build())
                    .addMailet(MailetConfiguration.builder()
                            .match("All")
                            .clazz("ICalendarParser")
                            .addProperty("sourceAttribute", MAIL_ATTRIBUTE)
                            .addProperty("destinationAttribute", MAIL_ATTRIBUTE)
                            .build())
                    .addMailet(MailetConfiguration.builder()
                            .match("All")
                            .clazz("ICALToHeader")
                            .addProperty("attribute", MAIL_ATTRIBUTE)
                            .build())
                    .addMailet(MailetConfiguration.builder()
                            .match("All")
                            .clazz("ICALToJsonAttribute")
                            .addProperty("source", MAIL_ATTRIBUTE)
                            .addProperty("destination", MAIL_ATTRIBUTE)
                            .build())
                    .addMailet(MailetConfiguration.builder()
                            .match("All")
                            .clazz("AmqpForwardAttribute")
                            .addProperty("uri", amqpRule.getAmqpUri())
                            .addProperty("exchange", EXCHANGE_NAME)
                            .addProperty("attribute", MAIL_ATTRIBUTE)
                            .addProperty("routing_key", ROUTING_KEY)
                            .build())
                    .addMailet(MailetConfiguration.builder()
                            .match("RecipientIsLocal")
                            .clazz("org.apache.james.jmap.mailet.VacationMailet")
                            .build())
                    .addMailet(MailetConfiguration.builder()
                            .match("RecipientIsLocal")
                            .clazz("LocalDelivery")
                            .build())
                    .build())
            .build();

        jamesServer = new TemporaryJamesServer(temporaryFolder, mailetContainer);
        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with().pollInterval(slowPacedPollInterval).and().with().pollDelay(slowPacedPollInterval).await();

        jamesServer.getServerProbe().addDomain(JAMES_APACHE_ORG);
        jamesServer.getServerProbe().addUser(FROM, PASSWORD);
        jamesServer.getServerProbe().addUser(RECIPIENT, PASSWORD);
        jamesServer.getServerProbe().createMailbox(MailboxConstants.USER_NAMESPACE, RECIPIENT, "INBOX");

        messageWithoutICSAttached = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .build(),
                MimeMessageBuilder.bodyPartBuilder()
                    .data("My attachment")
                    .filename("test.txt")
                    .disposition("attachment")
                    .build())
            .setSubject("test")
            .build();

        messageWithICSAttached = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .build(),
                MimeMessageBuilder.bodyPartBuilder()
                    .data(ICS_1.getBytes(Charsets.UTF_8))
                    .filename("meeting.ics")
                    .disposition("attachment")
                    .build())
            .setSubject("test")
            .build();
    }

    @After
    public void tearDown() throws Exception {
        jamesServer.shutdown();
    }

    @Test
    public void calendarAttachmentShouldNotBePublishedInMQWhenNoICalAttachment() throws Exception {
        Mail mail = FakeMail.builder()
              .mimeMessage(messageWithoutICSAttached)
              .sender(new MailAddress(FROM))
              .recipient(new MailAddress(RECIPIENT))
              .build();

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
                IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(mail);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(RECIPIENT, PASSWORD));
        }

        assertThat(amqpRule.readContent()).isEmpty();
    }

    @Test
    public void calendarAttachmentShouldBePublishedInMQWhenMatchingWorkflowConfiguration() throws Exception {
        Mail mail = FakeMail.builder()
              .mimeMessage(messageWithICSAttached)
              .sender(new MailAddress(FROM))
              .recipient(new MailAddress(RECIPIENT))
              .build();

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
                IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(mail);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(RECIPIENT, PASSWORD));
        }

        Optional<String> content = amqpRule.readContent();
        assertThat(content).isPresent();
        DocumentContext jsonPath = toJsonPath(content.get());
        assertThat(jsonPath.<String> read("ical")).isEqualTo(ICS_1);
        assertThat(jsonPath.<String> read("sender")).isEqualTo(FROM);
        assertThat(jsonPath.<String> read("recipient")).isEqualTo(RECIPIENT);
        assertThat(jsonPath.<String> read("uid")).isEqualTo(ICS_UID);
        assertThat(jsonPath.<String> read("sequence")).isEqualTo(ICS_SEQUENCE);
        assertThat(jsonPath.<String> read("dtstamp")).isEqualTo(ICS_DTSTAMP);
        assertThat(jsonPath.<String> read("method")).isEqualTo(ICS_METHOD);
        assertThat(jsonPath.<String> read("recurrence-id")).isNull();
    }

    private DocumentContext toJsonPath(String content) {
        return JsonPath.using(Configuration.defaultConfiguration()
                .addOptions(Option.SUPPRESS_EXCEPTIONS))
            .parse(content);
    }

    @Test
    public void headersShouldNotBeAddedInMailWhenNoICalAttachment() throws Exception {
        Mail mail = FakeMail.builder()
              .mimeMessage(messageWithoutICSAttached)
              .sender(new MailAddress(FROM))
              .recipient(new MailAddress(RECIPIENT))
              .build();

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
                IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(mail);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(RECIPIENT, PASSWORD));

            String receivedHeaders = imapMessageReader.readFirstMessageHeadersInInbox(RECIPIENT, PASSWORD);

            assertThat(receivedHeaders).doesNotContain("X-MEETING-UID");
            assertThat(receivedHeaders).doesNotContain("X-MEETING-METHOD");
            assertThat(receivedHeaders).doesNotContain("X-MEETING-RECURRENCE-ID");
            assertThat(receivedHeaders).doesNotContain("X-MEETING-SEQUENCE");
            assertThat(receivedHeaders).doesNotContain("X-MEETING-DTSTAMP");
        }
    }

    @Test
    public void headersShouldBeAddedInMailWhenOneICalAttachment() throws Exception {
        Mail mail = FakeMail.builder()
              .mimeMessage(messageWithICSAttached)
              .sender(new MailAddress(FROM))
              .recipient(new MailAddress(RECIPIENT))
              .build();

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
                IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(mail);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(RECIPIENT, PASSWORD));

            String receivedHeaders = imapMessageReader.readFirstMessageHeadersInInbox(RECIPIENT, PASSWORD);

            assertThat(receivedHeaders).contains("X-MEETING-UID: " + ICS_UID);
            assertThat(receivedHeaders).contains("X-MEETING-METHOD: " + ICS_METHOD);
            assertThat(receivedHeaders).contains("X-MEETING-SEQUENCE: " + ICS_SEQUENCE);
            assertThat(receivedHeaders).contains("X-MEETING-DTSTAMP: " + ICS_DTSTAMP);
        }
    }

    @Ignore("See JIRA issue MAILET-151")
    @Test
    public void headersShouldBeFilledOnlyWithOneICalAttachmentWhenMailHasSeveral() throws Exception {
        MimeMessage messageWithThreeICSAttached = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .build(),
                MimeMessageBuilder.bodyPartBuilder()
                    .data(ICS_1.getBytes(Charsets.UTF_8))
                    .filename("test.txt")
                    .disposition("attachment")
                    .build(),
                MimeMessageBuilder.bodyPartBuilder()
                    .data(ICS_2.getBytes(Charsets.UTF_8))
                    .filename("test.txt")
                    .disposition("attachment")
                    .build(),
                MimeMessageBuilder.bodyPartBuilder()
                    .data(ICS_3.getBytes(Charsets.UTF_8))
                    .filename("test.txt")
                    .disposition("attachment")
                    .build())
            .setSubject("test")
            .build();
        
        Mail mail = FakeMail.builder()
              .mimeMessage(messageWithThreeICSAttached)
              .sender(new MailAddress(FROM))
              .recipient(new MailAddress(RECIPIENT))
              .build();

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
                IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(mail);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(RECIPIENT, PASSWORD));

            String receivedHeaders = imapMessageReader.readFirstMessageHeadersInInbox(RECIPIENT, PASSWORD);
            
            //Here only the third ICal attachment is used to fill headers
            assertThat(receivedHeaders).contains("X-MEETING-UID: " + ICS_UID);
            assertThat(receivedHeaders).contains("X-MEETING-METHOD: " + ICS_METHOD);
            assertThat(receivedHeaders).contains("X-MEETING-SEQUENCE: " + ICS_SEQUENCE);
            assertThat(receivedHeaders).contains("X-MEETING-DTSTAMP: " + ICS_DTSTAMP);
        }
    }

}
