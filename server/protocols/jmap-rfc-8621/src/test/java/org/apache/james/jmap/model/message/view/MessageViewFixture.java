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

package org.apache.james.jmap.model.message.view;

import org.apache.james.core.Username;
import org.apache.james.jmap.model.Emailer;

import com.google.common.collect.ImmutableMap;

public interface MessageViewFixture {
    Username BOB = Username.of("bob@local");

    Emailer BOB_EMAIL = Emailer.builder().name(BOB.getLocalPart()).email(BOB.asString()).build();
    Emailer ALICE_EMAIL = Emailer.builder().name("alice").email("alice@local").build();
    Emailer JACK_EMAIL = Emailer.builder().name("jack").email("jack@local").build();
    Emailer JACOB_EMAIL = Emailer.builder().name("jacob").email("jacob@local").build();

    ImmutableMap<String, String> HEADERS_MAP = ImmutableMap.<String, String>builder()
        .put("Content-Type", "multipart/mixed; boundary=\"------------7AF1D14DE1DFA16229726B54\"")
        .put("Date", "Tue, 7 Jun 2016 16:23:37 +0200")
        .put("From", "alice <alice@local>")
        .put("To", "bob <bob@local>")
        .put("Subject", "Full message")
        .put("Mime-Version", "1.0")
        .put("Message-ID", "<1cc7f114-dbc4-42c2-99bd-f1100db6d0c1@open-paas.org>")
        .put("Cc", "jack <jack@local>, jacob <jacob@local>")
        .put("Bcc", "alice <alice@local>")
        .put("Reply-to", "alice <alice@local>")
        .put("In-reply-to", "bob@local")
        .build();
}
