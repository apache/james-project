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
package org.apache.james.smtpserver.futurerelease;

import static org.apache.james.smtpserver.futurerelease.FutureReleaseParameters.MAX_HOLD_FOR_SUPPORTED;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;

import com.google.common.collect.ImmutableSet;

public class FutureReleaseEHLOHook implements HeloHook {
    private final Clock clock;

    @Inject
    public FutureReleaseEHLOHook(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Set<String> implementedEsmtpFeatures(SMTPSession session) {
        if (session.getUsername() != null) {
            Instant now = LocalDateTime.now(clock).toInstant(ZoneOffset.UTC);
            String dateAsString = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC")).format(now.plus(MAX_HOLD_FOR_SUPPORTED));

            return ImmutableSet.of("FUTURERELEASE " + MAX_HOLD_FOR_SUPPORTED.toSeconds() + " " + dateAsString);
        }
        return ImmutableSet.of();
    }

    @Override
    public HookResult doHelo(SMTPSession session, String helo) {
        return HookResult.DECLINED;
    }
}

