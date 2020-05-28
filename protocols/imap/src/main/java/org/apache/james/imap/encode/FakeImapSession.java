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
package org.apache.james.imap.encode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;

public class FakeImapSession implements ImapSession {

    private ImapSessionState state = ImapSessionState.NON_AUTHENTICATED;

    private SelectedMailbox selectedMailbox = null;

    private final Map<String, Object> attributesByKey;
    private final SessionId sessionId;

    public FakeImapSession() {
        this.sessionId = SessionId.generate();
        this.attributesByKey = new ConcurrentHashMap<>();
    }

    @Override
    public SessionId sessionId() {
        return sessionId;
    }

    @Override
    public void logout() {
        closeMailbox();
        state = ImapSessionState.LOGOUT;
    }

    @Override
    public void authenticated() {
        this.state = ImapSessionState.AUTHENTICATED;
    }

    @Override
    public void deselect() {
        this.state = ImapSessionState.AUTHENTICATED;
        closeMailbox();
    }

    @Override
    public void selected(SelectedMailbox mailbox) {
        this.state = ImapSessionState.SELECTED;
        closeMailbox();
        this.selectedMailbox = mailbox;
    }

    @Override
    public SelectedMailbox getSelected() {
        return this.selectedMailbox;
    }

    @Override
    public ImapSessionState getState() {
        return this.state;
    }

    public void closeMailbox() {
        if (selectedMailbox != null) {
            selectedMailbox.deselect();
            selectedMailbox = null;
        }
    }

    @Override
    public Object getAttribute(String key) {
        return attributesByKey.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributesByKey.remove(key);
        } else {
            attributesByKey.put(key, value);
        }
    }
    
    @Override
    public boolean startTLS() {
        return false;
    }

    @Override
    public boolean supportStartTLS() {
        return false;
    }

    @Override
    public boolean isCompressionSupported() {
        return false;
    }

    @Override
    public boolean startCompression() {
        return false;
    }

    @Override
    public void pushLineHandler(ImapLineHandler lineHandler) {
    }

    @Override
    public void popLineHandler() {
        
    }

    @Override
    public boolean isPlainAuthDisallowed() {
        return false;
    }

    @Override
    public boolean isTLSActive() {
        return false;
    }

    @Override
    public boolean supportMultipleNamespaces() {
        return false;
    }

    @Override
    public boolean isCompressionActive() {
        return false;
    }

}
