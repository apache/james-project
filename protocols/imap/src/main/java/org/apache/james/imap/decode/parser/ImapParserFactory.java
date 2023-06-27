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

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.ImapCommandParser;
import org.apache.james.imap.decode.ImapCommandParserFactory;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;

import com.google.common.collect.ImmutableMap;

/**
 * A factory for ImapCommand instances, provided based on the command name.
 * Command instances are created on demand, when first accessed.
 */
public class ImapParserFactory implements ImapCommandParserFactory {
    private final Map<String, ImapCommandParser> imapCommands;

    public ImapParserFactory(StatusResponseFactory statusResponseFactory) {
        Stream<AbstractImapCommandParser> parsers = Stream.of(
            // Commands valid in any state
            // CAPABILITY, NOOP, and LOGOUT
            new CapabilityCommandParser(statusResponseFactory),
            new NoopCommandParser(statusResponseFactory),
            new LogoutCommandParser(statusResponseFactory),

            // Commands valid in NON_AUTHENTICATED state.
            // AUTHENTICATE and LOGIN
            new AuthenticateCommandParser(statusResponseFactory),
            new LoginCommandParser(statusResponseFactory),

            // Commands valid in AUTHENTICATED or SELECTED state.
            // RFC2060: SELECT, EXAMINE, CREATE, DELETE, RENAME, SUBSCRIBE,
            // UNSUBSCRIBE, LIST, LSUB, STATUS, and APPEND
            new SelectCommandParser(statusResponseFactory),
            new ExamineCommandParser(statusResponseFactory),
            new ReplaceCommandParser(statusResponseFactory, Clock.systemDefaultZone()),
            new CreateCommandParser(statusResponseFactory),
            new DeleteCommandParser(statusResponseFactory),
            new RenameCommandParser(statusResponseFactory),
            new SubscribeCommandParser(statusResponseFactory),
            new UnsubscribeCommandParser(statusResponseFactory),
            new ListCommandParser(statusResponseFactory),
            new XListCommandParser(statusResponseFactory),
            new LsubCommandParser(statusResponseFactory),
            new StatusCommandParser(statusResponseFactory),
            new AppendCommandParser(statusResponseFactory, Clock.systemDefaultZone()),

            // RFC2342 NAMESPACE
            new NamespaceCommandParser(statusResponseFactory),

            // RFC4314 GETACL, SETACL, DELETEACL, LISTRIGHTS, MYRIGHTS
            new GetACLCommandParser(statusResponseFactory),
            new SetACLCommandParser(statusResponseFactory),
            new DeleteACLCommandParser(statusResponseFactory),
            new ListRightsCommandParser(statusResponseFactory),
            new MyRightsCommandParser(statusResponseFactory),

            // Commands only valid in SELECTED state.
            // CHECK, CLOSE, EXPUNGE, SEARCH, FETCH, STORE, COPY, UID and IDLE
            new CheckCommandParser(statusResponseFactory),
            new CloseCommandParser(statusResponseFactory),
            new ExpungeCommandParser(statusResponseFactory),
            new CopyCommandParser(statusResponseFactory),
            new MoveCommandParser(statusResponseFactory),
            new SearchCommandParser(statusResponseFactory),
            new FetchCommandParser(statusResponseFactory),
            new StoreCommandParser(statusResponseFactory),
            new UidCommandParser(this, statusResponseFactory),
            new IdleCommandParser(statusResponseFactory),
            new IDCommandParser(statusResponseFactory),
            new StartTLSCommandParser(statusResponseFactory),

            // RFC3691
            new UnselectCommandParser(statusResponseFactory),

            // RFC4978
            new CompressCommandParser(statusResponseFactory),

            new EnableCommandParser(statusResponseFactory),

            // RFC2087
            // GETQUOTAROOT, GETQUOTA, SETQUOTA
            new GetQuotaRootCommandParser(statusResponseFactory),
            new GetQuotaCommandParser(statusResponseFactory),
            new SetQuotaCommandParser(statusResponseFactory),

            //RFC5464
            //SETMETADATA, GETMETADATA
            new SetAnnotationCommandParser(statusResponseFactory),
            new GetMetadataCommandParser(statusResponseFactory));

        imapCommands = parsers.collect(ImmutableMap.toImmutableMap(
                parser -> parser.getCommand().getName(),
                Function.identity()));
    }

    public ImapParserFactory(Map<String, ImapCommandParser> imapCommands) {
        this.imapCommands = imapCommands;
    }

    @Override
    public ImapCommandParser getParser(String commandName) {
        return imapCommands.get(commandName.toUpperCase(Locale.US));
    }

    public ImapParserFactory union(ImapParserFactory other) {
        return new ImapParserFactory(ImmutableMap.<String, ImapCommandParser>builder()
            .putAll(this.imapCommands)
            .putAll(other.imapCommands)
            .build());
    }
}
