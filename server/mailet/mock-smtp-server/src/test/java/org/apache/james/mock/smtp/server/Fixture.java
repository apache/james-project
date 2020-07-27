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

package org.apache.james.mock.smtp.server;

import org.apache.james.core.MailAddress;
import org.apache.james.mock.smtp.server.jackson.MailAddressModule;
import org.apache.james.mock.smtp.server.model.Condition;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.model.MockSMTPBehavior;
import org.apache.james.mock.smtp.server.model.MockSmtpBehaviors;
import org.apache.james.mock.smtp.server.model.Operator;
import org.apache.james.mock.smtp.server.model.Response;
import org.apache.james.mock.smtp.server.model.SMTPCommand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;

public interface Fixture {

    class MailsFixutre {
        static Mail MAIL_1;
        static Mail MAIL_2;

        static {
            try {
                MAIL_1 = new Mail(
                new Mail.Envelope(
                    new MailAddress(BOB), new MailAddress(ALICE), new MailAddress(JACK)),
                "bob to alice and jack");

                MAIL_2 = new Mail(
                new Mail.Envelope(
                    new MailAddress(ALICE), new MailAddress(BOB)),
                "alice to bob");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    String DOMAIN = "james.org";
    String BOB = "bob@" + DOMAIN;
    String ALICE = "alice@" + DOMAIN;
    String JACK = "jack@" + DOMAIN;

    ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new GuavaModule())
        .registerModule(MailAddressModule.MODULE);

    Response RESPONSE = new Response(Response.SMTPStatusCode.of(250), "message");

    String JSON_BEHAVIOR_COMPULSORY_FIELDS = "{" +
        "  \"condition\": {\"operator\":\"matchAll\"}," +
        "  \"response\": {\"code\":250, \"message\":\"OK\"}," +
        "  \"command\": \"EHLO\"" +
        "}";

    MockSMTPBehavior BEHAVIOR_COMPULSORY_FIELDS = new MockSMTPBehavior(
        SMTPCommand.EHLO,
        Condition.MATCH_ALL,
        new Response(Response.SMTPStatusCode.ACTION_COMPLETE_250, "OK"),
        MockSMTPBehavior.NumberOfAnswersPolicy.anytime());

    String JSON_BEHAVIOR_ALL_FIELDS = "{" +
        "  \"response\": {\"code\":250, \"message\":\"OK\"}," +
        "  \"condition\": {\"operator\":\"contains\", \"matchingValue\":\"matchme\"}," +
        "  \"command\": \"EHLO\"," +
        "  \"numberOfAnswer\": 7" +
        "}";

    MockSMTPBehavior BEHAVIOR_ALL_FIELDS = new MockSMTPBehavior(
        SMTPCommand.EHLO,
        new Condition.OperatorCondition(Operator.CONTAINS, "matchme"),
        new Response(Response.SMTPStatusCode.of(250), "OK"),
        MockSMTPBehavior.NumberOfAnswersPolicy.times(7));

    MockSMTPBehavior BEHAVIOR_MATCHING_EVERYTIME = new MockSMTPBehavior(
        SMTPCommand.MAIL_FROM,
        Condition.MATCH_ALL,
        new Response(Response.SMTPStatusCode.COMMAND_NOT_IMPLEMENTED_502, "match all messages"),
        MockSMTPBehavior.NumberOfAnswersPolicy.anytime());
    MockSMTPBehavior BEHAVIOR_MATCHING_2_TIMES = new MockSMTPBehavior(
        SMTPCommand.MAIL_FROM,
        Condition.MATCH_ALL,
        new Response(Response.SMTPStatusCode.COMMAND_NOT_IMPLEMENTED_502, "match all messages"),
        MockSMTPBehavior.NumberOfAnswersPolicy.times(2));
    MockSMTPBehavior BEHAVIOR_MATCHING_3_TIMES = new MockSMTPBehavior(
        SMTPCommand.MAIL_FROM,
        Condition.MATCH_ALL,
        new Response(Response.SMTPStatusCode.COMMAND_NOT_IMPLEMENTED_502, "match all messages"),
        MockSMTPBehavior.NumberOfAnswersPolicy.times(3));

    String JSON_BEHAVIORS = "[" + JSON_BEHAVIOR_ALL_FIELDS + ", "
        + JSON_BEHAVIOR_COMPULSORY_FIELDS + "]";

    ImmutableList<MockSMTPBehavior> BEHAVIOR_LIST = ImmutableList.of(
        BEHAVIOR_ALL_FIELDS,
        BEHAVIOR_COMPULSORY_FIELDS);
    MockSmtpBehaviors BEHAVIORS = new MockSmtpBehaviors(BEHAVIOR_LIST);

    String JSON_MAILS_LIST = "[" +
        "  {\"from\":\"bob@james.org\",\"recipients\":[\"alice@james.org\", \"jack@james.org\"],\"message\":\"bob to alice and jack\"}," +
        "  {\"from\":\"alice@james.org\",\"recipients\":[\"bob@james.org\"],\"message\":\"alice to bob\"}" +
        "]";

    String JSON_MAIL = "[{\"from\":\"bob@james.org\",\"recipients\":[\"alice@james.org\", \"jack@james.org\"],\"message\":\"bob to alice and jack\"}]";
}
