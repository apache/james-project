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

package org.apache.james.imap.api;

import java.util.EnumSet;

/**
 * Represents a processor for a particular Imap command. Implementations of this
 * interface should encpasulate all command specific processing.
 */
public class ImapCommand {
    enum Validity {
        NonAuthenticated(EnumSet.of(ImapSessionState.NON_AUTHENTICATED)),
        Authenticated(EnumSet.of(ImapSessionState.AUTHENTICATED, ImapSessionState.SELECTED)),
        Selected(EnumSet.of(ImapSessionState.SELECTED)),
        Any(EnumSet.of(ImapSessionState.AUTHENTICATED, ImapSessionState.NON_AUTHENTICATED, ImapSessionState.SELECTED));

        private final EnumSet<ImapSessionState> validStates;

        Validity(EnumSet<ImapSessionState> validStates) {
            this.validStates = validStates;
        }

        boolean allowed(ImapSessionState sessionState) {
            return validStates.contains(sessionState);
        }
    }

    public static ImapCommand nonAuthenticatedStateCommand(String name) {
        return new ImapCommand(Validity.NonAuthenticated, name);
    }

    public static ImapCommand authenticatedStateCommand(String name) {
        return new ImapCommand(Validity.Authenticated, name);
    }

    public static ImapCommand selectedStateCommand(String name) {
        return new ImapCommand(Validity.Selected, name);
    }

    public static ImapCommand anyStateCommand(String name) {
        return new ImapCommand(Validity.Any, name);
    }

    private final Validity validity;
    private final String name;

    private ImapCommand(Validity validity, String name) {
        this.validity = validity;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean validForState(ImapSessionState state) {
        return validity.allowed(state);
    }

    public String toString() {
        return name;
    }
}
