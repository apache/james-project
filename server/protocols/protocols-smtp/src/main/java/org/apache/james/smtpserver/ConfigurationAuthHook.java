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
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.AuthHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * Declarative authentication.
 *
 * <p>Each {@code account} supports an optional {@code allowUseOtherIdentity} flag (defaults to {@code false}).
 * When set, the authenticated session bypasses the {@code verifyIdentity} checks and may thus use any
 * MAIL FROM / From identity. This is intended for application accounts sending on behalf of end users
 * (calendar invitations, notifications...), and should be granted only to accounts whose credentials are
 * under the operator control.</p>
 *
 * @deprecated Prefer implementing a SASL mechanism factory. Existing handler-chain registrations
 * are adapted by the SMTP AUTH handler during migration.
 */
@Deprecated
public class ConfigurationAuthHook implements AuthHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationAuthHook.class);

    private record Account(Username username, List<String> passwords, boolean allowUseOtherIdentity) {
        boolean matches(Username username, String password) {
            return this.username.equals(username) && passwords.stream().anyMatch(password::equals);
        }
    }

    private List<Account> accounts = ImmutableList.of();

    @Inject
    public ConfigurationAuthHook() {

    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = (HierarchicalConfiguration<ImmutableNode>) config;

        this.accounts = hierarchicalConfiguration.configurationAt("accounts")
            .configurationsAt("account")
            .stream()
            .flatMap(accountNode -> parseAccount(accountNode).stream())
            .collect(ImmutableList.toImmutableList());

        LOGGER.info("SMTP authentication enabled from configuration for users: {}", accounts.stream()
            .map(account -> account.username().asString())
            .collect(ImmutableList.toImmutableList()));
    }

    private Optional<Account> parseAccount(HierarchicalConfiguration<ImmutableNode> accountNode) {
        return Optional.ofNullable(accountNode.getString("username"))
            .map(username -> new Account(Username.of(username),
                accountNode.getList(String.class, "passwords.password", ImmutableList.of()),
                accountNode.getBoolean("allowUseOtherIdentity", false)));
    }

    @Override
    public HookResult doAuth(SMTPSession session, Username username, String password) {
        return accounts.stream()
            .filter(account -> account.matches(username, password))
            .findFirst()
            .map(account -> authenticate(session, account))
            .orElse(HookResult.DECLINED);
    }

    private HookResult authenticate(SMTPSession session, Account account) {
        session.setUsername(account.username());
        session.setRelayingAllowed(true);
        if (account.allowUseOtherIdentity()) {
            session.setAttachment(SMTPSession.ALLOW_USE_OTHER_IDENTITY, true, ProtocolSession.State.Connection);
        }

        return HookResult.builder()
            .hookReturnCode(HookReturnCode.ok())
            .smtpDescription("Authentication Successful")
            .build();
    }

    @Override
    public HookResult doSasl(SMTPSession session, OidcSASLConfiguration configuration, String initialResponse) {
        throw new NotImplementedException("No support for OATHBEARER so far");
    }

}
