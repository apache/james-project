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

package org.apache.james.examples.imap;

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.message.request.AbstractImapRequest;
import org.apache.james.modules.protocols.ImapPackage;
import org.apache.james.utils.ClassName;

import com.google.common.collect.ImmutableList;

public class PingImapPackages extends ImapPackage.Impl {
    public static class PingRequest extends AbstractImapRequest {
        public PingRequest(Tag tag) {
            super(tag, PING_COMMAND);
        }
    }

    public static class PingResponse implements ImapResponseMessage {
    }

    public static ImapCommand PING_COMMAND = ImapCommand.anyStateCommand("PING");

    @Inject
    public PingImapPackages() {
        super(ImmutableList.of(new ClassName(PingProcessor.class.getCanonicalName())),
            ImmutableList.of(new ClassName(PingCommandParser.class.getCanonicalName())),
            ImmutableList.of(new ClassName(PingResponseEncoder.class.getCanonicalName())));
    }
}
