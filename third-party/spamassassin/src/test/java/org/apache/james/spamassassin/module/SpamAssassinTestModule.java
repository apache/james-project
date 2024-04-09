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

package org.apache.james.spamassassin.module;

import jakarta.inject.Singleton;

import org.apache.james.jmap.event.PopulateEmailQueryViewListener;
import org.apache.james.modules.mailbox.ListenerConfiguration;
import org.apache.james.modules.mailbox.ListenersConfiguration;
import org.apache.james.spamassassin.SpamAssassinConfiguration;
import org.apache.james.spamassassin.SpamAssassinExtension;
import org.apache.james.spamassassin.SpamAssassinListener;
import org.apache.james.util.Host;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class SpamAssassinTestModule extends AbstractModule {

    private final SpamAssassinExtension spamAssassinExtension;

    public SpamAssassinTestModule(SpamAssassinExtension spamAssassinExtension) {
        this.spamAssassinExtension = spamAssassinExtension;
    }

    @Override
    protected void configure() {
        bind(ListenersConfiguration.class)
            .toInstance(ListenersConfiguration.of(
                ListenerConfiguration.forClass(SpamAssassinListener.class.getCanonicalName()),
                ListenerConfiguration.forClass(PopulateEmailQueryViewListener.class.getCanonicalName())));
    }

    @Provides
    @Singleton
    private SpamAssassinConfiguration getSpamAssassinConfiguration() {
        SpamAssassinExtension.SpamAssassin spamAssassin = spamAssassinExtension.getSpamAssassin();
        return new SpamAssassinConfiguration(Host.from(spamAssassin.getIp(), spamAssassin.getBindingPort()));
    }

}
