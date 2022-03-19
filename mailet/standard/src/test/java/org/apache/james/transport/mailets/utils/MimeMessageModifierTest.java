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

package org.apache.james.transport.mailets.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.junit.jupiter.api.Test;

public class MimeMessageModifierTest {

    @Test
    public void replaceSubjectShouldReplaceTheSubjectWhenSubjectIsPresent() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("subject")
            .build();

        String expectedSubject = "new subject";
        new MimeMessageModifier(message).replaceSubject(Optional.of(expectedSubject));

        assertThat(message.getSubject()).isEqualTo(expectedSubject);
    }

    @Test
    public void replaceSubjectShouldNotAlterTheSubjectWhenSubjectIsAbsent() throws Exception {
        String expectedSubject = "subject";
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
                .setSubject(expectedSubject)
                .build();

        new MimeMessageModifier(message).replaceSubject(Optional.empty());

        assertThat(message.getSubject()).isEqualTo(expectedSubject);
    }
}
