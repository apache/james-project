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

import static org.apache.james.smtpserver.dsn.DSNMailParameterHook.DSN_ENVID;
import static org.apache.james.smtpserver.dsn.DSNMailParameterHook.DSN_RET;
import static org.apache.james.smtpserver.dsn.DSNRcptParameterHook.DSN_RCPT_PARAMETERS;

import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.smtpserver.JamesMessageHook;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.Mail;

import com.google.common.collect.ImmutableMap;

public class DSNMessageHook implements JamesMessageHook {
    @Override
    public HookResult onMessage(SMTPSession session, Mail mail) {
        Optional<DsnParameters.Ret> ret = session.getAttachment(DSN_RET, ProtocolSession.State.Transaction);
        Optional<DsnParameters.EnvId> envId = session.getAttachment(DSN_ENVID, ProtocolSession.State.Transaction);
        ImmutableMap<MailAddress, DsnParameters.RecipientDsnParameters> rcptParameters =
            session.getAttachment(DSN_RCPT_PARAMETERS, ProtocolSession.State.Transaction)
                .map(DSNRcptParameterHook.Builder::build)
                .orElse(ImmutableMap.of());

        DsnParameters.of(envId, ret, rcptParameters)
            .ifPresent(mail::setDsnParameters);
        return HookResult.DECLINED;
    }
}
