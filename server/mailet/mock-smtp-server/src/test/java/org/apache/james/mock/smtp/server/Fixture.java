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

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;

public interface Fixture {
    ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new GuavaModule());

    Response RESPONSE = Response.serverAccept(Response.SMTPStatusCode.of(250), "message");

    String JSON_BEHAVIOR_COMPULSORY_FIELDS = "{" +
        "  \"response\": {\"code\":250, \"message\":\"OK\", \"rejected\":false}," +
        "  \"command\": \"EHLO\"" +
        "}";

    MockSMTPBehavior BEHAVIOR_COMPULSORY_FIELDS = new MockSMTPBehavior(
        SMTPCommand.EHLO,
        Optional.empty(),
        Response.serverAccept(Response.SMTPStatusCode.ACTION_COMPLETE_250, "OK"),
        MockSMTPBehavior.NumberOfAnswersPolicy.anytime());

    String JSON_BEHAVIOR_ALL_FIELDS = "{" +
        "  \"response\": {\"code\":250, \"message\":\"OK\", \"rejected\":false}," +
        "  \"condition\": {\"operator\":\"contains\", \"matchingValue\":\"matchme\"}," +
        "  \"command\": \"EHLO\"," +
        "  \"numberOfAnswer\": 7" +
        "}";

    MockSMTPBehavior BEHAVIOR_ALL_FIELDS = new MockSMTPBehavior(
        SMTPCommand.EHLO,
        Optional.of(new Condition.OperatorCondition(Operator.CONTAINS, "matchme")),
        Response.serverAccept(Response.SMTPStatusCode.of(250), "OK"),
        MockSMTPBehavior.NumberOfAnswersPolicy.times(7));

    String JSON_BEHAVIORS = "[" + JSON_BEHAVIOR_ALL_FIELDS + ", "
        + JSON_BEHAVIOR_COMPULSORY_FIELDS + "]";

    MockSmtpBehaviors BEHAVIORS = new MockSmtpBehaviors(ImmutableList.of(
        BEHAVIOR_ALL_FIELDS,
        BEHAVIOR_COMPULSORY_FIELDS));
}
