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

package org.apache.james.mailbox.store.search.mime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

class EMailersTest {

    @Test
    void fromShouldThrowWhenSetIsNull() {
        assertThatThrownBy(() -> EMailers.from(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'emailers' is mandatory");
    }

    @Test
    void serializeShouldReturnEmptyWhenEmptySet() {
        EMailers eMailers = EMailers.from(ImmutableSet.of());

        assertThat(eMailers.serialize()).isEmpty();
    }

    @Test
    void serializeShouldNotJoinWhenOneElement() {
        EMailer emailer = new EMailer(Optional.of("name"), "address@domain.tld", "domain.tld");
        EMailers eMailers = EMailers.from(ImmutableSet.of(emailer));

        assertThat(eMailers.serialize()).isEqualTo(emailer.serialize());
    }

    @Test
    void serializeShouldJoinWhenMultipleElements() {
        EMailer emailer = new EMailer(Optional.of("name"), "address@domain.tld", "domain.tld");
        EMailer emailer2 = new EMailer(Optional.of("name2"), "address2@domain.tld", "domain.tld");
        EMailer emailer3 = new EMailer(Optional.of("name3"), "address3@domain.tld", "domain.tld");

        String expected = Joiner.on(" ").join(emailer.serialize(), emailer2.serialize(), emailer3.serialize());

        EMailers eMailers = EMailers.from(ImmutableSet.of(emailer, emailer2, emailer3));

        assertThat(eMailers.serialize()).isEqualTo(expected);
    }
}
