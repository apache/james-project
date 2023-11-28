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

import java.util.Optional;
import java.util.Set;

import org.apache.james.protocols.api.OidcSASLConfiguration;
import org.apache.james.protocols.api.ProtocolConfiguration;

import com.google.common.collect.ImmutableSet;


/**
 * Provides a number of server-wide constant values to the
 * SMTPHandlers
 *
 */
public interface SMTPConfiguration extends ProtocolConfiguration {

    /**
     * Returns the service wide maximum message size in bytes.
     *
     * @return the maximum message size
     */
    long getMaxMessageSize();

    /**
     * Returns whether relaying is allowed for the IP address passed.
     *
     * @param remoteIP the remote IP address in String form
     * @return whether relaying is allowed
     */
    boolean isRelayingAllowed(String remoteIP);

    /**
     * Returns whether SMTP AUTH is active for this server, and
     * necessary for the IP address passed.
     *
     * @param remoteIP the remote IP address in String form
     * @return whether SMTP authentication is on
     */
    boolean isAuthAnnounced(String remoteIP, boolean tlsStarted);
    
    /**
     * Returns whether the remote server needs to send a HELO/EHLO
     * of its senders.
     *
     * @return whether SMTP authentication is on
     */
    boolean useHeloEhloEnforcement();
    
    /**
     * Return wheter the mailserver will accept addresses without brackets enclosed.
     * 
     * @return true or false
     */
    boolean useAddressBracketsEnforcement();

    boolean isPlainAuthEnabled();

    Optional<OidcSASLConfiguration> saslConfiguration();

    default Set<String> disabledFeatures() {
        return ImmutableSet.of();
    }

}
