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
package org.apache.james.jmap.methods.integration;

import javax.mail.MessagingException;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.james.mailbox.spamassassin.SpamAssassinConfiguration;
import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.james.mailetcontainer.impl.MailetConfigImpl;
import org.apache.james.transport.mailets.SpamAssassin;
import org.apache.james.util.Host;
import org.apache.james.utils.ExtendedClassLoader;
import org.apache.james.utils.GuiceGenericLoader;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;

import com.google.inject.Inject;
import com.google.inject.Injector;

public class SpamAssassinGuiceMailetLoader implements MailetLoader {

    private static final String STANDARD_PACKAGE = "org.apache.james.transport.mailets.";

    private final GuiceGenericLoader<Mailet> genericLoader;
    private final SpamAssassinConfiguration spamAssassinConfiguration;

    @Inject
    public SpamAssassinGuiceMailetLoader(Injector injector, ExtendedClassLoader extendedClassLoader, SpamAssassinConfiguration spamAssassinConfiguration) {
        this.genericLoader = new GuiceGenericLoader<>(injector, extendedClassLoader, STANDARD_PACKAGE);
        this.spamAssassinConfiguration = spamAssassinConfiguration;
    }

    @Override
    public Mailet getMailet(MailetConfig config) throws MessagingException {
        String mailetName = config.getMailetName();
        try {
            if (mailetName.equals(SpamAssassin.class.getSimpleName())) {
                return configureSpamAssassinMailet(mailetName);
            }
            Mailet result = genericLoader.instanciate(mailetName);
            result.init(config);
            return result;
        } catch (Exception e) {
            throw new MessagingException("Can not load mailet " + mailetName, e);
        }
    }

    private Mailet configureSpamAssassinMailet(String mailetName) throws Exception, MessagingException {
        Mailet mailet = genericLoader.instanciate(mailetName);
        mailet.init(spamAssassinMailetConfig());
        return mailet;
    }

    private MailetConfigImpl spamAssassinMailetConfig() throws MessagingException {
        BaseConfiguration baseConfiguration = new BaseConfiguration();
        Host host = getHostOrThrow(spamAssassinConfiguration);
        baseConfiguration.addProperty(SpamAssassin.SPAMD_HOST, host.getHostName());
        baseConfiguration.addProperty(SpamAssassin.SPAMD_PORT, host.getPort());

        MailetConfigImpl mailetConfig = new MailetConfigImpl();
        mailetConfig.setConfiguration(baseConfiguration);
        return mailetConfig;
    }

    private Host getHostOrThrow(SpamAssassinConfiguration spamAssassinConfiguration) throws MessagingException {
        return spamAssassinConfiguration.getHost()
                .orElseThrow(() -> new MessagingException("SpamAssassin configuration missing"));
    }

}
