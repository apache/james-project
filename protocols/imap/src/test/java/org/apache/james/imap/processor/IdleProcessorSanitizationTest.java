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

package org.apache.james.imap.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdleProcessorSanitizationTest {

    @Test
    void sanitizeForDisplayShouldPreserveNormalCharacters() {
        assertThat(IdleProcessor.sanitizeForDisplay("DONE"))
            .isEqualTo("DONE");
        assertThat(IdleProcessor.sanitizeForDisplay("INVALID_COMMAND"))
            .isEqualTo("INVALID_COMMAND");
    }

    @Test
    void sanitizeForDisplayShouldStripControlCharacters() {
        assertThat(IdleProcessor.sanitizeForDisplay("LOG\r\nOUT\t\0"))
            .isEqualTo("LOGOUT");
    }

    @Test
    void sanitizeForDisplayShouldTruncateLongInputTo32CharactersWithEllipsis() {
        String longInput = "1234567890123456789012345678901234567890";
        assertThat(IdleProcessor.sanitizeForDisplay(longInput))
            .isEqualTo("12345678901234567890123456789012...");
    }

    @Test
    void sanitizeForDisplayShouldNotAddEllipsisWhenCleanedStringFitsLimit() {
        String inputWithManyControlChars = "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0HELLO";
        assertThat(IdleProcessor.sanitizeForDisplay(inputWithManyControlChars))
            .isEqualTo("HELLO");
    }

    @Test
    @SuppressWarnings("checkstyle:avoidescapedunicodecharacters")
    void sanitizeForDisplayShouldStripNonAsciiCharacters() {
        assertThat(IdleProcessor.sanitizeForDisplay("DONE\u200B\u00A0тест"))
            .isEqualTo("DONE");
    }
}
