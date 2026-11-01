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

package org.apache.james.event.json;

import static org.apache.james.event.json.SerializerFixture.DTO_JSON_SERIALIZE;
import static org.apache.james.event.json.SerializerFixture.EVENT_SERIALIZER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import play.api.libs.json.JsNumber;
import play.api.libs.json.JsString;
import scala.math.BigDecimal;

class InvalidValuesDeserializationTest {
    private static String aclUpdatedJson(String eventId, String user, String aclKey, String rights) {
        return "{\"MailboxACLUpdated\":{" +
            "\"eventId\":\"" + eventId + "\"," +
            "\"mailboxPath\":{\"namespace\":\"#private\",\"user\":\"bob\",\"name\":\"mailboxName\"}," +
            "\"aclDiff\":{\"oldACL\":{},\"newACL\":{\"" + aclKey + "\":\"" + rights + "\"}}," +
            "\"mailboxId\":\"23\",\"sessionId\":6,\"user\":\"" + user + "\"}}";
    }

    private static final String VALID_EVENT_ID = "6e0dd59d-660e-4d9b-b22f-0354479f47b4";

    @Test
    void validJsonShouldBeDeserialized() {
        assertThat(EVENT_SERIALIZER.fromJson(aclUpdatedJson(VALID_EVENT_ID, "user", "$any", "a")).isSuccess())
            .isTrue();
    }

    @Test
    void invalidRightShouldBeAJsError() {
        assertThat(EVENT_SERIALIZER.fromJson(aclUpdatedJson(VALID_EVENT_ID, "user", "$any", "a|")).isError())
            .isTrue();
    }

    @Test
    void invalidEntryKeyShouldBeAJsError() {
        assertThat(EVENT_SERIALIZER.fromJson(aclUpdatedJson(VALID_EVENT_ID, "user", "", "a")).isError())
            .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@", "@d", "a@-d", "a@d.1x"})
    void invalidUserShouldBeAJsError(String user) {
        assertThat(EVENT_SERIALIZER.fromJson(aclUpdatedJson(VALID_EVENT_ID, user, "$any", "a")).isError())
            .isTrue();
    }

    @Test
    void invalidEventIdShouldBeAJsError() {
        assertThat(EVENT_SERIALIZER.fromJson(aclUpdatedJson("6e0d", "user", "$any", "a")).isError())
            .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{\"MailboxACLUpdated\":", "{\"type\":\"org.apache.james.jmap.change.StateChangeEvent\"}"})
    void unreadableJsonShouldBeAJsError(String json) {
        assertThat(EVENT_SERIALIZER.fromJson(json).isError())
            .isTrue();
    }

    @Test
    void invalidQuotaScopeShouldBeAJsError() {
        assertThat(DTO_JSON_SERIALIZE.quotaScopeReads().reads(new JsString("Doma")).isError())
            .isTrue();
    }

    @Test
    void invalidQuotaRootShouldBeAJsError() {
        assertThat(DTO_JSON_SERIALIZE.quotaRootReads().reads(new JsString("#privatefoo")).isError())
            .isTrue();
    }

    @Test
    void negativeModSeqShouldBeAJsError() {
        assertThat(DTO_JSON_SERIALIZE.modSeqReads().reads(new JsNumber(BigDecimal.valueOf(-1))).isError())
            .isTrue();
    }

    @Test
    void invalidMessageUidKeyShouldBeAJsError() {
        String json = "{\"Expunged\":{" +
            "\"eventId\":\"" + VALID_EVENT_ID + "\"," +
            "\"mailboxPath\":{\"namespace\":\"#private\",\"user\":\"bob\",\"name\":\"mailboxName\"}," +
            "\"mailboxId\":\"18\",\"sessionId\":42,\"user\":\"user\"," +
            "\"expunged\":{\"1234u6\":{\"uid\":123456,\"modSeq\":35,\"flags\":{\"systemFlags\":[],\"userFlags\":[]}," +
            "\"size\":45,\"internalDate\":\"2018-12-14T09:41:51.541Z\",\"messageId\":\"42\",\"threadId\":\"42\"}}}}";

        assertThat(EVENT_SERIALIZER.fromJson(json).isError())
            .isTrue();
    }
}
