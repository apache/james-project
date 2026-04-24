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

import java.util.List;
import java.util.Set;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.api.ProtocolSession;

import com.google.common.collect.ImmutableSet;

/**
 * All the handlers access this interface to communicate with
 * SMTPHandler object
 */

public interface SMTPSession extends ProtocolSession {

    // Keys used to store/lookup data in the internal state hash map
    /** Sender's email address */
    AttachmentKey<MaybeSender> SENDER = AttachmentKey.of("SENDER_ADDRESS", MaybeSender.class);
    /** The message recipients */
    @SuppressWarnings("unchecked")
    AttachmentKey<List<MailAddress>> RCPT_LIST = AttachmentKey.of("RCPT_LIST", (Class<List<MailAddress>>) (Object) List.class);
    /** HELO or EHLO */
    AttachmentKey<String> CURRENT_HELO_MODE = AttachmentKey.of("CURRENT_HELO_MODE", String.class);
    AttachmentKey<String> CURRENT_HELO_NAME = AttachmentKey.of("CURRENT_HELO_NAME", String.class);
    /** Set when the authenticated account was granted the right to use identities other than its own */
    AttachmentKey<Boolean> ALLOW_USE_OTHER_IDENTITY = AttachmentKey.of("ALLOW_USE_OTHER_IDENTITY", Boolean.class);

    /**
     * Whether this session is allowed to use MAIL FROM / From identities other than the one it authenticated with.
     *
     * Set upon authentication for accounts explicitly granted that right, this bypasses the identity checks
     * performed by the sender identity verification hook (see the <code>verifyIdentity</code> setting).
     *
     * @return true if identity verification is to be bypassed for this session
     */
    default boolean allowUseOtherIdentity() {
        return getAttachment(ALLOW_USE_OTHER_IDENTITY, State.Connection).orElse(false);
    }

    /** Set per-transaction when the client asserted the RFC 6531 SMTPUTF8 parameter on MAIL FROM. */
    AttachmentKey<Boolean> SMTPUTF8_REQUESTED = AttachmentKey.of("SMTPUTF8_REQUESTED", Boolean.class);

    /**
     * Returns the service wide configuration
     *
     * @return the configuration
     */
    @Override
    SMTPConfiguration getConfiguration();
    
    
    /**
     * Returns whether Relaying is allowed or not
     *
     * @return the relaying status
     */
    boolean isRelayingAllowed();
    
    /**
     * Set if reallying is allowed
     */
    void setRelayingAllowed(boolean relayingAllowed);

    /**
     * Returns whether Authentication is required or not
     *
     * @return authentication required or not
     */
    boolean isAuthAnnounced();

    /**
     Returns a boolean value indicating whether the user is unauthorized based on their priority level.
    *
    * @param priority the priority level of the user
    * @return true if the user is unauthorized, false otherwise
    */
    default boolean isUnAuthorized(int priority) {
        return this.getUsername() == null && priority > 0;
    }

    /**
     * Returns the recipient count
     * 
     * @return recipient count
     */
    int getRcptCount();


    long currentMessageSize();

    void setCurrentMessageSize(long increment);

    boolean headerComplete();

    void setHeaderComplete(boolean value);

    boolean messageFailed();

    void setMessageFailed(boolean value);

    default Set<String> disabledFeatures() {
        return ImmutableSet.of();
    }

}

