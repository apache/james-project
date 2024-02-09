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
package org.apache.james.protocols.smtp.core.log;

import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.Hook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookResultHook;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log the {@link HookResult}. If {@link HookReturnCode.Action#DENY}, {@link HookReturnCode.Action#DENYSOFT} or {@link HookReturnCode#isDisconnected()} was used it will get
 * logged to INFO. If not to DEBUG
 */
public class HookResultLogger implements HookResultHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(HookResultLogger.class);

    @Override
    public HookResult onHookResult(SMTPSession session, HookResult hResult, long executionTime, Hook hook) {
        HookReturnCode result = hResult.getResult();

        boolean requiresInfoLogging = result.getAction() == HookReturnCode.Action.DENY
            || result.getAction() == HookReturnCode.Action.DENYSOFT
            || result.isDisconnected();

        if (requiresInfoLogging) {
            LOGGER.info("{}: result= ({} {}) {} {}", hook.getClass().getName(),
                result.getAction(),
                result.getConnectionStatus(),
                hResult.getSmtpRetCode(),
                hResult.getSmtpDescription());
        } else {
            LOGGER.debug("{}: result= ({} {})", hook.getClass().getName(),
                result.getAction(),
                result.getConnectionStatus());
        }
        return hResult;
    }

}
