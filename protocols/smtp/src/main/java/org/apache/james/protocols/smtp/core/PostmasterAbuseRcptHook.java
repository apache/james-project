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
package org.apache.james.protocols.smtp.core;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.core.MailAddress;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler which whitelist "postmaster" and "abuse" recipients.
 */
public class PostmasterAbuseRcptHook implements RcptHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostmasterAbuseRcptHook.class);
    
    @Override
    public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
        if (rcpt.getLocalPart().equalsIgnoreCase("postmaster") || rcpt.getLocalPart().equalsIgnoreCase("abuse")) {
            LOGGER.debug("Sender allowed");
            return HookResult.OK;
        } else {
            return HookResult.DECLINED;
        }
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }
}
