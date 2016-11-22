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

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

public class NotifyMailetInitParametersTest {

    private GenericMailet mailet;

    @Before
    public void setup() {
        mailet = new GenericMailet() {
            
            @Override
            public void service(Mail mail) throws MessagingException {
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
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

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
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean passThrough = testee.getPassThrough();
        assertThat(passThrough).isFalse();
    }

    @Test
    public void getPassThroughShouldReturnTrueWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean passThrough = testee.getPassThrough();
        assertThat(passThrough).isTrue();
    }

    @Test
    public void getFakeDomainCheckShouldReturnTrueWhenSetToTrue() throws Exception {
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
    public void getFakeDomainCheckShouldReturnFalseWhenSetToFalse() throws Exception {
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
    public void getFakeDomainCheckShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean fakeDomainCheck = testee.getFakeDomainCheck();
        assertThat(fakeDomainCheck).isFalse();
    }

    @Test
    public void getInLineTypeShouldReturnValueWhenSet() throws Exception {
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
    public void getInLineTypeShouldReturnNoneWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        TypeCode inLineType = testee.getInLineType();
        assertThat(inLineType).isEqualTo(TypeCode.NONE);
    }

    @Test
    public void getAttachmentTypeShouldReturnValueWhenSet() throws Exception {
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
    public void getAttachmentTypeShouldReturnMessageWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        TypeCode attachmentType = testee.getAttachmentType();
        assertThat(attachmentType).isEqualTo(TypeCode.MESSAGE);
    }

    @Test
    public void getMessageShouldReturnNoticeValueWhenSet() throws Exception {
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
    public void getMessageShouldReturnMessageValueWhenSet() throws Exception {
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
    public void getMessageShouldReturnDefaultMessageWhenNoticeAndMessageNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String message = testee.getMessage();
        assertThat(message).isEqualTo("We were unable to deliver the attached message because of an error in the mail server.");
    }

    @Test
    public void getSubjectShouldReturnNull() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

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
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String prefix = testee.getSubjectPrefix();
        assertThat(prefix).isEqualTo("my prefix");
    }

    @Test
    public void getSubjectPrefixShouldReturnDefaultValueWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String prefix = testee.getSubjectPrefix();
        assertThat(prefix).isEqualTo("Re:");
    }

    @Test
    public void isAttachErrorShouldReturnTrueWhenSetToTrue() throws Exception {
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
    public void isAttachErrorShouldReturnFalseWhenSetToFalse() throws Exception {
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
    public void isAttachErrorShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean attachError = testee.isAttachError();
        assertThat(attachError).isFalse();
    }

    @Test
    public void isReplyShouldReturnTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean isReply = testee.isReply();
        assertThat(isReply).isTrue();
    }

    @Test
    public void getRecipientsShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("recipients", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String recipients = testee.getRecipients();
        assertThat(recipients).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getRecipientsShouldReturnNullWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("recipients", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String recipients = testee.getRecipients();
        assertThat(recipients).isNull();
    }

    @Test
    public void getRecipientsShouldReturnNullWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String recipients = testee.getRecipients();
        assertThat(recipients).isNull();
    }

    @Test
    public void getToShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("to", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String to = testee.getTo();
        assertThat(to).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getToShouldReturnNullWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("to", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String to = testee.getTo();
        assertThat(to).isNull();
    }

    @Test
    public void getToShouldReturnNullWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String to = testee.getTo();
        assertThat(to).isNull();
    }

    @Test
    public void getReversePathShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("reversePath", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String reversePath = testee.getReversePath();
        assertThat(reversePath).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getReversePathShouldReturnNullWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("reversePath", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String reversePath = testee.getReversePath();
        assertThat(reversePath).isNull();
    }

    @Test
    public void getReversePathShouldReturnNullWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String reversePath = testee.getReversePath();
        assertThat(reversePath).isNull();
    }

    @Test
    public void getSenderShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("sender", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String sender = testee.getSender();
        assertThat(sender).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getSenderShouldReturnNullWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("sender", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String sender = testee.getSender();
        assertThat(sender).isNull();
    }

    @Test
    public void getSenderShouldReturnNullWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String sender = testee.getSender();
        assertThat(sender).isNull();
    }

    @Test
    public void getReplyToShouldReturnValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("replyTo", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String replyTo = testee.getReplyTo();
        assertThat(replyTo).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getReplyToShouldReturnreplytoValueWhenSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("replyto", "user@james.org, user2@james.org")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String replyTo = testee.getReplyTo();
        assertThat(replyTo).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getReplyToShouldReturnNullWhenEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .setProperty("replyTo", "")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String replyTo = testee.getReplyTo();
        assertThat(replyTo).isNull();
    }

    @Test
    public void getReplyToShouldReturnNullWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String replyTo = testee.getReplyTo();
        assertThat(replyTo).isNull();
    }

    @Test
    public void isDebugShouldReturnTrueWhenSetToTrue() throws Exception {
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
    public void isDebugShouldReturnFalseWhenSetToFalse() throws Exception {
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
    public void isDebugShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

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
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

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
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean isStatic = testee.isStatic();
        assertThat(isStatic).isFalse();
    }

    @Test
    public void isStaticShouldReturnFalseWhenNotSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("mailet")
                .build();
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean isStatic = testee.isStatic();
        assertThat(isStatic).isFalse();
    }
}
