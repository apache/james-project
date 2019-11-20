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
package org.apache.james.jmap.draft.methods.integration;

import java.util.Optional;

import javax.inject.Singleton;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.spamassassin.SpamAssassin;
import org.apache.james.mailbox.spamassassin.SpamAssassinConfiguration;
import org.apache.james.mailbox.spamassassin.SpamAssassinListener;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailetcontainer.impl.MailetConfigImpl;
import org.apache.james.spamassassin.SpamAssassinExtension;
import org.apache.james.util.Host;
import org.apache.james.utils.MailetConfigurationOverride;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;

public class SpamAssassinModule extends AbstractModule {

    private final SpamAssassinExtension spamAssassinExtension;

    public SpamAssassinModule(SpamAssassinExtension spamAssassinExtension) {
        this.spamAssassinExtension = spamAssassinExtension;
    }

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), MailetConfigurationOverride.class)
            .addBinding()
            .toInstance(
                new MailetConfigurationOverride(
                    org.apache.james.transport.mailets.SpamAssassin.class,
                    spamAssassinMailetConfig()));
    }

    @Provides
    @Singleton
    private SpamAssassinConfiguration getSpamAssassinConfiguration() {
        SpamAssassinExtension.SpamAssassin spamAssassin = spamAssassinExtension.getSpamAssassin();
        return new SpamAssassinConfiguration(Optional.of(Host.from(spamAssassin.getIp(), spamAssassin.getBindingPort())));
    }

    private MailetConfigImpl spamAssassinMailetConfig() {
        BaseConfiguration baseConfiguration = new BaseConfiguration();
        Host host = Host.from(spamAssassinExtension.getSpamAssassin().getIp(), spamAssassinExtension.getSpamAssassin().getBindingPort());
        baseConfiguration.addProperty(org.apache.james.transport.mailets.SpamAssassin.SPAMD_HOST, host.getHostName());
        baseConfiguration.addProperty(org.apache.james.transport.mailets.SpamAssassin.SPAMD_PORT, host.getPort());

        MailetConfigImpl mailetConfig = new MailetConfigImpl();
        mailetConfig.setConfiguration(baseConfiguration);
        return mailetConfig;
    }

}
