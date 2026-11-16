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

package org.apache.james.protocols.smtp.core.esmtp;

import java.util.Collections;
import java.util.List;

import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailParametersHook;

/**
 * RFC 6531 SMTPUTF8 extension.
 *
 * Advertises the {@code SMTPUTF8} EHLO keyword and parses the {@code SMTPUTF8}
 * parameter on {@code MAIL FROM}. The parameter takes no value; its presence
 * on a transaction authorises the use of UTF-8 in the envelope addresses.
 *
 * Gating of UTF-8 addresses themselves lives in {@code MailCmdHandler} /
 * {@code RcptCmdHandler}, which reject non-ASCII addresses with 553 5.6.7
 * when {@link SMTPSession#SMTPUTF8_REQUESTED} is not set.
 */
public class SMTPUTF8Extension implements MailParametersHook, EhloExtension {

    private static final String[] MAIL_PARAMS = { "SMTPUTF8" };
    private static final List<String> FEATURES = Collections.singletonList("SMTPUTF8");
    // RFC 6531 section 3.4: the SMTPUTF8 parameter takes no value
    private static final HookResult VALUE_NOT_ALLOWED = HookResult.builder()
        .hookReturnCode(HookReturnCode.deny())
        .smtpReturnCode(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS)
        .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " SMTPUTF8 parameter takes no value")
        .build();

    @Override
    public HookResult doMailParameter(SMTPSession session, String paramName, String paramValue) {
        if (!paramValue.isEmpty()) {
            return VALUE_NOT_ALLOWED;
        }
        session.setAttachment(SMTPSession.SMTPUTF8_REQUESTED, Boolean.TRUE, State.Transaction);
        return null;
    }

    @Override
    public String[] getMailParamNames() {
        return MAIL_PARAMS;
    }

    @Override
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        return FEATURES;
    }
}
