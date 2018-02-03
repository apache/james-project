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
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ConnectHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;

/**
 * This ConnectHandler print the greeting on connecting
 */
public class WelcomeMessageHandler implements ConnectHandler<SMTPSession> {

    private static final String SERVICE_TYPE = "SMTP";
    
    /**
     * @see org.apache.james.protocols.api.handler.ConnectHandler#onConnect(org.apache.james.protocols.api.ProtocolSession)
     */
    public Response onConnect(SMTPSession session) {
        String smtpGreeting = session.getConfiguration().getGreeting();

        SMTPResponse welcomeResponse;
        // if no greeting was configured use a default
        if (smtpGreeting == null) {
            // Initially greet the connector
            welcomeResponse = new SMTPResponse(SMTPRetCode.SERVICE_READY,
                          new StringBuilder(256)
                          .append(session.getConfiguration().getHelloName())
                          .append(" ").append(getServiceType(session)).append(" Server (")
                          .append(session.getConfiguration().getSoftwareName())
                          .append(") ready"));
        } else {
            welcomeResponse = new SMTPResponse(SMTPRetCode.SERVICE_READY,smtpGreeting);
        }
        return welcomeResponse;
    }

    protected String getServiceType(SMTPSession session) {
        return SERVICE_TYPE;
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }
}
