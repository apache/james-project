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
package org.apache.james.protocols.imap;

import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.handler.LineHandler;

public interface IMAPSession extends ProtocolSession{

    /**
     * Pop the last command handler 
     */
    void popLineHandler();
    
    /**
     * Return the size of the pushed {@link LineHandler}
     * @return size of the pushed line handler
     */
    int getPushedLineHandlerCount();

    /**
     * Logs out the session. Marks the connection for closure;
     */
    void logout();

    /**
     * Gets the current client state.
     * 
     * @return Returns the current state of this session.
     */
    ImapSessionState getSessionState();

    /**
     * Moves the session into {@link ImapSessionState#AUTHENTICATED} state.
     */
    void authenticated();

    /**
     * Moves this session into {@link ImapSessionState#SELECTED} state and sets
     * the supplied mailbox to be the currently selected mailbox.
     * 
     * @param mailbox
     *            The selected mailbox.
     */
    void selected(SelectedMailbox mailbox);

    /**
     * Moves the session out of {@link ImapSessionState#SELECTED} state and back
     * into {@link ImapSessionState#AUTHENTICATED} state. The selected mailbox
     * is cleared.
     */
    void deselect();

    /**
     * Provides the selected mailbox for this session, or <code>null</code> if
     * this session is not in {@link ImapSessionState#SELECTED} state.
     * 
     * @return the currently selected mailbox.
     */
    SelectedMailbox getSelected();

    /**
     * Return true if compression is active
     * 
     * @return compActive
     */
    public boolean isCompressionActive();

    /**
     * Return true if compression is supported. This is related to COMPRESS extension.
     * See http://www.ietf.org/rfc/rfc4978.txt
     * 
     * @return compressSupport
     */
    public boolean isCompressionSupported();

    /**
     * Start the compression
     * 
     * @return success
     */
    public boolean startCompression();

    /**
     * Return true if multiple namespaces are supported
     * 
     * @return multipleNamespaces
     */
    public boolean supportMultipleNamespaces();
    
    /**
     * Return true if the login / authentication via plain username / password is
     * disallowed
     * 
     * @return plainDisallowed
     */
    public boolean isPlainAuthDisallowed();
}
