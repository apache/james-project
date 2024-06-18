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

import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.model.QuotaRoot;
import org.junit.jupiter.api.Test;

import play.api.libs.json.JsError;
import play.api.libs.json.JsNull$;
import play.api.libs.json.JsNumber;
import play.api.libs.json.JsString;
import scala.math.BigDecimal;

class QuotaRootTest {
    @Test
    void quotaRootWithDomainShouldBeWellSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaRootWrites().writes(QuotaRoot.quotaRoot("bob@domain.tld", Optional.of(Domain.of("domain.tld")))))
            .isEqualTo(new JsString("bob@domain.tld"));
    }

    @Test
    void quotaRootWithDomainShouldBeWellDeSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaRootReads().reads(new JsString("#private&bob@domain.tld")).get())
            .isEqualTo(QuotaRoot.quotaRoot("#private&bob@domain.tld", Optional.of(Domain.of("domain.tld"))));
    }

    @Test
    void quotaRootShouldBeWellSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaRootWrites().writes(QuotaRoot.quotaRoot("bob", Optional.empty())))
            .isEqualTo(new JsString("bob"));
    }

    @Test
    void quotaRootShouldBeWellDeSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaRootReads().reads(new JsString("#private&bob")).get())
            .isEqualTo(QuotaRoot.quotaRoot("#private&bob", Optional.empty()));
    }

    @Test
    void quotaRootWithAndShouldBeWellDeSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaRootReads().reads(new JsString("#private&bob&marley")).get())
            .isEqualTo(QuotaRoot.quotaRoot("#private&bob&marley", Optional.empty()));
    }

    @Test
    void emptyQuotaRootShouldBeWellSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaRootWrites().writes(QuotaRoot.quotaRoot("", Optional.empty())))
            .isEqualTo(new JsString(""));
    }

    @Test
    void emptyQuotaRootShouldBeWellDeSerialized() {
        assertThatThrownBy(() -> DTO_JSON_SERIALIZE.quotaRootReads().reads(new JsString("")).get())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("username should not be null or empty after being trimmed");
    }

    @Test
    void emptyQuotaRootShouldReturnErrorWhenNull() {
        assertThat(DTO_JSON_SERIALIZE.quotaRootReads().reads(JsNull$.MODULE$))
            .isInstanceOf(JsError.class);
    }

    @Test
    void emptyQuotaRootShouldReturnErrorWhenNotString() {
        assertThat(DTO_JSON_SERIALIZE.quotaRootReads().reads(new JsNumber(BigDecimal.valueOf(18))))
            .isInstanceOf(JsError.class);
    }
}
