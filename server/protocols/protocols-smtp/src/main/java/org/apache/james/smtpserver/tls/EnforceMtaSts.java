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

import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailHook;
import org.apache.james.util.MDCStructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CF https://datatracker.ietf.org/doc/html/rfc8461
 *
 * SMTP MTA Strict Transport Security (MTA-STS)
 *
 * Aimed at enforcing mode enforce
 */
public class EnforceMtaSts implements MailHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnforceMtaSts.class);

    @Override
    public HookResult doMail(SMTPSession session, MaybeSender sender) {
        if (!session.isTLSStarted()) {
            MDCStructuredLogger.forLogger(LOGGER)
                .field("sessionId", session.getSessionID())
                .field("sender", sender.asPrettyString())
                .log(logger -> logger.warn("Attempt to send to us a clear text message"));

            return HookResult.builder()
                .hookReturnCode(HookReturnCode.deny())
                .smtpReturnCode("571")
                .smtpDescription("StartTLS required")
                .build();
        }
        return HookResult.DECLINED;
    }
}
