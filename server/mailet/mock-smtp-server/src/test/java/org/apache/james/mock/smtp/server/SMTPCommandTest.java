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

package org.apache.james.mock.smtp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SMTPCommandTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void jacksonShouldDeserializeRsetCommand() throws Exception {
        SMTPCommand command = OBJECT_MAPPER.readValue("\"RSET\"", SMTPCommand.class);

        assertThat(command).isEqualTo(SMTPCommand.RSET);
    }

    @Test
    void jacksonShouldDeserializeRcptToCommand() throws Exception {
        SMTPCommand command = OBJECT_MAPPER.readValue("\"RCPT TO\"", SMTPCommand.class);

        assertThat(command).isEqualTo(SMTPCommand.RCPT_TO);
    }

    @Test
    void jacksonShouldDeserializeMailFromCommand() throws Exception {
        SMTPCommand command = OBJECT_MAPPER.readValue("\"MAIL FROM\"", SMTPCommand.class);

        assertThat(command).isEqualTo(SMTPCommand.MAIL_FROM);
    }

    @Test
    void jacksonShouldDeserializeNoopCommand() throws Exception {
        SMTPCommand command = OBJECT_MAPPER.readValue("\"NOOP\"", SMTPCommand.class);

        assertThat(command).isEqualTo(SMTPCommand.NOOP);
    }

    @Test
    void jacksonShouldDeserializeEhloCommand() throws Exception {
        SMTPCommand command = OBJECT_MAPPER.readValue("\"EHLO\"", SMTPCommand.class);

        assertThat(command).isEqualTo(SMTPCommand.EHLO);
    }

    @Test
    void jacksonShouldDeserializeVrfyCommand() throws Exception {
        SMTPCommand command = OBJECT_MAPPER.readValue("\"VRFY\"", SMTPCommand.class);

        assertThat(command).isEqualTo(SMTPCommand.VRFY);
    }

    @Test
    void jacksonShouldDeserializeDataCommand() throws Exception {
        SMTPCommand command = OBJECT_MAPPER.readValue("\"DATA\"", SMTPCommand.class);

        assertThat(command).isEqualTo(SMTPCommand.DATA);
    }

    @Test
    void jacksonShouldDeserializeQuitCommand() throws Exception {
        SMTPCommand command = OBJECT_MAPPER.readValue("\"QUIT\"", SMTPCommand.class);

        assertThat(command).isEqualTo(SMTPCommand.QUIT);
    }

    @Test
    void jacksonShouldThrowWhenDeserializingInvalidValue() {
        assertThatThrownBy(() -> OBJECT_MAPPER.readValue("\"invalid\"", SMTPCommand.class))
            .isInstanceOf(IOException.class);
    }

    @Test
    void jacksonShouldSerializeRsetCommand() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(SMTPCommand.RSET);

        assertThat(json).isEqualTo("\"RSET\"");
    }

    @Test
    void jacksonShouldSerializeRcptToCommand() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(SMTPCommand.RCPT_TO);

        assertThat(json).isEqualTo("\"RCPT TO\"");
    }

    @Test
    void jacksonShouldSerializeMailFromCommand() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(SMTPCommand.MAIL_FROM);

        assertThat(json).isEqualTo("\"MAIL FROM\"");
    }

    @Test
    void jacksonShouldSerializeNoopCommand() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(SMTPCommand.NOOP);

        assertThat(json).isEqualTo("\"NOOP\"");
    }

    @Test
    void jacksonShouldSerializeEhloCommand() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(SMTPCommand.EHLO);

        assertThat(json).isEqualTo("\"EHLO\"");
    }

    @Test
    void jacksonShouldSerializeVrfyCommand() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(SMTPCommand.VRFY);

        assertThat(json).isEqualTo("\"VRFY\"");
    }

    @Test
    void jacksonShouldSerializeDataCommand() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(SMTPCommand.DATA);

        assertThat(json).isEqualTo("\"DATA\"");
    }

    @Test
    void jacksonShouldSerializeQuitCommand() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(SMTPCommand.QUIT);

        assertThat(json).isEqualTo("\"QUIT\"");
    }
}