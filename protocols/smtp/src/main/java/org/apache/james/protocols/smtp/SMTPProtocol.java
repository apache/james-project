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

package org.apache.james.protocols.smtp;

import org.apache.james.protocols.api.ProtocolImpl;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.api.handler.ProtocolHandlerChain;
import org.slf4j.Logger;

/**
 * {@link ProtocolImpl} which creates {@link SMTPSessionImpl} instances
 * 
 *
 */
public class SMTPProtocol extends ProtocolImpl {

    public SMTPProtocol(ProtocolHandlerChain chain, SMTPConfiguration config, Logger logger) {
        super(chain, config, logger);
    }

    @Override
    public ProtocolSession newSession(ProtocolTransport transport) {
        return new SMTPSessionImpl(logger, transport, (SMTPConfiguration) getConfiguration());
    }

}
