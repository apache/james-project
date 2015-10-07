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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;

/**
  * Command handler for handling VRFY command
  */
public class VrfyCmdHandler implements CommandHandler<SMTPSession> {
    
    private static final Collection<String> COMMANDS = Collections.unmodifiableCollection(Arrays.asList("VRFY"));
    private static final Response NOT_SUPPORTED = new SMTPResponse(SMTPRetCode.UNIMPLEMENTED_COMMAND, 
            DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.SYSTEM_NOT_CAPABLE)+" VRFY is not supported").immutable();

    /**
     * Handler method called upon receipt of a VRFY command.
     * This method informs the client that the command is
     * not implemented.
     *
     */
    public Response onCommand(SMTPSession session, Request request) {
        return NOT_SUPPORTED;
    }
    
    /**
     * @see org.apache.james.protocols.api.handler.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
    	return COMMANDS;
    }

}
