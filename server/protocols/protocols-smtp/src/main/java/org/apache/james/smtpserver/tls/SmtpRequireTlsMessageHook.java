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

package org.apache.james.smtpserver.tls;

import static org.apache.james.protocols.api.ProtocolSession.State.Transaction;

import java.util.Arrays;

import jakarta.mail.MessagingException;

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.smtpserver.JamesMessageHook;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmtpRequireTlsMessageHook implements JamesMessageHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpRequireTlsMessageHook.class);
    public static final String REQUIRETLS = "REQUIRETLS";
    public static final String TLS_REQUIRED = "TLS-Required";

    public static final ProtocolSession.AttachmentKey<Boolean> REQUIRETLS_KEY =
        ProtocolSession.AttachmentKey.of(REQUIRETLS, Boolean.class);

    @Override
    public HookResult onMessage(SMTPSession session, Mail mail) {
        try {
            session.getAttachment(REQUIRETLS_KEY, Transaction).ifPresent(requireTsl ->
                mail.setAttribute(new Attribute(AttributeName.of(REQUIRETLS), AttributeValue.of(true))));
            if (isTlsNotRequired(mail) && !isRequireTlsAttributeContains(mail)) {
                mail.setAttribute(new Attribute(AttributeName.of(REQUIRETLS), AttributeValue.of(false)));
            }
        } catch (MessagingException e) {
            LOGGER.debug("Incorrect syntax when handling TLS-Required header field", e);
            return HookResult.builder()
                .smtpReturnCode(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS)
                .hookReturnCode(HookReturnCode.deny())
                .smtpDescription("Incorrect syntax when handling TLS-Required header field")
                .build();
        }
        return HookResult.DECLINED;
    }

    private static boolean isRequireTlsAttributeContains(Mail mail) {
        return mail.attributeNames().map(AttributeName::asString)
            .anyMatch(attributeName -> attributeName.equals(REQUIRETLS));
    }

    private static boolean isTlsNotRequired(Mail mail) throws MessagingException {
        String[] headers = mail.getMessage().getHeader(TLS_REQUIRED);
        return headers != null && headers.length == 1 && (Arrays.asList(headers).contains("No"));
    }
}
