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

package org.apache.james.jmap.draft.methods.integration.cucumber;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.draft.MessageIdProbe;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.probe.ACLProbe;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import io.cucumber.guice.ScenarioScoped;

@ScenarioScoped
public class MainStepdefs {

    public GuiceJamesServer jmapServer;
    public DataProbe dataProbe;
    public MailboxProbeImpl mailboxProbe;
    public ACLProbe aclProbe;
    public MessageIdProbe messageIdProbe;
    public Runnable awaitMethod = () -> { };
    public MessageId.Factory messageIdFactory;
    
    public void init() throws Exception {
        jmapServer.start();
        dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        aclProbe = jmapServer.getProbe(ACLProbeImpl.class);
        messageIdProbe = jmapServer.getProbe(MessageIdProbe.class);
    }

    public void tearDown() {
        jmapServer.stop();
    }

    public MailboxId getMailboxId(String username, String mailbox) {
        return getMailboxId(MailboxConstants.USER_NAMESPACE, username, mailbox);
    }

    public MailboxId getMailboxId(String namespace, String username, String mailbox) {
        return mailboxProbe.getMailboxId(namespace, username, mailbox);
    }
    
    public String getMailboxIds(String username, List<String> mailboxes) {
        return Joiner.on("\",\"")
            .join(getMailboxIdsList(username, mailboxes.stream()));
    }

    public ImmutableList<String> getMailboxIdsList(String username, Stream<String> mailboxes) {
        return mailboxes.map(mailbox -> getMailboxId(username, mailbox)
                .serialize())
            .collect(ImmutableList.toImmutableList());
    }

    public ImmutableList<String> getMailboxIdsList(String username, Collection<String> mailboxes) {
        return getMailboxIdsList(username, mailboxes.stream());
    }
}
