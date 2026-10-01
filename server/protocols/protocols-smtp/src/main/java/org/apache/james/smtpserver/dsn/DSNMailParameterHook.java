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

package org.apache.james.smtpserver.dsn;

import static org.apache.james.protocols.api.ProtocolSession.State.Transaction;
import static org.apache.mailet.DsnParameters.ENVID_PARAMETER;
import static org.apache.mailet.DsnParameters.RET_PARAMETER;

import java.util.Optional;

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailParametersHook;
import org.apache.mailet.DsnParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DSNMailParameterHook implements MailParametersHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(DSNMailParameterHook.class);

    public static final ProtocolSession.AttachmentKey<DsnParameters.Ret> DSN_RET = ProtocolSession.AttachmentKey.of("DSN_RET", DsnParameters.Ret.class);
    public static final ProtocolSession.AttachmentKey<DsnParameters.EnvId> DSN_ENVID = ProtocolSession.AttachmentKey.of("DSN_ENVID", DsnParameters.EnvId.class);

    private static HookResult syntaxError(String description) {
        return HookResult.builder()
            .hookReturnCode(HookReturnCode.deny())
            .smtpReturnCode(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS)
            .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " " + description)
            .build();
    }

    @Override
    public HookResult doMailParameter(SMTPSession session, String paramName, String paramValue) {
        if (paramName.equals(RET_PARAMETER)) {
            DsnParameters.Ret.parse(paramValue)
                .or(() -> {
                    LOGGER.debug("Invalid DSN RET value: {}", paramValue);
                    return Optional.empty();
                })
                .ifPresent(ret -> session.setAttachment(DSN_RET, ret, Transaction));
        }
        if (paramName.equals(ENVID_PARAMETER)) {
            try {
                session.setAttachment(DSN_ENVID, DsnParameters.EnvId.of(paramValue), Transaction);
            } catch (IllegalArgumentException e) {
                LOGGER.debug("Invalid DSN ENVID value: {}", paramValue, e);
                return syntaxError("Invalid ENVID parameter value");
            }
        }
        return HookResult.DECLINED;
    }

    @Override
    public String[] getMailParamNames() {
        return new String[] {RET_PARAMETER, ENVID_PARAMETER};
    }
}
