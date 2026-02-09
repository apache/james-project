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

package org.apache.james.mailets.configuration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.protocols.smtp.SMTPConfiguration;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class SmtpConfiguration implements SerializableAsXml {
    public static final boolean AUTH_REQUIRED = true;
    public static final SmtpConfiguration DEFAULT = SmtpConfiguration.builder().build();

    static class HookConfigurationEntry {
        String hookFqcn;
        Map<String, String> hookConfig;

        HookConfigurationEntry(String hookFqcn, Map<String, String> hookConfig) {
            this.hookFqcn = hookFqcn;
            this.hookConfig = hookConfig;
        }

        HookConfigurationEntry(String hookFqcn) {
            this.hookFqcn = hookFqcn;
            this.hookConfig = new HashMap<>();
        }

        private static Map<String, Object> asMustacheScopes(HookConfigurationEntry hook) {
            Map<String, Object> hookScope = new HashMap<>();
            hookScope.put("hookFqcn", hook.hookFqcn);
            hookScope.put("hookConfigAsXML", hook.hookConfig.entrySet());
            return hookScope;
        }
    }

    public static class Builder {
        private static final String DEFAULT_DISABLED = "0";

        private Optional<Boolean> authRequired;
        private Optional<Boolean> startTls;
        private Optional<String> maxMessageSize;
        private Optional<SMTPConfiguration.SenderVerificationMode> verifyIndentity;
        private Optional<Boolean> allowUnauthenticatedSender;
        private Optional<Boolean> bracketEnforcement;
        private Optional<String> authorizedAddresses;
        private final ImmutableList.Builder<HookConfigurationEntry> additionalHooks;

        public Builder() {
            authorizedAddresses = Optional.empty();
            authRequired = Optional.empty();
            startTls = Optional.empty();
            verifyIndentity = Optional.empty();
            maxMessageSize = Optional.empty();
            bracketEnforcement = Optional.empty();
            allowUnauthenticatedSender = Optional.empty();
            additionalHooks = ImmutableList.builder();
        }

        public Builder withAutorizedAddresses(String authorizedAddresses) {
            Preconditions.checkNotNull(authorizedAddresses);
            this.authorizedAddresses = Optional.of(authorizedAddresses);
            return this;
        }

        public Builder withMaxMessageSize(String size) {
            this.maxMessageSize = Optional.of(size);
            return this;
        }

        public Builder requireAuthentication() {
            this.authRequired = Optional.of(AUTH_REQUIRED);
            return this;
        }

        public Builder requireStartTls() {
            this.startTls = Optional.of(true);
            return this;
        }

        public Builder requireBracketEnforcement() {
            this.bracketEnforcement = Optional.of(true);
            return this;
        }

        public Builder doNotRequireBracketEnforcement() {
            this.bracketEnforcement = Optional.of(false);
            return this;
        }

        public Builder verifyIdentity() {
            this.verifyIndentity = Optional.of(SMTPConfiguration.SenderVerificationMode.STRICT);
            return this;
        }

        public Builder forbidUnauthenticatedSenders() {
            this.allowUnauthenticatedSender = Optional.of(false);
            return this;
        }

        public Builder doNotVerifyIdentity() {
            this.verifyIndentity = Optional.of(SMTPConfiguration.SenderVerificationMode.DISABLED);
            return this;
        }

        public Builder relaxedIdentityVerification() {
            this.verifyIndentity = Optional.of(SMTPConfiguration.SenderVerificationMode.RELAXED);
            return this;
        }

        public Builder addHook(String hookFQCN) {
            this.additionalHooks.add(new HookConfigurationEntry(hookFQCN));
            return this;
        }

        public Builder addHook(String hookFQCN, Map<String, String> hookConfig) {
            this.additionalHooks.add(new HookConfigurationEntry(hookFQCN, hookConfig));
            return this;
        }

        public SmtpConfiguration build() {
            return new SmtpConfiguration(authorizedAddresses,
                    authRequired.orElse(!AUTH_REQUIRED),
                    startTls.orElse(false),
                    bracketEnforcement.orElse(true),
                    allowUnauthenticatedSender.orElse(true),
                    verifyIndentity.orElse(SMTPConfiguration.SenderVerificationMode.DISABLED),
                    maxMessageSize.orElse(DEFAULT_DISABLED),
                    additionalHooks.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Optional<String> authorizedAddresses;
    private final boolean authRequired;
    private final boolean startTls;
    private final boolean bracketEnforcement;
    private final boolean allowUnauthenticatedSenders;
    private final SMTPConfiguration.SenderVerificationMode verifyIndentity;
    private final String maxMessageSize;
    private final ImmutableList<HookConfigurationEntry> additionalHooks;

    private SmtpConfiguration(Optional<String> authorizedAddresses,
                              boolean authRequired,
                              boolean startTls,
                              boolean bracketEnforcement,
                              boolean allowUnauthenticatedSenders,
                              SMTPConfiguration.SenderVerificationMode verifyIndentity,
                              String maxMessageSize,
                              ImmutableList<HookConfigurationEntry> additionalHooks) {
        this.authorizedAddresses = authorizedAddresses;
        this.authRequired = authRequired;
        this.bracketEnforcement = bracketEnforcement;
        this.verifyIndentity = verifyIndentity;
        this.allowUnauthenticatedSenders = allowUnauthenticatedSenders;
        this.maxMessageSize = maxMessageSize;
        this.startTls = startTls;
        this.additionalHooks = additionalHooks;
    }

    @Override
    public String serializeAsXml() throws IOException {
        HashMap<String, Object> scopes = new HashMap<>();
        scopes.put("hasAuthorizedAddresses", authorizedAddresses.isPresent());
        authorizedAddresses.ifPresent(value -> scopes.put("authorizedAddresses", value));
        scopes.put("authRequired", authRequired);
        scopes.put("verifyIdentity", verifyIndentity.toString());
        scopes.put("forbidUnauthenticatedSenders", Boolean.toString(!allowUnauthenticatedSenders));
        scopes.put("maxmessagesize", maxMessageSize);
        scopes.put("bracketEnforcement", bracketEnforcement);
        scopes.put("startTls", startTls);

        List<Map<String, Object>> additionalHooksWithConfig = additionalHooks.stream()
                .map(HookConfigurationEntry::asMustacheScopes)
                .collect(ImmutableList.toImmutableList());

        scopes.put("hooks", additionalHooksWithConfig);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(byteArrayOutputStream);
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache = mf.compile(getPatternReader(), "example");
        mustache.execute(writer, scopes);
        writer.flush();
        return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    }

    private StringReader getPatternReader() throws IOException {
        InputStream patternStream = ClassLoader.getSystemResourceAsStream("smtpserver.xml");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        IOUtils.copy(patternStream, byteArrayOutputStream);
        String pattern = byteArrayOutputStream.toString(StandardCharsets.UTF_8);
        return new StringReader(pattern);
    }
}
