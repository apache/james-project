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

class RedirectMailetInitParametersTest {
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
    public void getPassThroughShouldReturnTrueWhenSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("passThrough", "true")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean passThrough = testee.getPassThrough();
        assertThat(passThrough).isTrue();
    }

    @Test
    public void getPassThroughShouldReturnFalseWhenSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("passThrough", "false")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean passThrough = testee.getPassThrough();
        assertThat(passThrough).isFalse();
    }

    @Test
    public void getPassThroughShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean passThrough = testee.getPassThrough();
        assertThat(passThrough).isFalse();
    }

    @Test
    public void getFakeDomainCheckShouldReturnTrueWhenSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("fakeDomainCheck", "true")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean fakeDomainCheck = testee.getFakeDomainCheck();
        assertThat(fakeDomainCheck).isTrue();
    }

    @Test
    public void getFakeDomainCheckShouldReturnFalseWhenSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("fakeDomainCheck", "false")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean fakeDomainCheck = testee.getFakeDomainCheck();
        assertThat(fakeDomainCheck).isFalse();
    }

    @Test
    public void getFakeDomainCheckShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean fakeDomainCheck = testee.getFakeDomainCheck();
        assertThat(fakeDomainCheck).isFalse();
    }

    @Test
    public void getInLineTypeShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("inline", "none")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        TypeCode inLineType = testee.getInLineType();
        assertThat(inLineType).isEqualTo(TypeCode.NONE);
    }

    @Test
    public void getInLineTypeShouldReturnNoneWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        TypeCode inLineType = testee.getInLineType();
        assertThat(inLineType).isEqualTo(TypeCode.UNALTERED);
    }

    @Test
    public void getAttachmentTypeShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("attachment", "unaltered")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        TypeCode attachmentType = testee.getAttachmentType();
        assertThat(attachmentType).isEqualTo(TypeCode.UNALTERED);
    }

    @Test
    public void getAttachmentTypeShouldReturnMessageWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        TypeCode attachmentType = testee.getAttachmentType();
        assertThat(attachmentType).isEqualTo(TypeCode.NONE);
    }

    @Test
    public void getMessageShouldReturnMessageValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("message", "my message")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        String message = testee.getMessage();
        assertThat(message).isEqualTo("my message");
    }

    @Test
    public void getMessageShouldReturnEmptyWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        String message = testee.getMessage();
        assertThat(message).isEqualTo("");
    }

    @Test
    public void getSubjectShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("subject", "my subject")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        String subject = testee.getSubject();
        assertThat(subject).isEqualTo("my subject");
    }

    @Test
    public void getSubjectShouldReturnNullWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        String subject = testee.getSubject();
        assertThat(subject).isNull();
    }

    @Test
    public void getSubjectPrefixShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("prefix", "my prefix")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        String prefix = testee.getSubjectPrefix();
        assertThat(prefix).isEqualTo("my prefix");
    }

    @Test
    public void getSubjectPrefixShouldReturnNullWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        String prefix = testee.getSubjectPrefix();
        assertThat(prefix).isNull();
    }

    @Test
    public void isAttachErrorShouldReturnTrueWhenSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("attachError", "true")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean attachError = testee.isAttachError();
        assertThat(attachError).isTrue();
    }

    @Test
    public void isAttachErrorShouldReturnFalseWhenSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("attachError", "false")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean attachError = testee.isAttachError();
        assertThat(attachError).isFalse();
    }

    @Test
    public void isAttachErrorShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean attachError = testee.isAttachError();
        assertThat(attachError).isFalse();
    }

    @Test
    public void isReplyShouldReturnTrueWhenSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("isReply", "true")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean isReply = testee.isReply();
        assertThat(isReply).isTrue();
    }

    @Test
    public void isReplyShouldReturnFalseWhenSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("isReply", "false")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean isReply = testee.isReply();
        assertThat(isReply).isFalse();
    }

    @Test
    public void isReplyShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean isReply = testee.isReply();
        assertThat(isReply).isFalse();
    }

    @Test
    public void getRecipientsShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("recipients", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> recipients = testee.getRecipients();
        assertThat(recipients).contains("user@james.org, user2@james.org");
    }

    @Test
    public void getRecipientsShouldReturnAbsentWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("recipients", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> recipients = testee.getRecipients();
        assertThat(recipients).isEmpty();
    }

    @Test
    public void getRecipientsShouldReturnAbsentWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> recipients = testee.getRecipients();
        assertThat(recipients).isEmpty();
    }

    @Test
    public void getToShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("to", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> to = testee.getTo();
        assertThat(to).contains("user@james.org, user2@james.org");
    }

    @Test
    public void getToShouldReturnAbsentWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("to", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> to = testee.getTo();
        assertThat(to).isEmpty();
    }

    @Test
    public void getToShouldReturnAbsentWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> to = testee.getTo();
        assertThat(to).isEmpty();
    }

    @Test
    public void getReversePathShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("reversePath", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> reversePath = testee.getReversePath();
        assertThat(reversePath).contains("user@james.org, user2@james.org");
    }

    @Test
    public void getReversePathShouldReturnAbsentWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("reversePath", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> reversePath = testee.getReversePath();
        assertThat(reversePath).isEmpty();
    }

    @Test
    public void getReversePathShouldReturnAbsentWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> reversePath = testee.getReversePath();
        assertThat(reversePath).isEmpty();
    }

    @Test
    public void getSenderShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("sender", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> sender = testee.getSender();
        assertThat(sender).contains("user@james.org, user2@james.org");
    }

    @Test
    public void getSenderShouldReturnAbsentWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("sender", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> sender = testee.getSender();
        assertThat(sender).isEmpty();
    }

    @Test
    public void getSenderShouldReturnAbsentWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> sender = testee.getSender();
        assertThat(sender).isEmpty();
    }

    @Test
    public void getReplyToShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("replyTo", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> replyTo = testee.getReplyTo();
        assertThat(replyTo).contains("user@james.org, user2@james.org");
    }

    @Test
    public void getReplyToShouldReturnreplytoValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("replyto", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> replyTo = testee.getReplyTo();
        assertThat(replyTo).contains("user@james.org, user2@james.org");
    }

    @Test
    public void getReplyToShouldReturnAbsentWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("replyTo", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> replyTo = testee.getReplyTo();
        assertThat(replyTo).isEmpty();
    }

    @Test
    public void getReplyToShouldReturnAbsentWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        Optional<String> replyTo = testee.getReplyTo();
        assertThat(replyTo).isEmpty();
    }

    @Test
    public void isDebugShouldReturnTrueWhenSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("debug", "true")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean debug = testee.isDebug();
        assertThat(debug).isTrue();
    }

    @Test
    public void isDebugShouldReturnFalseWhenSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("debug", "false")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean debug = testee.isDebug();
        assertThat(debug).isFalse();
    }

    @Test
    public void isDebugShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean debug = testee.isDebug();
        assertThat(debug).isFalse();
    }

    @Test
    public void isStaticShouldReturnTrueWhenSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("static", "true")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean isStatic = testee.isStatic();
        assertThat(isStatic).isTrue();
    }

    @Test
    public void isStaticShouldReturnFalseWhenSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("static", "false")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean isStatic = testee.isStatic();
        assertThat(isStatic).isFalse();
    }

    @Test
    public void isStaticShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = RedirectMailetInitParameters.from(mailet);

        boolean isStatic = testee.isStatic();
        assertThat(isStatic).isFalse();
    }

}
