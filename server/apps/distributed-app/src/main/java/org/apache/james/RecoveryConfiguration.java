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

package org.apache.james;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Configuration for the S3 blob store recovery run.
 *
 * <p>The optional {@code restoreAfter} instant restricts recovery to messages whose {@code Date}
 * header is strictly after the given point in time. It can be provided (highest precedence first) as:</p>
 * <ul>
 *     <li>a {@code --restore-after=<ISO-8601 instant>} program argument</li>
 *     <li>the {@code RESTORE_MESSAGES_AFTER} environment variable</li>
 *     <li>the {@code restore.messages.after} system property</li>
 * </ul>
 */
public record RecoveryConfiguration(Optional<Instant> restoreAfter) {
    private static final String RESTORE_AFTER_ARG = "--restore-after=";
    private static final String RESTORE_AFTER_ENV = "RESTORE_MESSAGES_AFTER";
    private static final String RESTORE_AFTER_PROPERTY = "restore.messages.after";

    public static RecoveryConfiguration parse(String[] args) {
        return new RecoveryConfiguration(restoreAfter(args).map(RecoveryConfiguration::parseInstant));
    }

    private static Optional<String> restoreAfter(String[] args) {
        return Arrays.stream(args)
            .filter(arg -> arg.startsWith(RESTORE_AFTER_ARG))
            .map(arg -> arg.substring(RESTORE_AFTER_ARG.length()))
            .findFirst()
            .or(() -> Optional.ofNullable(System.getenv(RESTORE_AFTER_ENV)))
            .or(() -> Optional.ofNullable(System.getProperty(RESTORE_AFTER_PROPERTY)))
            .map(String::trim)
            .filter(value -> !value.isEmpty());
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid '" + RESTORE_AFTER_ARG + "' value: '" + value
                + "'. Expected an ISO-8601 instant, e.g. 2026-01-01T00:00:00Z", e);
        }
    }
}
