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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.UnknownHook;

/**
 * {@link UnknownHook} implementation which disconnect the client after a issue to many unknown commands
 * 
 *
 */
public class MaxUnknownCmdHandler implements UnknownHook {

    public static final int DEFAULT_MAX_UNKOWN = 5;
    
    private static final String UNKOWN_COMMAND_COUNT = "UNKNOWN_COMMAND_COUNT";
    private int maxUnknown = DEFAULT_MAX_UNKOWN;

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    public void setMaxUnknownCmdCount(int maxUnknown) {
        this.maxUnknown = maxUnknown;
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.smtp.hook.UnknownHook#doUnknown(org.apache.james.protocols.smtp.SMTPSession, java.lang.String)
     */
    public HookResult doUnknown(SMTPSession session, String command) {
        Integer count = (Integer) session.getAttachment(UNKOWN_COMMAND_COUNT, State.Transaction);
        if (count == null) {
            count = 1;
        } else {
            count++;
        }
        session.setAttachment(UNKOWN_COMMAND_COUNT, count, State.Transaction);
        if (count > maxUnknown) {
            return new HookResult(HookReturnCode.DENY | HookReturnCode.DISCONNECT, "521", "Closing connection as too many unknown commands received");

        } else {
            
            return HookResult.declined();
        }
    }

    
}
