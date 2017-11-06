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

package org.apache.james.jmap.methods.integration.cucumber;

import javax.inject.Inject;

import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.ACLProbeImpl;

import cucumber.api.java.en.Given;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class MailboxStepdefs {

    private final MainStepdefs mainStepdefs;

    @Inject
    private MailboxStepdefs(MainStepdefs mainStepdefs) {
        this.mainStepdefs = mainStepdefs;
    }

    @Given("^\"([^\"]*)\" has a mailbox \"([^\"]*)\"$")
    public void createMailbox(String username, String mailbox) throws Throwable {
        mainStepdefs.mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, mailbox);
    }

    @Given("^\"([^\"]*)\" shares its mailbox \"([^\"]*)\" with rights \"([^\"]*)\" with \"([^\"]*)\"$")
    public void shareMailboxWithRight(String owner, String mailbox, String rights, String shareTo) throws Throwable {
        MailboxPath mailboxPath = MailboxPath.forUser(owner, mailbox);

        mainStepdefs.aclProbe.replaceRights(mailboxPath, shareTo, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights(rights));
    }
    
    @Given("^\"([^\"]*)\" shares (?:his|her) mailbox \"([^\"]*)\" with \"([^\"]*)\" with \"([^\"]*)\" rights$")
    public void shareMailbox(String owner, String mailbox, String shareTo, String rights) throws Throwable {
        mainStepdefs.jmapServer.getProbe(ACLProbeImpl.class)
            .replaceRights(MailboxPath.forUser(owner, mailbox),
                shareTo,
                MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights(rights));
    }
}
