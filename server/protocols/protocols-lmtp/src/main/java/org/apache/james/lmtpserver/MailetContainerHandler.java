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

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.smtpserver.DataLineJamesMessageHookHandler;
import org.apache.mailet.Mail;

public class MailetContainerHandler extends DataLineJamesMessageHookHandler {
    private final MailProcessor mailProcessor;

    @Inject
    public MailetContainerHandler(MailProcessor mailProcessor) {
        this.mailProcessor = mailProcessor;
    }

    @Override
    protected Response processExtensions(SMTPSession session, Mail mail) {
        try {
            mailProcessor.service(mail);

            return AbstractHookableCmdHandler.calcDefaultSMTPResponse(HookResult.builder()
                .hookReturnCode(HookReturnCode.ok())
                .smtpReturnCode(SMTPRetCode.MAIL_OK)
                .smtpDescription(DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.CONTENT_OTHER) + " Message received")
                .build());
        } catch (MessagingException e) {
            return new SMTPResponse(SMTPRetCode.LOCAL_ERROR, DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.UNDEFINED_STATUS) + "Temporary error deliver message");
        }
    }
}
