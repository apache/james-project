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

/**
 * Represents a processor for a particular Imap command. Implementations of this
 * interface should encpasulate all command specific processing.
 */
public class ImapCommand {
    public static ImapCommand nonAuthenticatedStateCommand(final String name) {
        return new ImapCommand(false, false, true, name);
    }

    public static ImapCommand authenticatedStateCommand(final String name) {
        return new ImapCommand(true, true, false, name);
    }

    public static ImapCommand selectedStateCommand(final String name) {
        return new ImapCommand(false, true, false, name);
    }

    public static ImapCommand anyStateCommand(final String name) {
        return new ImapCommand(true, true, true, name);
    }

    private final boolean validInAuthenticated;

    private final boolean validInSelected;

    private final boolean validInNonAuthenticated;

    private final String name;

    private ImapCommand(boolean validInAuthenticated, boolean validInSelected, boolean validInNonAuthenticated, final String name) {
        super();
        this.validInAuthenticated = validInAuthenticated;
        this.validInSelected = validInSelected;
        this.validInNonAuthenticated = validInNonAuthenticated;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean validForState(ImapSessionState state) {
        final boolean result;
        switch (state) {
        case AUTHENTICATED:
            result = validInAuthenticated;
            break;
        case NON_AUTHENTICATED:
            result = validInNonAuthenticated;
            break;
        case SELECTED:
            result = validInSelected;
            break;
        default:
            result = false;
        }
        return result;
    }

    public String toString() {
        return name;
    }
}
