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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

import com.github.steveash.guavate.Guavate;

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

    @Given("^\"([^\"]*)\" shares its mailbox \"([^\"]*)\" with \"([^\"]*)\"$")
    public void shareMailbox(String owner, String mailbox, String shareTo) throws Throwable {
        MailboxPath mailboxPath = MailboxPath.forUser(owner, mailbox);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read);

        mainStepdefs.aclProbe.addRights(mailboxPath, shareTo, rights);
    }

    @Given("^\"([^\"]*)\" shares its mailbox \"([^\"]*)\" with rights \"([^\"]*)\" with \"([^\"]*)\"$")
    public void shareMailboxWithRight(String owner, String mailbox, String rights, String shareTo) throws Throwable {
        MailboxPath mailboxPath = MailboxPath.forUser(owner, mailbox);

        mainStepdefs.aclProbe.replaceRights(mailboxPath, shareTo, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights(rights));
    }
}
