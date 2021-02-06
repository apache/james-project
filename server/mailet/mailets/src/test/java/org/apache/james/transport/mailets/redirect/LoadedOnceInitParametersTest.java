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

import java.util.Optional;

import org.junit.jupiter.api.Test;

class LoadedOnceInitParametersTest {
    @Test
    void fromShouldTakeValueFromInitParameters() {
        InitParameters expectedParameters = new MyInitParameters();

        InitParameters parameters = LoadedOnceInitParameters.from(expectedParameters);

        assertThat(parameters.getPassThrough()).isEqualTo(expectedParameters.getPassThrough());
        assertThat(parameters.getFakeDomainCheck()).isEqualTo(expectedParameters.getFakeDomainCheck());
        assertThat(parameters.getInLineType()).isEqualTo(expectedParameters.getInLineType());
        assertThat(parameters.getAttachmentType()).isEqualTo(expectedParameters.getAttachmentType());
        assertThat(parameters.getMessage()).isEqualTo(expectedParameters.getMessage());
        assertThat(parameters.getSubject()).isEqualTo(expectedParameters.getSubject());
        assertThat(parameters.getSubjectPrefix()).isEqualTo(expectedParameters.getSubjectPrefix());
        assertThat(parameters.isAttachError()).isEqualTo(expectedParameters.isAttachError());
        assertThat(parameters.isReply()).isEqualTo(expectedParameters.isReply());
        assertThat(parameters.getRecipients()).isEqualTo(expectedParameters.getRecipients());
        assertThat(parameters.getTo()).isEqualTo(expectedParameters.getTo());
        assertThat(parameters.getReversePath()).isEqualTo(expectedParameters.getReversePath());
        assertThat(parameters.getSender()).isEqualTo(expectedParameters.getSender());
        assertThat(parameters.getReplyTo()).isEqualTo(expectedParameters.getReplyTo());
        assertThat(parameters.isDebug()).isEqualTo(expectedParameters.isDebug());
        assertThat(parameters.isStatic()).isEqualTo(expectedParameters.isStatic());
    }

    private static class MyInitParameters implements InitParameters {

        @Override
        public boolean getPassThrough() {
            return true;
        }

        @Override
        public boolean getFakeDomainCheck() {
            return true;
        }

        @Override
        public TypeCode getInLineType() {
            return TypeCode.ALL;
        }

        @Override
        public TypeCode getAttachmentType() {
            return TypeCode.BODY;
        }

        @Override
        public String getMessage() {
            return "message";
        }

        @Override
        public String getSubject() {
            return "subject";
        }

        @Override
        public String getSubjectPrefix() {
            return "prefix";
        }

        @Override
        public boolean isAttachError() {
            return true;
        }

        @Override
        public boolean isReply() {
            return true;
        }

        @Override
        public Optional<String> getRecipients() {
            return Optional.of("recipients");
        }

        @Override
        public Optional<String> getTo() {
            return Optional.of("to");
        }

        @Override
        public Optional<String> getReversePath() {
            return Optional.of("reversePath");
        }

        @Override
        public Optional<String> getSender() {
            return Optional.of("sender");
        }

        @Override
        public Optional<String> getReplyTo() {
            return Optional.of("replyTo");
        }

        @Override
        public boolean isDebug() {
            return true;
        }

        @Override
        public boolean isStatic() {
            return true;
        }

        @Override
        public String asString() {
            return InitParametersSerializer.serialize(this);
        }
    }
}
