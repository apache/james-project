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
import static org.apache.mailet.DsnParameters.NOTIFY_PARAMETER;
import static org.apache.mailet.DsnParameters.ORCPT_PARAMETER;

import java.util.Map;
import java.util.Set;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.DsnParameters.RecipientDsnParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class DSNRcptParameterHook implements RcptHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(DSNRcptParameterHook.class);

    public static class Builder {
        private final ImmutableMap.Builder<MailAddress, RecipientDsnParameters> entries;

        public Builder() {
            entries = ImmutableMap.builder();
        }

        public Builder add(MailAddress recipient, RecipientDsnParameters parameters) {
            entries.put(recipient, parameters);
            return this;
        }

        public ImmutableMap<MailAddress, RecipientDsnParameters> build() {
            return entries.build();
        }
    }

    public static final ProtocolSession.AttachmentKey<Builder> DSN_RCPT_PARAMETERS =
        ProtocolSession.AttachmentKey.of("DSN_RCPT_PARAMETERS", Builder.class);

    private static HookResult syntaxError(String description) {
        return HookResult.builder()
            .hookReturnCode(HookReturnCode.deny())
            .smtpReturnCode(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS)
            .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " " + description)
            .build();
    }

    @Override
    public Set<String> supportedParameters() {
        return ImmutableSet.of(ORCPT_PARAMETER, NOTIFY_PARAMETER);
    }

    @Override
    public HookResult doRcpt(SMTPSession session, MaybeSender sender, MailAddress rcpt, Map<String, String> parameters) {
        try {
            Builder builder = session.getAttachment(DSN_RCPT_PARAMETERS, Transaction)
                .orElse(new Builder());
            DsnParameters.RecipientDsnParameters.fromSMTPArgLine(parameters)
                .ifPresent(rcptParameters ->
                    session.setAttachment(DSN_RCPT_PARAMETERS, builder.add(rcpt, rcptParameters), Transaction));
            return HookResult.DECLINED;
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Invalid DSN RCPT parameters {} for <{}>", parameters, rcpt.asString(), e);
            return syntaxError("Invalid NOTIFY or ORCPT parameter value");
        }
    }
}
