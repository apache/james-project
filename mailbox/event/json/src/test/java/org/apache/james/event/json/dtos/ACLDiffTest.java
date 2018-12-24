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

import static org.apache.james.event.json.SerializerFixture.DTO_JSON_SERIALIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import play.api.libs.json.JsError;
import play.api.libs.json.JsNull$;
import play.api.libs.json.JsNumber;
import play.api.libs.json.JsString;
import play.api.libs.json.Json;
import scala.math.BigDecimal;

class ACLDiffTest {
    @Test
    void deSerializeShouldThrowWhenNewACLIsMissing() {
        assertThat(DTO_JSON_SERIALIZE.aclDiffReads().reads(Json.parse(
            "{\"oldACL\":{}}")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void deSerializeShouldAcceptDoubleRight() {
        assertThat(DTO_JSON_SERIALIZE.aclDiffReads().reads(Json.parse(
            "{\"oldACL\":{\"$any\":\"aa\"},\"newACL\":{}}"))
            .get().toJava())
            .isEqualTo(new ACLDiff(new MailboxACL(ImmutableMap.of(
                new MailboxACL.EntryKey("any", MailboxACL.NameType.group, false),
                new MailboxACL.Rfc4314Rights(MailboxACL.Right.Administer))),
                new MailboxACL()));
    }

    @Test
    void deSerializeShouldAcceptEmptyRight() {
        assertThat(DTO_JSON_SERIALIZE.aclDiffReads().reads(Json.parse(
            "{\"oldACL\":{\"$any\":\"\"},\"newACL\":{}}"))
            .get().toJava())
            .isEqualTo(new ACLDiff(new MailboxACL(ImmutableMap.of(
                new MailboxACL.EntryKey("any", MailboxACL.NameType.group, false),
                new MailboxACL.Rfc4314Rights())),
                new MailboxACL()));
    }

    @Test
    void deSerializeShouldThrowWhenOldACLIsMissing() {
        assertThat(DTO_JSON_SERIALIZE.aclDiffReads().reads(Json.parse(
            "{\"newACL\":{}}")))
            .isInstanceOf(JsError.class);
    }

    @Nested
    class EntryKeyTest {
        @Test
        void deSerializeShouldThrowWhenNotIncludedNameInEntryKey() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.aclEntryKeyReads().reads(new JsString("$")))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void deSerializeShouldThrowWhenNameInEntryKeyIsEmpty() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.aclEntryKeyReads().reads(new JsString("")))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deSerializeShouldThrowWhenNameInEntryKeyIsNotWellFormatted() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.aclEntryKeyReads().reads(new JsString("-")))
                .isInstanceOf(StringIndexOutOfBoundsException.class);
        }

        @Test
        void deSerializeShouldThrowWhenNameInEntryKeyIsNull() {
            assertThat(DTO_JSON_SERIALIZE.aclEntryKeyReads().reads(JsNull$.MODULE$))
                .isInstanceOf(JsError.class);
        }

        @Test
        void deSerializeShouldThrowWhenNameInEntryKeyIsNotString() {
            assertThat(DTO_JSON_SERIALIZE.aclEntryKeyReads().reads(new JsNumber(BigDecimal.valueOf(18))))
                .isInstanceOf(JsError.class);
        }
    }

    @Nested
    class RightTest {
        @Test
        void deSerializeShouldThrowWhenUnsupportedRightInNewACL() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.aclRightsReads().reads(new JsString("\"unsupported\"")))
                .isInstanceOf(UnsupportedRightException.class);
        }

        @Test
        void deSerializeShouldThrowWhenNull() {
            assertThat(DTO_JSON_SERIALIZE.aclRightsReads().reads(JsNull$.MODULE$))
                .isInstanceOf(JsError.class);
        }

        @Test
        void deSerializeShouldThrowWhenRightIsNotString() {
            assertThat(DTO_JSON_SERIALIZE.aclRightsReads().reads(new JsNumber(BigDecimal.valueOf(18))))
                .isInstanceOf(JsError.class);
        }
    }
}
