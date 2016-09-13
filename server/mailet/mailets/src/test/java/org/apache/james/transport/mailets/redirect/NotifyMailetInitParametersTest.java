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

import java.util.Properties;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.test.FakeMailContext;
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
        Properties properties = new Properties();
        properties.put("passThrough", "true");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean passThrough = testee.getPassThrough();
        assertThat(passThrough).isTrue();
    }

    @Test
    public void getPassThroughShouldReturnFalseWhenSetToFalse() throws Exception {
        Properties properties = new Properties();
        properties.put("passThrough", "false");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean passThrough = testee.getPassThrough();
        assertThat(passThrough).isFalse();
    }

    @Test
    public void getPassThroughShouldReturnTrueWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean passThrough = testee.getPassThrough();
        assertThat(passThrough).isTrue();
    }

    @Test
    public void getFakeDomainCheckShouldReturnTrueWhenSetToTrue() throws Exception {
        Properties properties = new Properties();
        properties.put("fakeDomainCheck", "true");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean fakeDomainCheck = testee.getFakeDomainCheck();
        assertThat(fakeDomainCheck).isTrue();
    }

    @Test
    public void getFakeDomainCheckShouldReturnFalseWhenSetToFalse() throws Exception {
        Properties properties = new Properties();
        properties.put("fakeDomainCheck", "false");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean fakeDomainCheck = testee.getFakeDomainCheck();
        assertThat(fakeDomainCheck).isFalse();
    }

    @Test
    public void getFakeDomainCheckShouldReturnFalseWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean fakeDomainCheck = testee.getFakeDomainCheck();
        assertThat(fakeDomainCheck).isFalse();
    }

    @Test
    public void getInLineTypeShouldReturnValueWhenSet() throws Exception {
        Properties properties = new Properties();
        properties.put("inline", "unaltered");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        TypeCode inLineType = testee.getInLineType();
        assertThat(inLineType).isEqualTo(TypeCode.UNALTERED);
    }

    @Test
    public void getInLineTypeShouldReturnNoneWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        TypeCode inLineType = testee.getInLineType();
        assertThat(inLineType).isEqualTo(TypeCode.NONE);
    }

    @Test
    public void getAttachmentTypeShouldReturnValueWhenSet() throws Exception {
        Properties properties = new Properties();
        properties.put("attachment", "unaltered");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        TypeCode attachmentType = testee.getAttachmentType();
        assertThat(attachmentType).isEqualTo(TypeCode.UNALTERED);
    }

    @Test
    public void getAttachmentTypeShouldReturnMessageWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        TypeCode attachmentType = testee.getAttachmentType();
        assertThat(attachmentType).isEqualTo(TypeCode.MESSAGE);
    }

    @Test
    public void getMessageShouldReturnNoticeValueWhenSet() throws Exception {
        Properties properties = new Properties();
        properties.put("notice", "my notice");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String message = testee.getMessage();
        assertThat(message).isEqualTo("my notice");
    }

    @Test
    public void getMessageShouldReturnMessageValueWhenSet() throws Exception {
        Properties properties = new Properties();
        properties.put("message", "my message");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String message = testee.getMessage();
        assertThat(message).isEqualTo("my message");
    }

    @Test
    public void getMessageShouldReturnDefaultMessageWhenNoticeAndMessageNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String message = testee.getMessage();
        assertThat(message).isEqualTo("We were unable to deliver the attached message because of an error in the mail server.");
    }

    @Test
    public void getSubjectShouldReturnNull() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String subject = testee.getSubject();
        assertThat(subject).isNull();
    }

    @Test
    public void getSubjectPrefixShouldReturnValueWhenSet() throws Exception {
        Properties properties = new Properties();
        properties.put("prefix", "my prefix");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String prefix = testee.getSubjectPrefix();
        assertThat(prefix).isEqualTo("my prefix");
    }

    @Test
    public void getSubjectPrefixShouldReturnDefaultValueWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String prefix = testee.getSubjectPrefix();
        assertThat(prefix).isEqualTo("Re:");
    }

    @Test
    public void isAttachErrorShouldReturnTrueWhenSetToTrue() throws Exception {
        Properties properties = new Properties();
        properties.put("attachError", "true");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean attachError = testee.isAttachError();
        assertThat(attachError).isTrue();
    }

    @Test
    public void isAttachErrorShouldReturnFalseWhenSetToFalse() throws Exception {
        Properties properties = new Properties();
        properties.put("attachError", "false");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean attachError = testee.isAttachError();
        assertThat(attachError).isFalse();
    }

    @Test
    public void isAttachErrorShouldReturnFalseWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean attachError = testee.isAttachError();
        assertThat(attachError).isFalse();
    }

    @Test
    public void isReplyShouldReturnTrue() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean isReply = testee.isReply();
        assertThat(isReply).isTrue();
    }

    @Test
    public void getRecipientsShouldReturnValueWhenSet() throws Exception {
        Properties properties = new Properties();
        properties.put("recipients", "user@james.org, user2@james.org");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String recipients = testee.getRecipients();
        assertThat(recipients).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getRecipientsShouldReturnNullWhenEmpty() throws Exception {
        Properties properties = new Properties();
        properties.put("recipients", "");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String recipients = testee.getRecipients();
        assertThat(recipients).isNull();
    }

    @Test
    public void getRecipientsShouldReturnNullWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String recipients = testee.getRecipients();
        assertThat(recipients).isNull();
    }

    @Test
    public void getToShouldReturnValueWhenSet() throws Exception {
        Properties properties = new Properties();
        properties.put("to", "user@james.org, user2@james.org");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String to = testee.getTo();
        assertThat(to).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getToShouldReturnNullWhenEmpty() throws Exception {
        Properties properties = new Properties();
        properties.put("to", "");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String to = testee.getTo();
        assertThat(to).isNull();
    }

    @Test
    public void getToShouldReturnNullWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String to = testee.getTo();
        assertThat(to).isNull();
    }

    @Test
    public void getReversePathShouldReturnValueWhenSet() throws Exception {
        Properties properties = new Properties();
        properties.put("reversePath", "user@james.org, user2@james.org");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String reversePath = testee.getReversePath();
        assertThat(reversePath).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getReversePathShouldReturnNullWhenEmpty() throws Exception {
        Properties properties = new Properties();
        properties.put("reversePath", "");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String reversePath = testee.getReversePath();
        assertThat(reversePath).isNull();
    }

    @Test
    public void getReversePathShouldReturnNullWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String reversePath = testee.getReversePath();
        assertThat(reversePath).isNull();
    }

    @Test
    public void getSenderShouldReturnValueWhenSet() throws Exception {
        Properties properties = new Properties();
        properties.put("sender", "user@james.org, user2@james.org");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String sender = testee.getSender();
        assertThat(sender).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getSenderShouldReturnNullWhenEmpty() throws Exception {
        Properties properties = new Properties();
        properties.put("sender", "");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String sender = testee.getSender();
        assertThat(sender).isNull();
    }

    @Test
    public void getSenderShouldReturnNullWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String sender = testee.getSender();
        assertThat(sender).isNull();
    }

    @Test
    public void getReplyToShouldReturnValueWhenSet() throws Exception {
        Properties properties = new Properties();
        properties.put("replyTo", "user@james.org, user2@james.org");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String replyTo = testee.getReplyTo();
        assertThat(replyTo).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getReplyToShouldReturnreplytoValueWhenSet() throws Exception {
        Properties properties = new Properties();
        properties.put("replyto", "user@james.org, user2@james.org");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String replyTo = testee.getReplyTo();
        assertThat(replyTo).isEqualTo("user@james.org, user2@james.org");
    }

    @Test
    public void getReplyToShouldReturnNullWhenEmpty() throws Exception {
        Properties properties = new Properties();
        properties.put("replyTo", "");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String replyTo = testee.getReplyTo();
        assertThat(replyTo).isNull();
    }

    @Test
    public void getReplyToShouldReturnNullWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        String replyTo = testee.getReplyTo();
        assertThat(replyTo).isNull();
    }

    @Test
    public void isDebugShouldReturnTrueWhenSetToTrue() throws Exception {
        Properties properties = new Properties();
        properties.put("debug", "true");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean debug = testee.isDebug();
        assertThat(debug).isTrue();
    }

    @Test
    public void isDebugShouldReturnFalseWhenSetToFalse() throws Exception {
        Properties properties = new Properties();
        properties.put("debug", "false");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean debug = testee.isDebug();
        assertThat(debug).isFalse();
    }

    @Test
    public void isDebugShouldReturnFalseWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean debug = testee.isDebug();
        assertThat(debug).isFalse();
    }

    @Test
    public void isStaticShouldReturnTrueWhenSetToTrue() throws Exception {
        Properties properties = new Properties();
        properties.put("static", "true");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean isStatic = testee.isStatic();
        assertThat(isStatic).isTrue();
    }

    @Test
    public void isStaticShouldReturnFalseWhenSetToFalse() throws Exception {
        Properties properties = new Properties();
        properties.put("static", "false");
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean isStatic = testee.isStatic();
        assertThat(isStatic).isFalse();
    }

    @Test
    public void isStaticShouldReturnFalseWhenNotSet() throws Exception {
        Properties properties = new Properties();
        FakeMailetConfig mailetConfig = new FakeMailetConfig("mailet", FakeMailContext.defaultContext(), properties);
        mailet.init(mailetConfig);
        InitParameters testee = NotifyMailetInitParameters.from(mailet);

        boolean isStatic = testee.isStatic();
        assertThat(isStatic).isFalse();
    }
}
