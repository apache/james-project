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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.event.json.JsonSerialize;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import play.api.libs.json.JsError;
import play.api.libs.json.JsNull$;
import play.api.libs.json.JsNumber;
import play.api.libs.json.JsString;
import play.api.libs.json.Json;
import scala.math.BigDecimal;

class ACLDiffTest {
    private static final JsonSerialize JSON_SERIALIZE = new JsonSerialize(new TestId.Factory(), new TestMessageId.Factory());

    @Test
    void deSerializeShouldThrowWhenNewACLIsMissing() {
        assertThat(JSON_SERIALIZE.aclDiffReads().reads(Json.parse(
            "{\"oldACL\":{}}")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void deSerializeShouldThrowWhenOldACLIsMissing() {
        assertThat(JSON_SERIALIZE.aclDiffReads().reads(Json.parse(
            "{\"newACL\":{}}")))
            .isInstanceOf(JsError.class);
    }

    @Nested
    class EntryKeyTest {
        @Test
        void deSerializeShouldThrowWhenNotIncludedNameInEntryKey() {
            assertThatThrownBy(() -> JSON_SERIALIZE.aclEntryKeyReads().reads(new JsString("$")))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void deSerializeShouldThrowWhenNameInEntryKeyIsEmpty() {
            assertThatThrownBy(() -> JSON_SERIALIZE.aclEntryKeyReads().reads(new JsString("")))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deSerializeShouldThrowWhenNameInEntryKeyIsNotWellFormatted() {
            assertThatThrownBy(() -> JSON_SERIALIZE.aclEntryKeyReads().reads(new JsString("-")))
                .isInstanceOf(StringIndexOutOfBoundsException.class);
        }

        @Test
        void deSerializeShouldThrowWhenNameInEntryKeyIsNull() {
            assertThat(JSON_SERIALIZE.aclEntryKeyReads().reads(JsNull$.MODULE$))
                .isInstanceOf(JsError.class);
        }

        @Test
        void deSerializeShouldThrowWhenNameInEntryKeyIsNotString() {
            assertThat(JSON_SERIALIZE.aclEntryKeyReads().reads(new JsNumber(BigDecimal.valueOf(18))))
                .isInstanceOf(JsError.class);
        }
    }

    @Nested
    class RightTest {
        @Test
        void deSerializeShouldThrowWhenUnsupportedRightInNewACL() {
            assertThat(JSON_SERIALIZE.aclDiffReads().reads(new JsString("\"unsupported\"")))
                .isInstanceOf(JsError.class);
        }

        @Test
        void deSerializeShouldThrowWhenNull() {
            assertThat(JSON_SERIALIZE.aclDiffReads().reads(JsNull$.MODULE$))
                .isInstanceOf(JsError.class);
        }

        @Test
        void deSerializeShouldThrowWhenRightIsNotString() {
            assertThat(JSON_SERIALIZE.aclDiffReads().reads(new JsNumber(BigDecimal.valueOf(18))))
                .isInstanceOf(JsError.class);
        }
    }
}
