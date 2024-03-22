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

package org.apache.james.lmtpserver;

import java.util.Collection;
import java.util.Objects;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.lmtp.LMTPMultiResponse;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.server.core.MimeMessageInputStreamSource;
import org.apache.james.smtpserver.DataLineJamesMessageHookHandler;
import org.apache.mailet.Mail;

import com.google.common.collect.ImmutableList;

public class MailetContainerHandler extends DataLineJamesMessageHookHandler {
    private static class Configuration {
        static Configuration DEFAULT = new Configuration(false);

        static Configuration parse(org.apache.commons.configuration2.Configuration config) {
            return new Configuration(config.getBoolean("splitExecution", false));
        }

        private final boolean splitExecution;

        private Configuration(boolean splitExecution) {
            this.splitExecution = splitExecution;
        }

        public boolean splitExecutionEnabled() {
            return splitExecution;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Configuration) {
                Configuration other = (Configuration) o;
                return Objects.equals(this.splitExecution, other.splitExecution);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(splitExecution);
        }
    }
    
    private final MailProcessor mailProcessor;
    private Configuration configuration;

    @Inject
    public MailetContainerHandler(MailProcessor mailProcessor) {
        this.mailProcessor = mailProcessor;
        this.configuration = Configuration.DEFAULT;
    }

    @Override
    public void init(org.apache.commons.configuration2.Configuration config) throws ConfigurationException {
        configuration = Configuration.parse(config);
    }

    @Override
    protected Response processExtensions(SMTPSession session, Mail mail, MimeMessageInputStreamSource mmiss) {
        Collection<MailAddress> recipients = ImmutableList.copyOf(mail.getRecipients());
        executeJamesMessageHooks(session, mail);

        if (recipients.isEmpty()) {
            // Return 503 see https://datatracker.ietf.org/doc/html/rfc2033#section-4.2
            AbstractHookableCmdHandler.calcDefaultSMTPResponse(HookResult.builder()
                .hookReturnCode(HookReturnCode.ok())
                .smtpReturnCode(SMTPRetCode.BAD_SEQUENCE)
                .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.CONTENT_OTHER) + " No recipients")
                .build());
        }

        if (configuration.splitExecutionEnabled()) {
            return executeEachRecipientSeparately(mail, recipients);
        } else {
            return executeAllRecipientsAtOnce(mail, recipients);
        }
    }

    private LMTPMultiResponse executeAllRecipientsAtOnce(Mail mail, Collection<MailAddress> recipients) {
        try {
            mailProcessor.service(mail);

            return LMTPMultiResponse.of(
                recipients.stream()
                    .map(recipient -> AbstractHookableCmdHandler.calcDefaultSMTPResponse(HookResult.builder()
                        .hookReturnCode(HookReturnCode.ok())
                        .smtpReturnCode(SMTPRetCode.MAIL_OK)
                        .smtpDescription(DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.CONTENT_OTHER) + " Message received <" + recipient.asString() + ">")
                        .build()))
                    .collect(ImmutableList.toImmutableList()));

        } catch (Exception e) {
            return LMTPMultiResponse.of(
                recipients.stream()
                    .map(recipient -> new SMTPResponse(SMTPRetCode.LOCAL_ERROR, DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.UNDEFINED_STATUS) + " Temporary error deliver message <" + recipient.asString() + ">"))
                    .collect(ImmutableList.toImmutableList()));
        }
    }

    private LMTPMultiResponse executeEachRecipientSeparately(Mail mail, Collection<MailAddress> recipients) {
        return LMTPMultiResponse.of(
            recipients.stream()
                .map(recipient -> executeFor(mail, recipient))
                .collect(ImmutableList.toImmutableList()));
    }

    private SMTPResponse executeFor(Mail mail, MailAddress recipient) {
        Mail newMail = null;
        try {
            newMail = mail.duplicate();
            newMail.setRecipients(ImmutableList.of(recipient));

            mailProcessor.service(newMail);

            return AbstractHookableCmdHandler.calcDefaultSMTPResponse(HookResult.builder()
                .hookReturnCode(HookReturnCode.ok())
                .smtpReturnCode(SMTPRetCode.MAIL_OK)
                .smtpDescription(DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.CONTENT_OTHER) + " Message received <" + recipient.asString() + ">")
                .build());
        } catch (Exception e) {
            return new SMTPResponse(SMTPRetCode.LOCAL_ERROR, DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.UNDEFINED_STATUS) + " Temporary error deliver message <" + recipient.asString() + ">");
        } finally {
            LifecycleUtil.dispose(newMail);
        }
    }
}
