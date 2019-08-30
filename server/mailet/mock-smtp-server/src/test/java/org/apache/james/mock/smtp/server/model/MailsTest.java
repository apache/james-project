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

package org.apache.james.mock.smtp.server.model;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.mock.smtp.server.Fixture.ALICE;
import static org.apache.james.mock.smtp.server.Fixture.BOB;
import static org.apache.james.mock.smtp.server.Fixture.JACK;
import static org.apache.james.mock.smtp.server.Fixture.JSON_MAILS_LIST;
import static org.apache.james.mock.smtp.server.Fixture.OBJECT_MAPPER;
import static org.assertj.core.api.Java6Assertions.assertThat;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import net.javacrumbs.jsonunit.core.Option;
import net.javacrumbs.jsonunit.core.internal.Options;
import nl.jqno.equalsverifier.EqualsVerifier;

class MailsTest {
    private Mails mails;

    @BeforeEach
    void setup() throws Exception {
        Mail mail1 = new Mail(
            new Mail.Envelope(
                new MailAddress(BOB),
                ImmutableList.of(new MailAddress(ALICE), new MailAddress(JACK))),
            "bob to alice and jack");

        Mail mail2 = new Mail(
            new Mail.Envelope(
                new MailAddress(ALICE),
                ImmutableList.of(new MailAddress(BOB))),
            "alice to bob");

        mails = new Mails(ImmutableList.of(mail1, mail2));
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Mails.class)
            .verify();
    }

    @Test
    void jacksonShouldDeserializeMails() throws Exception {
        Mails actualMails = OBJECT_MAPPER.readValue(JSON_MAILS_LIST, Mails.class);

        assertThat(actualMails)
            .isEqualTo(mails);
    }

    @Test
    void jacksonShouldSerializeMails() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(mails);

        assertThatJson(json)
            .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT, Option.IGNORING_ARRAY_ORDER))
            .isEqualTo(JSON_MAILS_LIST);
    }
}
