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

package org.apache.james.protocols.smtp.core.fastfail;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This handler can be used to just ignore duplicated recipients. 
 */
public class SupressDuplicateRcptHandler implements RcptHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupressDuplicateRcptHandler.class);

    @Override
    public HookResult doRcpt(SMTPSession session, MaybeSender sender, MailAddress rcpt) {
        return session.getAttachment(SMTPSession.RCPT_LIST, State.Transaction)
            .filter(rcptList -> rcptList.contains(rcpt))
            .map(rcptList -> {
                StringBuilder responseBuffer = new StringBuilder();

                responseBuffer.append(DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.ADDRESS_VALID))
                              .append(" Recipient <")
                              .append(rcpt.toString())
                              .append("> OK");
                LOGGER.debug("Duplicate recipient not add to recipient list: {}", rcpt);
                return HookResult.builder()
                    .hookReturnCode(HookReturnCode.ok())
                    .smtpReturnCode(SMTPRetCode.MAIL_OK)
                    .smtpDescription(responseBuffer.toString())
                    .build();
            })
            .orElse(HookResult.DECLINED);
    }
}
