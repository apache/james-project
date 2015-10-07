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

package org.apache.james.imap.api.process;

import org.apache.james.imap.api.ImapSessionState;
import org.slf4j.Logger;

/**
 * Encapsulates all state held for an ongoing Imap session, which commences when
 * a client first establishes a connection to the Imap server, and continues
 * until that connection is closed.
 * 
 * @version $Revision: 109034 $
 */
public interface ImapSession {

    /**
     * Gets the context sensitive log for this session. Understanding the
     * context of a log message is an important comprehension aid when analying
     * multi-threaded systems. Using this log allows context information to be
     * associated.
     * 
     * @return context sensitive log, not null
     */
    Logger getLog();

    /**
     * Logs out the session. Marks the connection for closure;
     */
    void logout();

    /**
     * Gets the current client state.
     * 
     * @return Returns the current state of this session.
     */
    ImapSessionState getState();

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
     * Gets an attribute of this session by name. Implementations should ensure
     * that access is thread safe.
     * 
     * @param key
     *            name of the key, not null
     * @return <code>Object</code> value or null if this attribute has unvalued
     */
    public Object getAttribute(String key);

    /**
     * Sets an attribute of this session by name. Implementations should ensure
     * that access is thread safe.
     * 
     * @param key
     *            name of the key, not null
     * @param value
     *            <code>Object</code> value or null to set this attribute as
     *            unvalued
     */
    public void setAttribute(String key, Object value);

    /**
     * Start TLS encryption of the session after the next response was written.
     * So you must make sure the next response will get send in clear text
     * 
     * @return true if the encryption of the session was successfully
     */
    public boolean startTLS();

    /**
     * Return true if the session is bound to a TLS encrypted socket.
     * 
     * @return tlsActive
     */
    public boolean isTLSActive();
 
    /**
     * Support startTLS ?
     * 
     * @return true if startTLS is supported
     */
    public boolean supportStartTLS();

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
     * Push in a new {@link ImapLineHandler} which is called for the next line received
     * 
     * @param lineHandler
     */
    public void pushLineHandler(ImapLineHandler lineHandler);

    /**
     * Pop the current {@link ImapLineHandler}
     * 
     */
    public void popLineHandler();
    
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
