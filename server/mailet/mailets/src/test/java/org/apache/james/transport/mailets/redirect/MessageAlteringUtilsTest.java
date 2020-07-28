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
package org.apache.james.transport.mailets.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.mailet.Mail;
import org.junit.jupiter.api.Test;

public class MessageAlteringUtilsTest {

    @Test
    void buildShouldThrowWhenMailetIsNull() {
        assertThatThrownBy(() -> MessageAlteringUtils.from(null).build())
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'mailet' is mandatory");
    }

    @Test
    void buildShouldThrowWhenOriginalMailIsNull() {
        assertThatThrownBy(() -> MessageAlteringUtils.from(mock(RedirectNotify.class))
                .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'originalMail' is mandatory");
    }

    @Test
    void buildShouldWorkWhenEverythingProvided() {
        MessageAlteringUtils.from(mock(RedirectNotify.class))
            .originalMail(mock(Mail.class))
            .build();
    }

    @Test
    void getFileNameShouldReturnNoSubjectWhenSubjectIsNull() {
        MessageAlteringUtils alteredMailUtils = MessageAlteringUtils.from(mock(RedirectNotify.class))
                .originalMail(mock(Mail.class))
                .build();

        String fileName = alteredMailUtils.getFileName(null);

        assertThat(fileName).isEqualTo("No Subject");
    }

    @Test
    void getFileNameShouldReturnNoSubjectWhenSubjectContainsOnlySpaces() {
        MessageAlteringUtils alteredMailUtils = MessageAlteringUtils.from(mock(RedirectNotify.class))
                .originalMail(mock(Mail.class))
                .build();

        String fileName = alteredMailUtils.getFileName("    ");

        assertThat(fileName).isEqualTo("No Subject");
    }

    @Test
    void getFileNameShouldReturnSubjectWhenSubjectIsGiven() {
        MessageAlteringUtils alteredMailUtils = MessageAlteringUtils.from(mock(RedirectNotify.class))
                .originalMail(mock(Mail.class))
                .build();

        String fileName = alteredMailUtils.getFileName("my Subject");

        assertThat(fileName).isEqualTo("my Subject");
    }

    @Test
    void getFileNameShouldReturnTrimmedSubjectWhenSubjectStartsWithSpaces() {
        MessageAlteringUtils alteredMailUtils = MessageAlteringUtils.from(mock(RedirectNotify.class))
                .originalMail(mock(Mail.class))
                .build();

        String fileName = alteredMailUtils.getFileName("    my Subject");

        assertThat(fileName).isEqualTo("my Subject");
    }
}
