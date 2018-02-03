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

package org.apache.james.imap.decode.parser;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.DelegatingImapCommandParser;
import org.apache.james.imap.decode.ImapCommandParser;
import org.apache.james.imap.decode.ImapCommandParserFactory;
import org.apache.james.imap.decode.MessagingImapCommandParser;

/**
 * A factory for ImapCommand instances, provided based on the command name.
 * Command instances are created on demand, when first accessed.
 */
public class ImapParserFactory implements ImapCommandParserFactory {
    private final Map<String, Class<?>> imapCommands;

    private final StatusResponseFactory statusResponseFactory;

    public ImapParserFactory(StatusResponseFactory statusResponseFactory) {
        this.statusResponseFactory = statusResponseFactory;
        imapCommands = new HashMap<>();

        // Commands valid in any state
        // CAPABILITY, NOOP, and LOGOUT
        imapCommands.put(ImapConstants.CAPABILITY_COMMAND_NAME, CapabilityCommandParser.class);
        imapCommands.put(ImapConstants.NOOP_COMMAND_NAME, NoopCommandParser.class);
        imapCommands.put(ImapConstants.LOGOUT_COMMAND_NAME, LogoutCommandParser.class);

        // Commands valid in NON_AUTHENTICATED state.
        // AUTHENTICATE and LOGIN
        imapCommands.put(ImapConstants.AUTHENTICATE_COMMAND_NAME, AuthenticateCommandParser.class);
        imapCommands.put(ImapConstants.LOGIN_COMMAND_NAME, LoginCommandParser.class);

        // Commands valid in AUTHENTICATED or SELECTED state.
        // RFC2060: SELECT, EXAMINE, CREATE, DELETE, RENAME, SUBSCRIBE,
        // UNSUBSCRIBE, LIST, LSUB, STATUS, and APPEND
        imapCommands.put(ImapConstants.SELECT_COMMAND_NAME, SelectCommandParser.class);
        imapCommands.put(ImapConstants.EXAMINE_COMMAND_NAME, ExamineCommandParser.class);
        imapCommands.put(ImapConstants.CREATE_COMMAND_NAME, CreateCommandParser.class);
        imapCommands.put(ImapConstants.DELETE_COMMAND_NAME, DeleteCommandParser.class);
        imapCommands.put(ImapConstants.RENAME_COMMAND_NAME, RenameCommandParser.class);
        imapCommands.put(ImapConstants.SUBSCRIBE_COMMAND_NAME, SubscribeCommandParser.class);
        imapCommands.put(ImapConstants.UNSUBSCRIBE_COMMAND_NAME, UnsubscribeCommandParser.class);
        imapCommands.put(ImapConstants.LIST_COMMAND_NAME, ListCommandParser.class);
        imapCommands.put(ImapConstants.XLIST_COMMAND_NAME, XListCommandParser.class);
        imapCommands.put(ImapConstants.LSUB_COMMAND_NAME, LsubCommandParser.class);
        imapCommands.put(ImapConstants.STATUS_COMMAND_NAME, StatusCommandParser.class);
        imapCommands.put(ImapConstants.APPEND_COMMAND_NAME, AppendCommandParser.class);

        // RFC2342 NAMESPACE
        imapCommands.put(ImapConstants.NAMESPACE_COMMAND_NAME, NamespaceCommandParser.class);

        // RFC4314 GETACL, SETACL, DELETEACL, LISTRIGHTS, MYRIGHTS
        imapCommands.put(ImapConstants.GETACL_COMMAND_NAME, GetACLCommandParser.class);
        imapCommands.put(ImapConstants.SETACL_COMMAND_NAME, SetACLCommandParser.class);
        imapCommands.put(ImapConstants.DELETEACL_COMMAND_NAME, DeleteACLCommandParser.class);
        imapCommands.put(ImapConstants.LISTRIGHTS_COMMAND_NAME, ListRightsCommandParser.class);
        imapCommands.put(ImapConstants.MYRIGHTS_COMMAND_NAME, MyRightsCommandParser.class);

        // Commands only valid in SELECTED state.
        // CHECK, CLOSE, EXPUNGE, SEARCH, FETCH, STORE, COPY, UID and IDLE
        imapCommands.put(ImapConstants.CHECK_COMMAND_NAME, CheckCommandParser.class);
        imapCommands.put(ImapConstants.CLOSE_COMMAND_NAME, CloseCommandParser.class);
        imapCommands.put(ImapConstants.EXPUNGE_COMMAND_NAME, ExpungeCommandParser.class);
        imapCommands.put(ImapConstants.COPY_COMMAND_NAME, CopyCommandParser.class);
        imapCommands.put(ImapConstants.MOVE_COMMAND_NAME, MoveCommandParser.class);
        imapCommands.put(ImapConstants.SEARCH_COMMAND_NAME, SearchCommandParser.class);
        imapCommands.put(ImapConstants.FETCH_COMMAND_NAME, FetchCommandParser.class);
        imapCommands.put(ImapConstants.STORE_COMMAND_NAME, StoreCommandParser.class);
        imapCommands.put(ImapConstants.UID_COMMAND_NAME, UidCommandParser.class);
        imapCommands.put(ImapConstants.IDLE_COMMAND_NAME, IdleCommandParser.class);
        imapCommands.put(ImapConstants.STARTTLS, StartTLSCommandParser.class);

        // RFC3691
        imapCommands.put(ImapConstants.UNSELECT_COMMAND_NAME, UnselectCommandParser.class);

        // RFC4978
        imapCommands.put(ImapConstants.COMPRESS_COMMAND_NAME, CompressCommandParser.class);
        
        imapCommands.put(ImapConstants.ENABLE_COMMAND_NAME, EnableCommandParser.class);

        // RFC2087
        // GETQUOTAROOT, GETQUOTA, SETQUOTA
        imapCommands.put(ImapConstants.GETQUOTAROOT_COMMAND_NAME, GetQuotaRootCommandParser.class);
        imapCommands.put(ImapConstants.GETQUOTA_COMMAND_NAME, GetQuotaCommandParser.class);
        imapCommands.put(ImapConstants.SETQUOTA_COMMAND_NAME, SetQuotaCommandParser.class);

        //RFC5464
        //SETMETADATA, GETMETADATA
        imapCommands.put(ImapConstants.SETANNOTATION_COMMAND_NAME, SetAnnotationCommandParser.class);
        imapCommands.put(ImapConstants.GETANNOTATION_COMMAND_NAME, GetAnnotationCommandParser.class);
    }

    /**
     * @see org.apache.james.imap.decode.ImapCommandParserFactory#getParser(java.lang.String)
     */
    public ImapCommandParser getParser(String commandName) {
        Class<?> cmdClass = imapCommands.get(commandName.toUpperCase(Locale.US));

        if (cmdClass == null) {
            return null;
        } else {
            return createCommand(cmdClass);
        }
    }

    private ImapCommandParser createCommand(Class<?> commandClass) {
        try {
            ImapCommandParser cmd = (ImapCommandParser) commandClass.newInstance();
            initialiseParser(cmd);
            return cmd;
        } catch (Exception e) {
            // TODO: would probably be better to manage this in protocol
            // TODO: this runtime will produce a nasty disconnect for the client
            throw new RuntimeException("Could not create command instance: " + commandClass.getName(), e);
        }
    }

    protected void initialiseParser(ImapCommandParser cmd) {

        if (cmd instanceof DelegatingImapCommandParser) {
            ((DelegatingImapCommandParser) cmd).setParserFactory(this);
        }

        if (cmd instanceof MessagingImapCommandParser) {
            final MessagingImapCommandParser messagingImapCommandParser = (MessagingImapCommandParser) cmd;
            messagingImapCommandParser.setStatusResponseFactory(statusResponseFactory);
        }
    }

}
