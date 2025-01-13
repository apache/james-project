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

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailParametersHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmtpRequireTlsParameterHook implements MailParametersHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpRequireTlsParameterHook.class);
    private static final String REQUIRETLS = "REQUIRETLS";
    public static final ProtocolSession.AttachmentKey<Boolean> REQUIRETLS_KEY =
        ProtocolSession.AttachmentKey.of(REQUIRETLS, Boolean.class);

    @Override
    public HookResult doMailParameter(SMTPSession session, String paramName, String paramValue) {
        if (session.getAttachment(REQUIRETLS_KEY, Transaction).isPresent()) {
            LOGGER.debug("The Mail parameter cannot contain more than one REQUIRETLS parameter at the same time");
            return HookResult.builder()
                .smtpReturnCode(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS)
                .hookReturnCode(HookReturnCode.deny())
                .smtpDescription("The Mail parameter cannot contain more than one REQUIRETLS parameter at the same time")
                .build();
        }
        if (paramName.equals(REQUIRETLS) && session.isStartTLSSupported() && session.isTLSStarted()) {
            session.setAttachment(REQUIRETLS_KEY, true, Transaction);
            String userName = Optional.ofNullable(session.getUsername()).map(Username::asString).orElse("unauthorized");
            LOGGER.info("SMTP sessionID: {}, User: {}, REQUIRETLS=true", session.getSessionID(), userName);
        }
        return HookResult.DECLINED;
    }

    @Override
    public String[] getMailParamNames() {
        return new String[]{REQUIRETLS};
    }
}