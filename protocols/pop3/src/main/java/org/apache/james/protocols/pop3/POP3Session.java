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

package org.apache.james.protocols.pop3;

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.pop3.mailbox.Mailbox;

/**
 * All the handlers access this interface to communicate with POP3Handler object
 */

public interface POP3Session extends ProtocolSession {

    final static String UID_LIST = "UID_LIST";
    final static String DELETED_UID_LIST = "DELETED_UID_LIST";
    final static String APOP_TIMESTAMP = "APOP_TIMESTAMP";

    // Authentication states for the POP3 interaction
    /** Waiting for user id */
    final static int AUTHENTICATION_READY = 0;
    /** User id provided, waiting for password */
    final static int AUTHENTICATION_USERSET = 1;
    /**
     * A valid user id/password combination has been provided. In this state the
     * client can access the mailbox of the specified user.
     */
    final static int TRANSACTION = 2;

    /**
     * Returns the current handler state
     * 
     * @return handler state
     */
    int getHandlerState();

    /**
     * Sets the new handler state
     * 
     * @param handlerState
     *            state
     */
    void setHandlerState(int handlerState);

    /**
     * Returns the mail list contained in the mailbox
     * 
     * @return mailbox content
     */
    Mailbox getUserMailbox();

    /**
     * Sets a new mailbox content
     * 
     * @param mailbox
     *            mailbox
     */
    void setUserMailbox(Mailbox mailbox);
}
