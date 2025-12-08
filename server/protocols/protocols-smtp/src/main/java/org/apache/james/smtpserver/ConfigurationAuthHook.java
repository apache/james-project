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
package org.apache.james.smtpserver;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.jwt.OidcSASLConfiguration;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.AuthHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;

/**
 * Declarative authentication.
 *
 * This is helpful for things like service accounts, used by other applications (and it is not desirable to create
 * user accounts for those applications)
 */
public class ConfigurationAuthHook implements AuthHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationAuthHook.class);

    private Multimap<Username, String> accounts = ImmutableListMultimap.of();

    @Inject
    public ConfigurationAuthHook() {

    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = (HierarchicalConfiguration<ImmutableNode>) config;

        ImmutableListMultimap.Builder<Username, String> builder = ImmutableListMultimap.builder();

        for (HierarchicalConfiguration<ImmutableNode> accountNode : hierarchicalConfiguration.configurationAt("accounts")
            .configurationsAt("account")) {
            String username = accountNode.getString("username");
            if (username != null) {
                List<String> passwords = accountNode.getList(String.class, "passwords.password");
                passwords.forEach(pw -> builder.put(Username.of(username), pw));
            }
        }
        this.accounts = builder.build();

        LOGGER.info("SMTP authentication enabled from configuration for users: {}", accounts.keySet()
            .stream()
            .map(Username::asString)
            .collect(ImmutableList.toImmutableList()));
    }

    @Override
    public HookResult doAuth(SMTPSession session, Username username, String password) {
        Optional<Username> loggedInUser = Optional.ofNullable(accounts.get(username))
            .filter(allowedsPass -> allowedsPass.stream().anyMatch(password::equals))
            .map(any -> username);

        if (loggedInUser.isPresent()) {
            session.setUsername(loggedInUser.get());
            session.setRelayingAllowed(true);

            return HookResult.builder()
                .hookReturnCode(HookReturnCode.ok())
                .smtpDescription("Authentication Successful")
                .build();
        }
        return HookResult.DECLINED;
    }

    @Override
    public HookResult doSasl(SMTPSession session, OidcSASLConfiguration configuration, String initialResponse) {
        throw new NotImplementedException("No support for OATHBEARER so far");
    }

}
