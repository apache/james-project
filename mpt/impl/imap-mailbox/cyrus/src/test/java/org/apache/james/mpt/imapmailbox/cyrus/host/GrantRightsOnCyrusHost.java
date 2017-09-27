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

package org.apache.james.mpt.imapmailbox.cyrus.host;

import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.imapmailbox.GrantRightsOnHost;
import org.apache.james.mpt.protocol.ProtocolSession;

import com.google.inject.Inject;

public class GrantRightsOnCyrusHost implements GrantRightsOnHost {
    private static final String GRANT_RIGHTS_LOCATION = "ACLCommands.grantRights";

    private final CyrusHostSystem system;

    @Inject
    private GrantRightsOnCyrusHost(CyrusHostSystem system) {
        this.system = system;
    }

    public void grantRights(MailboxPath mailboxPath, String userName, MailboxACL.Rfc4314Rights rights) throws Exception {
        ProtocolSession protocolSession = system.logAndGetAdminProtocolSession(new ProtocolSession());
        protocolSession.CL(String.format("A1 SETACL %s %s %s",
            system.createMailboxStringFromMailboxPath(mailboxPath),
            userName,
            rights.serialize()));
        protocolSession.SL("A1 OK .*", GRANT_RIGHTS_LOCATION);
        system.executeProtocolSession(system.logoutAndGetProtocolSession(protocolSession));
    }
}
