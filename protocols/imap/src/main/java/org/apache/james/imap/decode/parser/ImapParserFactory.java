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
import org.apache.james.imap.decode.ImapCommandParser;
import org.apache.james.imap.decode.ImapCommandParserFactory;
import org.apache.james.imap.decode.MessagingImapCommandParser;

/**
 * A factory for ImapCommand instances, provided based on the command name.
 * Command instances are created on demand, when first accessed.
 */
public class ImapParserFactory implements ImapCommandParserFactory {
    private final Map<String, ImapCommandParser> imapCommands;

    private final StatusResponseFactory statusResponseFactory;

    public ImapParserFactory(StatusResponseFactory statusResponseFactory) {
        this.statusResponseFactory = statusResponseFactory;
        imapCommands = new HashMap<>();

        // Commands valid in any state
        // CAPABILITY, NOOP, and LOGOUT
        imapCommands.put(ImapConstants.CAPABILITY_COMMAND_NAME, new CapabilityCommandParser());
        imapCommands.put(ImapConstants.NOOP_COMMAND_NAME, new NoopCommandParser());
        imapCommands.put(ImapConstants.LOGOUT_COMMAND_NAME, new LogoutCommandParser());

        // Commands valid in NON_AUTHENTICATED state.
        // AUTHENTICATE and LOGIN
        imapCommands.put(ImapConstants.AUTHENTICATE_COMMAND_NAME, new AuthenticateCommandParser());
        imapCommands.put(ImapConstants.LOGIN_COMMAND_NAME, new LoginCommandParser());

        // Commands valid in AUTHENTICATED or SELECTED state.
        // RFC2060: SELECT, EXAMINE, CREATE, DELETE, RENAME, SUBSCRIBE,
        // UNSUBSCRIBE, LIST, LSUB, STATUS, and APPEND
        imapCommands.put(ImapConstants.SELECT_COMMAND_NAME, new SelectCommandParser());
        imapCommands.put(ImapConstants.EXAMINE_COMMAND_NAME, new ExamineCommandParser());
        imapCommands.put(ImapConstants.CREATE_COMMAND_NAME, new CreateCommandParser());
        imapCommands.put(ImapConstants.DELETE_COMMAND_NAME, new DeleteCommandParser());
        imapCommands.put(ImapConstants.RENAME_COMMAND_NAME, new RenameCommandParser());
        imapCommands.put(ImapConstants.SUBSCRIBE_COMMAND_NAME, new SubscribeCommandParser());
        imapCommands.put(ImapConstants.UNSUBSCRIBE_COMMAND_NAME, new UnsubscribeCommandParser());
        imapCommands.put(ImapConstants.LIST_COMMAND_NAME, new ListCommandParser());
        imapCommands.put(ImapConstants.XLIST_COMMAND_NAME, new XListCommandParser());
        imapCommands.put(ImapConstants.LSUB_COMMAND_NAME, new LsubCommandParser());
        imapCommands.put(ImapConstants.STATUS_COMMAND_NAME, new StatusCommandParser());
        imapCommands.put(ImapConstants.APPEND_COMMAND_NAME,new AppendCommandParser());

        // RFC2342 NAMESPACE
        imapCommands.put(ImapConstants.NAMESPACE_COMMAND_NAME, new NamespaceCommandParser());

        // RFC4314 GETACL, SETACL, DELETEACL, LISTRIGHTS, MYRIGHTS
        imapCommands.put(ImapConstants.GETACL_COMMAND_NAME, new GetACLCommandParser());
        imapCommands.put(ImapConstants.SETACL_COMMAND_NAME, new SetACLCommandParser());
        imapCommands.put(ImapConstants.DELETEACL_COMMAND_NAME, new DeleteACLCommandParser());
        imapCommands.put(ImapConstants.LISTRIGHTS_COMMAND_NAME, new ListRightsCommandParser());
        imapCommands.put(ImapConstants.MYRIGHTS_COMMAND_NAME, new MyRightsCommandParser());

        // Commands only valid in SELECTED state.
        // CHECK, CLOSE, EXPUNGE, SEARCH, FETCH, STORE, COPY, UID and IDLE
        imapCommands.put(ImapConstants.CHECK_COMMAND_NAME, new CheckCommandParser());
        imapCommands.put(ImapConstants.CLOSE_COMMAND_NAME, new CloseCommandParser());
        imapCommands.put(ImapConstants.EXPUNGE_COMMAND_NAME, new ExpungeCommandParser());
        imapCommands.put(ImapConstants.COPY_COMMAND_NAME, new CopyCommandParser());
        imapCommands.put(ImapConstants.MOVE_COMMAND_NAME, new MoveCommandParser());
        imapCommands.put(ImapConstants.SEARCH_COMMAND_NAME, new SearchCommandParser());
        imapCommands.put(ImapConstants.FETCH_COMMAND_NAME, new FetchCommandParser());
        imapCommands.put(ImapConstants.STORE_COMMAND_NAME, new StoreCommandParser());
        imapCommands.put(ImapConstants.UID_COMMAND_NAME, new UidCommandParser(this));
        imapCommands.put(ImapConstants.IDLE_COMMAND_NAME, new IdleCommandParser());
        imapCommands.put(ImapConstants.STARTTLS, new StartTLSCommandParser());

        // RFC3691
        imapCommands.put(ImapConstants.UNSELECT_COMMAND_NAME, new UnselectCommandParser());

        // RFC4978
        imapCommands.put(ImapConstants.COMPRESS_COMMAND_NAME, new CompressCommandParser());
        
        imapCommands.put(ImapConstants.ENABLE_COMMAND_NAME, new EnableCommandParser());

        // RFC2087
        // GETQUOTAROOT, GETQUOTA, SETQUOTA
        imapCommands.put(ImapConstants.GETQUOTAROOT_COMMAND_NAME, new GetQuotaRootCommandParser());
        imapCommands.put(ImapConstants.GETQUOTA_COMMAND_NAME, new GetQuotaCommandParser());
        imapCommands.put(ImapConstants.SETQUOTA_COMMAND_NAME, new SetQuotaCommandParser());

        //RFC5464
        //SETMETADATA, GETMETADATA
        imapCommands.put(ImapConstants.SETANNOTATION_COMMAND_NAME, new SetAnnotationCommandParser());
        imapCommands.put(ImapConstants.GETANNOTATION_COMMAND_NAME, new GetAnnotationCommandParser());
    }

    @Override
    public ImapCommandParser getParser(String commandName) {
        ImapCommandParser parser = imapCommands.get(commandName.toUpperCase(Locale.US));

        if (parser == null) {
            return null;
        } else {
            initialiseParser(parser);
            return parser;
        }
    }

    private void initialiseParser(ImapCommandParser cmd) {
        if (cmd instanceof MessagingImapCommandParser) {
            final MessagingImapCommandParser messagingImapCommandParser = (MessagingImapCommandParser) cmd;
            messagingImapCommandParser.setStatusResponseFactory(statusResponseFactory);
        }
    }
}
