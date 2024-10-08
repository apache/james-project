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

package org.apache.james.mailets.configuration;

import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

public class Constants {
    public static Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    public static Duration ONE_MILLISECOND = Duration.ofMillis(1);

    public static ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(ONE_MILLISECOND)
        .await();
    public static ConditionFactory awaitAtMostOneMinute = calmlyAwait.atMost(ONE_MINUTE);

    public static final String DEFAULT_DOMAIN = "james.org";
    public static final String LOCALHOST_IP = "127.0.0.1";
    public static final String PASSWORD = "secret";
    public static final String FROM = "from@" + DEFAULT_DOMAIN;
    public static final String FROM2 = "from2@" + DEFAULT_DOMAIN;
    public static final String RECIPIENT = "recipient@" + DEFAULT_DOMAIN;
    public static final String ALIAS = "recipientalias@" + DEFAULT_DOMAIN;
    public static final String RECIPIENT2 = "recipient2@" + DEFAULT_DOMAIN;
}
