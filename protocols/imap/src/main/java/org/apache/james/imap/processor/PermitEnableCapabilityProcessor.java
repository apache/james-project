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
package org.apache.james.imap.processor;

import java.util.List;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.process.ImapSession;

/**
 * {@link CapabilityImplementingProcessor} which allows to ENABLE one ore more Capabilities
 */
public interface PermitEnableCapabilityProcessor extends CapabilityImplementingProcessor {

    /**
     * Return the capabilities which supports to get ENABLED.
     * 
     * Be sure that these are also returned by {@link #getImplementedCapabilities(ImapSession)}
     */
    List<Capability> getPermitEnableCapabilities(ImapSession session);
    
    /**
     * Callback which is used when a ENABLED command was used to enable on of the CAPABILITIES which is managed by this implementation
     */
    void enable(ImapMessage message, Responder responder, ImapSession session, Capability capability) throws EnableException;

    /**
     * Exception which should get thrown if for whatever reason its not possible to enable a capability
     */
    final class EnableException extends Exception {
        private static final long serialVersionUID = -4456052968041000753L;

        public EnableException(String msg, Throwable e) {
            super(msg, e);
        }
        
        public EnableException(Throwable e) {
            super(e);
        }
    }
}
