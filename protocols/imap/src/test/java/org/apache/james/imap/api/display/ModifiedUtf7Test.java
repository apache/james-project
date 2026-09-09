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
package org.apache.james.imap.api.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.MalformedInputException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModifiedUtf7Test {
    @Test
    void decodeShouldReturnUnencodedValueAsIs() {
        assertThat(ModifiedUtf7.decodeModifiedUTF7("INBOX.test")).isEqualTo("INBOX.test");
    }

    @Test
    void decodeShouldSupportShiftSequences() {
        assertThat(ModifiedUtf7.decodeModifiedUTF7("&AOE-")).isEqualTo("á");
    }

    @Test
    void decodeShouldSupportEscapedAmpersand() {
        assertThat(ModifiedUtf7.decodeModifiedUTF7("&-")).isEqualTo("&");
    }

    @Test
    void encodeThenDecodeShouldRoundTrip() {
        String value = "Dossier & éléments";

        assertThat(ModifiedUtf7.decodeModifiedUTF7(ModifiedUtf7.encodeModifiedUTF7(value))).isEqualTo(value);
    }

    /**
     * Those used to be reported as a java.lang.Error, which would tear down the IMAP connection
     * rather than being answered a BAD response.
     *
     * See https://github.com/linagora/tmail-backend/issues/2611
     */
    @ParameterizedTest
    @ValueSource(strings = {"&", "&&", "&A", "&AO", "&AOE", "&x-", "test&", "a&b"})
    void decodeShouldThrowMalformedUtf7ExceptionOnInvalidShiftSequence(String input) {
        assertThatThrownBy(() -> ModifiedUtf7.decodeModifiedUTF7(input))
            .isInstanceOf(MalformedUtf7Exception.class)
            .hasCauseInstanceOf(MalformedInputException.class);
    }
}
