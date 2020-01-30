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

package org.apache.james.jmap;

import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.james.core.Username;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

public interface JMAPTestingConstants {
    Duration slowPacedPollInterval = Duration.ONE_HUNDRED_MILLISECONDS;
    Duration ONE_MILLISECOND = new Duration(1, TimeUnit.MILLISECONDS);

    ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and().with()
        .pollDelay(ONE_MILLISECOND)
        .await();

    RequestSpecBuilder jmapRequestSpecBuilder = new RequestSpecBuilder()
        .setContentType(ContentType.JSON)
        .setAccept(ContentType.JSON)
        .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)));

    String NAME = "[0][0]";
    String ARGUMENTS = "[0][1]";
    String FIRST_MAILBOX = ARGUMENTS + ".list[0]";
    String SECOND_MAILBOX = ARGUMENTS + ".list[1]";
    String SECOND_NAME = "[1][0]";
    String SECOND_ARGUMENTS = "[1][1]";

    String DOMAIN = "domain.tld";
    String DOMAIN_ALIAS = "domain-alias.tld";
    Username BOB = Username.of("bob@" + DOMAIN);
    String BOB_PASSWORD = "123456";
    Username ALICE = Username.of("alice@" + DOMAIN);
    String ALICE_PASSWORD = "789123";
    Username CEDRIC = Username.of("cedric@" + DOMAIN);
    String CEDRIC_PASSWORD = "456789";


    String LOCALHOST_IP = "127.0.0.1";
}
