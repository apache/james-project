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

package org.apache.james;

import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.LmtpGuiceProbe;
import org.apache.james.modules.protocols.Pop3GuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;

public interface JamesServerConcreteContract extends JamesServerContract {
    @Override
    default int imapPort(GuiceJamesServer server) {
        return server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    @Override
    default int imapsPort(GuiceJamesServer server) {
        return server.getProbe(ImapGuiceProbe.class).getImapStartTLSPort();
    }

    @Override
    default int smtpPort(GuiceJamesServer server) {
        return server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue();
    }

    @Override
    default int lmtpPort(GuiceJamesServer server) {
        return server.getProbe(LmtpGuiceProbe.class).getLmtpPort();
    }

    @Override
    default int pop3Port(GuiceJamesServer server) {
        return server.getProbe(Pop3GuiceProbe.class).getPop3Port();
    }
}
