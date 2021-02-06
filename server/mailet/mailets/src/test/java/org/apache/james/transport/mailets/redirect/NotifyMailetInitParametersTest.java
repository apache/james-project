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

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotifyMailetInitParametersTest {
    private GenericMailet mailet;

    @BeforeEach
    void setup() {
        mailet = new GenericMailet() {
            
            @Override
            public void service(Mail mail) {
            }
        };
    }

    @Test
    void getPassThroughShouldReturnTrueWhenSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("passThrough", "true")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean passThrough = testee.getPassThrough();
        assertThat(passThrough).isTrue();
    }

    @Test
    void getPassThroughShouldReturnFalseWhenSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("passThrough", "false")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean passThrough = testee.getPassThrough();
        assertThat(passThrough).isFalse();
    }

    @Test
    void getPassThroughShouldReturnTrueWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean passThrough = testee.getPassThrough();
        assertThat(passThrough).isTrue();
    }

    @Test
    void getFakeDomainCheckShouldReturnTrueWhenSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("fakeDomainCheck", "true")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean fakeDomainCheck = testee.getFakeDomainCheck();
        assertThat(fakeDomainCheck).isTrue();
    }

    @Test
    void getFakeDomainCheckShouldReturnFalseWhenSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("fakeDomainCheck", "false")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean fakeDomainCheck = testee.getFakeDomainCheck();
        assertThat(fakeDomainCheck).isFalse();
    }

    @Test
    void getFakeDomainCheckShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean fakeDomainCheck = testee.getFakeDomainCheck();
        assertThat(fakeDomainCheck).isFalse();
    }

    @Test
    void getInLineTypeShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("inline", "unaltered")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        TypeCode inLineType = testee.getInLineType();
        assertThat(inLineType).isEqualTo(TypeCode.UNALTERED);
    }

    @Test
    void getInLineTypeShouldReturnNoneWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        TypeCode inLineType = testee.getInLineType();
        assertThat(inLineType).isEqualTo(TypeCode.NONE);
    }

    @Test
    void getAttachmentTypeShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("attachment", "unaltered")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        TypeCode attachmentType = testee.getAttachmentType();
        assertThat(attachmentType).isEqualTo(TypeCode.UNALTERED);
    }

    @Test
    void getAttachmentTypeShouldReturnMessageWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        TypeCode attachmentType = testee.getAttachmentType();
        assertThat(attachmentType).isEqualTo(TypeCode.MESSAGE);
    }

    @Test
    void getMessageShouldReturnNoticeValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("notice", "my notice")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String message = testee.getMessage();
        assertThat(message).isEqualTo("my notice");
    }

    @Test
    void getMessageShouldReturnMessageValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("message", "my message")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String message = testee.getMessage();
        assertThat(message).isEqualTo("my message");
    }

    @Test
    void getMessageShouldReturnDefaultMessageWhenNoticeAndMessageNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String message = testee.getMessage();
        assertThat(message).isEqualTo("We were unable to deliver the attached message because of an error in the mail server.");
    }

    @Test
    void getSubjectShouldReturnNull() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String subject = testee.getSubject();
        assertThat(subject).isNull();
    }

    @Test
    void getSubjectPrefixShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("prefix", "my prefix")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String prefix = testee.getSubjectPrefix();
        assertThat(prefix).isEqualTo("my prefix");
    }

    @Test
    void getSubjectPrefixShouldReturnDefaultValueWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String prefix = testee.getSubjectPrefix();
        assertThat(prefix).isEqualTo("Re:");
    }

    @Test
    void isAttachErrorShouldReturnTrueWhenSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("attachError", "true")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean attachError = testee.isAttachError();
        assertThat(attachError).isTrue();
    }

    @Test
    void isAttachErrorShouldReturnFalseWhenSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("attachError", "false")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean attachError = testee.isAttachError();
        assertThat(attachError).isFalse();
    }

    @Test
    void isAttachErrorShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean attachError = testee.isAttachError();
        assertThat(attachError).isFalse();
    }

    @Test
    void isReplyShouldReturnTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean isReply = testee.isReply();
        assertThat(isReply).isTrue();
    }

    @Test
    void getRecipientsShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("recipients", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> recipients = testee.getRecipients();
        assertThat(recipients).contains("user@james.org, user2@james.org");
    }

    @Test
    void getRecipientsShouldReturnAbsentWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("recipients", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> recipients = testee.getRecipients();
        assertThat(recipients).isEmpty();
    }

    @Test
    void getRecipientsShouldReturnAbsentWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> recipients = testee.getRecipients();
        assertThat(recipients).isEmpty();
    }

    @Test
    void getToShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("to", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> to = testee.getTo();
        assertThat(to).contains("user@james.org, user2@james.org");
    }

    @Test
    void getToShouldReturnAbsentWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("to", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> to = testee.getTo();
        assertThat(to).isEmpty();
    }

    @Test
    void getToShouldReturnAbsentWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> to = testee.getTo();
        assertThat(to).isEmpty();
    }

    @Test
    void getReversePathShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("reversePath", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> reversePath = testee.getReversePath();
        assertThat(reversePath).contains("user@james.org, user2@james.org");
    }

    @Test
    void getReversePathShouldReturnAbsentWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("reversePath", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> reversePath = testee.getReversePath();
        assertThat(reversePath).isEmpty();
    }

    @Test
    void getReversePathShouldReturnAbsentWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> reversePath = testee.getReversePath();
        assertThat(reversePath).isEmpty();
    }

    @Test
    void getSenderShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("sender", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> sender = testee.getSender();
        assertThat(sender).contains("user@james.org, user2@james.org");
    }

    @Test
    void getSenderShouldReturnAbsentWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("sender", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> sender = testee.getSender();
        assertThat(sender).isEmpty();
    }

    @Test
    void getSenderShouldReturnAbsentWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> sender = testee.getSender();
        assertThat(sender).isEmpty();
    }

    @Test
    void getReplyToShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("replyTo", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> replyTo = testee.getReplyTo();
        assertThat(replyTo).contains("user@james.org, user2@james.org");
    }

    @Test
    void getReplyToShouldReturnreplytoValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("replyto", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> replyTo = testee.getReplyTo();
        assertThat(replyTo).contains("user@james.org, user2@james.org");
    }

    @Test
    void getReplyToShouldReturnAbsentWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("replyTo", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> replyTo = testee.getReplyTo();
        assertThat(replyTo).isEmpty();
    }

    @Test
    void getReplyToShouldReturnAbsentWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        Optional<String> replyTo = testee.getReplyTo();
        assertThat(replyTo).isEmpty();
    }

    @Test
    void isDebugShouldReturnTrueWhenSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("debug", "true")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean debug = testee.isDebug();
        assertThat(debug).isTrue();
    }

    @Test
    void isDebugShouldReturnFalseWhenSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("debug", "false")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean debug = testee.isDebug();
        assertThat(debug).isFalse();
    }

    @Test
    void isDebugShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean debug = testee.isDebug();
        assertThat(debug).isFalse();
    }

    @Test
    void isStaticShouldReturnTrueWhenSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("static", "true")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean isStatic = testee.isStatic();
        assertThat(isStatic).isTrue();
    }

    @Test
    void isStaticShouldReturnFalseWhenSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("static", "false")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean isStatic = testee.isStatic();
        assertThat(isStatic).isFalse();
    }

    @Test
    void isStaticShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean isStatic = testee.isStatic();
        assertThat(isStatic).isFalse();
    }
}
