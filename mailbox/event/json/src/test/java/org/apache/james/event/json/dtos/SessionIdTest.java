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

package org.apache.james.event.json.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.event.json.JsonSerialize;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Test;

import play.api.libs.json.JsError;
import play.api.libs.json.JsNull$;
import play.api.libs.json.JsNumber;
import play.api.libs.json.JsPath;
import play.api.libs.json.JsString;
import play.api.libs.json.JsSuccess;
import scala.collection.immutable.List;
import scala.math.BigDecimal;

class SessionIdTest {
    private static final JsonSerialize JSON_SERIALIZE = new JsonSerialize(new TestId.Factory(), new TestMessageId.Factory());

    @Test
    void sessionIdShouldBeWellSerialized() {
        assertThat(JSON_SERIALIZE.sessionIdWrites().writes(MailboxSession.SessionId.of(18)).toString())
            .isEqualTo("18");
    }

    @Test
    void sessionIdShouldBeWellDeSerialized() {
        assertThat(JSON_SERIALIZE.sessionIdReads().reads(new JsNumber(BigDecimal.valueOf(18))))
            .isEqualTo(new JsSuccess<>(MailboxSession.SessionId.of(18), new JsPath(List.empty())));
    }

    @Test
    void sessionIdShouldReturnErrorWhenString() {
        assertThat(JSON_SERIALIZE.sessionIdReads().reads(new JsString("18")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void sessionIdShouldReturnErrorWhenNull() {
        assertThat(JSON_SERIALIZE.sessionIdReads().reads(JsNull$.MODULE$))
            .isInstanceOf(JsError.class);
    }
}
