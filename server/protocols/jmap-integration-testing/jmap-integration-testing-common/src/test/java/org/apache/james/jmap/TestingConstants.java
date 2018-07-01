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

import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public interface TestingConstants {
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
    String BOB = "bob@" + DOMAIN;
    String BOB_PASSWORD = "123456";
    String ALICE = "alice@" + DOMAIN;
    String ALICE_PASSWORD = "789123";
    String CEDRIC = "cedric@" + DOMAIN;
    String CEDRIC_PASSWORD = "456789";


    String LOCALHOST_IP = "127.0.0.1";
    int SMTP_PORT = 1025;
    int IMAP_PORT = 1143;


}
