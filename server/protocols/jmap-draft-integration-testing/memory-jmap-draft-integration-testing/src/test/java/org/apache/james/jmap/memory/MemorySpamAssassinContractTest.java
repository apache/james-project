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
package org.apache.james.jmap.memory;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.jmap.draft.methods.integration.SpamAssassinContract;
import org.apache.james.jmap.draft.methods.integration.SpamAssassinModuleExtension;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.spamassassin.SpamAssassinExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

class MemorySpamAssassinContractTest implements SpamAssassinContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .extension(new SpamAssassinModuleExtension())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .build();

    @Override
    @Tag(Unstable.TAG)
    public void spamShouldBeDeliveredInSpamMailboxWhenSameMessageHasAlreadyBeenMovedToSpam(GuiceJamesServer jamesServer, SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        SpamAssassinContract.super.spamShouldBeDeliveredInSpamMailboxWhenSameMessageHasAlreadyBeenMovedToSpam(jamesServer, spamAssassin);
    }

    @Override
    @Tag(Unstable.TAG)
    public void spamShouldBeDeliveredInSpamMailboxOrInboxWhenMultipleRecipientsConfigurations(GuiceJamesServer jamesServer, SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        SpamAssassinContract.super.spamShouldBeDeliveredInSpamMailboxOrInboxWhenMultipleRecipientsConfigurations(jamesServer, spamAssassin);
    }

    @Override
    @Tag(Unstable.TAG)
    public void movingAMailToTrashShouldNotImpactSpamassassinLearning(GuiceJamesServer jamesServer, SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        SpamAssassinContract.super.movingAMailToTrashShouldNotImpactSpamassassinLearning(jamesServer, spamAssassin);
    }

    @Override
    @Tag(Unstable.TAG)
    public void expungingSpamMessageShouldNotImpactSpamAssassinState(GuiceJamesServer jamesServer, SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        SpamAssassinContract.super.expungingSpamMessageShouldNotImpactSpamAssassinState(jamesServer, spamAssassin);
    }

    @Override
    @Tag(Unstable.TAG)
    public void imapMovesToSpamMailboxShouldBeConsideredAsSpam(GuiceJamesServer jamesServer, SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        SpamAssassinContract.super.imapMovesToSpamMailboxShouldBeConsideredAsSpam(jamesServer, spamAssassin);
    }

    @Override
    @Tag(Unstable.TAG)
    public void imapCopiesToSpamMailboxShouldBeConsideredAsSpam(GuiceJamesServer jamesServer, SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        SpamAssassinContract.super.imapCopiesToSpamMailboxShouldBeConsideredAsSpam(jamesServer, spamAssassin);
    }

    @Override
    @Tag(Unstable.TAG)
    public void deletingSpamMessageShouldNotImpactSpamAssassinState(GuiceJamesServer jamesServer, SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        SpamAssassinContract.super.deletingSpamMessageShouldNotImpactSpamAssassinState(jamesServer, spamAssassin);
    }
}
