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

import org.apache.james.mailbox.ModSeq;
import org.junit.jupiter.api.Test;

import play.api.libs.json.JsError;
import play.api.libs.json.JsNull$;
import play.api.libs.json.JsNumber;
import play.api.libs.json.JsString;
import scala.math.BigDecimal;

class ModSeqTest {
    @Test
    void messageUidShouldBeWellSerialized() {
        assertThat(DTO_JSON_SERIALIZE.modSeqWrites().writes(ModSeq.of(18)))
            .isEqualTo(new JsNumber(BigDecimal.valueOf(18)));
    }

    @Test
    void messageUidShouldBeWellDeSerialized() {
        assertThat(DTO_JSON_SERIALIZE.modSeqReads().reads(new JsNumber(BigDecimal.valueOf(18))).get())
            .isEqualTo(ModSeq.of(18));
    }

    @Test
    void messageUidShouldReturnErrorWhenString() {
        assertThat(DTO_JSON_SERIALIZE.modSeqReads().reads(new JsString("18")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void messageUidShouldReturnErrorWhenNull() {
        assertThat(DTO_JSON_SERIALIZE.modSeqReads().reads(JsNull$.MODULE$))
            .isInstanceOf(JsError.class);
    }
}
