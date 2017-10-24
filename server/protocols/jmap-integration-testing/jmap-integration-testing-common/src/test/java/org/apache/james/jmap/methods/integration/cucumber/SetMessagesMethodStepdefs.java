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

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.jmap.model.Keywords;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.MailboxProbeImpl;

import com.google.common.collect.ImmutableList;

import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class SetMessagesMethodStepdefs {

    private final MainStepdefs mainStepdefs;
    private final UserStepdefs userStepdefs;
    private final HttpStepDefs httpStepDefs;
    private final GetMessagesMethodStepdefs getMessagesMethodStepdefs;

    @Inject
    private SetMessagesMethodStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs, HttpStepDefs httpStepDefs, GetMessagesMethodStepdefs getMessagesMethodStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
        this.httpStepDefs = httpStepDefs;
        this.getMessagesMethodStepdefs = getMessagesMethodStepdefs;
    }

    @When("^the user move \"([^\"]*)\" to mailbox \"([^\"]*)\"")
    public void moveMessageToMailbox(String message, String mailbox) throws Throwable {
        MessageId messageId = getMessagesMethodStepdefs.getMessageId(message);
        MailboxId mailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, userStepdefs.getConnectedUser(), mailbox)
            .getMailboxId();

        httpStepDefs.post("[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"update\": { \"" + messageId.serialize() + "\" : {" +
            "        \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]");
        mainStepdefs.awaitMethod.run();
    }

    @When("^\"([^\"]*)\" copy \"([^\"]*)\" from mailbox \"([^\"]*)\" to mailbox \"([^\"]*)\"")
    public void copyMessageToMailbox(String userName, String message, String sourceMailbox, String destinationMailbox) throws Throwable {
        MessageId messageId = getMessagesMethodStepdefs.getMessageId(message);
        MailboxId sourceMailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, userName, sourceMailbox)
            .getMailboxId();
        MailboxId destinationMailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, userName, destinationMailbox)
            .getMailboxId();


        httpStepDefs.post("[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"update\": { \"" + messageId.serialize() + "\" : {" +
            "        \"mailboxIds\": [\"" + destinationMailboxId.serialize() + "\",\"" + sourceMailboxId.serialize() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]");
        mainStepdefs.awaitMethod.run();
    }

    @When("^the user set flags on \"([^\"]*)\" to \"([^\"]*)\"")
    public void setFlags(String message, List<String> keywords) throws Throwable {
        MessageId messageId = getMessagesMethodStepdefs.getMessageId(message);
        String keywordString = keywords
            .stream()
            .map(value -> "\"" + value + "\" : true")
            .collect(Collectors.joining(","));

        httpStepDefs.post("[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"update\": { \"" + messageId.serialize() + "\" : {" +
            "        \"keywords\": {" + keywordString + "}" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]");
        mainStepdefs.awaitMethod.run();
    }

    @When("^message \"([^\"]*)\" has flags (.*) in mailbox \"([^\"]*)\" of user \"([^\"]*)\"")
    public void setMessageFlagsInSpecifiedMailbox(String message, List<String> flags, String mailbox, String mailboxOwner) throws Exception {
        Flags newFlags = Keywords.factory().fromList(flags).asFlags();
        String username = userStepdefs.getConnectedUser();
        MessageId messageId = getMessagesMethodStepdefs.getMessageId(message);
        MailboxId mailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, mailboxOwner, mailbox)
            .getMailboxId();

        mainStepdefs.messageIdProbe.updateNewFlags(username, newFlags, messageId, ImmutableList.of(mailboxId));
        mainStepdefs.awaitMethod.run();
    }
}
