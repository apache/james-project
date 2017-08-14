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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.Hook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookResultHook;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * Log the {@link HookResult}. If {@link HookReturnCode#DENY}, {@link HookReturnCode#DENYSOFT} or {@link HookReturnCode#DISCONNECT} was used it will get 
 * logged to INFO. If not to DEBUG
 *
 */
public class HookResultLogger implements HookResultHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(HookResultLogger.class);

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    public HookResult onHookResult(SMTPSession session, HookResult hResult, long executionTime, Hook hook) {
        boolean match = false;
        boolean info = false;
        int result = hResult.getResult();
        StringBuilder sb = new StringBuilder();
        sb.append(hook.getClass().getName());
        sb.append(": result=");
        sb.append(result);
        sb.append(" (");
        if ((result & HookReturnCode.DECLINED) == HookReturnCode.DECLINED) {
            sb.append("DECLINED");
            match = true;
        }
        if ((result & HookReturnCode.OK) == HookReturnCode.OK) {
            sb.append("OK");
            match = true;
        }
        if ((result & HookReturnCode.DENY) == HookReturnCode.DENY) {
            sb.append("DENY");
            match = true;
            info = true;
        }
        if ((result & HookReturnCode.DENYSOFT) == HookReturnCode.DENYSOFT) {
            sb.append("DENYSOFT");
            match = true;
            info = true;
        }
        if ((result & HookReturnCode.DISCONNECT) == HookReturnCode.DISCONNECT) {
            if(match) {
                sb.append("|");
            }
            sb.append("DISCONNECT");
            info = true;
        }
        sb.append(")");

        if (info) {
            LOGGER.info(sb.toString());
        } else {
            LOGGER.debug(sb.toString());
        }
        return hResult;
    }

}
