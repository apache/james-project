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

import com.google.common.base.Preconditions;

/**
 * Configuration for the S3 blob store recovery run.
 *
 * <p>The optional {@code restoreAfter} instant restricts recovery to messages whose {@code Date}
 * header is strictly after the given point in time. It can be provided (highest precedence first) as a
 * {@code --restore-after=<ISO-8601 instant>} program argument, the {@code RESTORE_MESSAGES_AFTER}
 * environment variable, or the {@code restore.messages.after} system property.</p>
 *
 * <p>The optional {@code headerBlobPrefix} narrows the walk to the recovery sidecars whose header blob
 * id starts with the given prefix. Because header blob ids are generation-aware
 * ({@code family_generation_...}), a clever admin can pass e.g. {@code 1_42_} to iterate solely the
 * latest generation instead of scanning the whole bucket. It defaults to the empty string (all
 * recovery sidecars) and can be provided as a {@code --header-blob-prefix=<prefix>} program argument,
 * the {@code RECOVERY_HEADER_BLOB_PREFIX} environment variable, or the {@code recovery.header.blob.prefix}
 * system property.</p>
 *
 * <p>The {@code concurrency} controls how many messages are restored in parallel. Since the dominant
 * cost is the per-message work (blob reads plus a full re-store through the mailbox), this is the main
 * lever on recovery wall-clock time. It defaults to {@value #DEFAULT_CONCURRENCY} and can be provided
 * as a {@code --concurrency=<n>} program argument, the {@code RECOVERY_CONCURRENCY} environment
 * variable, or the {@code recovery.concurrency} system property.</p>
 */
public record RecoveryConfiguration(Optional<Instant> restoreAfter, String headerBlobPrefix, int concurrency) {
    public static final int DEFAULT_CONCURRENCY = 8;
    private static final String RESTORE_AFTER_ARG = "--restore-after=";
    private static final String RESTORE_AFTER_ENV = "RESTORE_MESSAGES_AFTER";
    private static final String RESTORE_AFTER_PROPERTY = "restore.messages.after";
    private static final String HEADER_BLOB_PREFIX_ARG = "--header-blob-prefix=";
    private static final String HEADER_BLOB_PREFIX_ENV = "RECOVERY_HEADER_BLOB_PREFIX";
    private static final String HEADER_BLOB_PREFIX_PROPERTY = "recovery.header.blob.prefix";
    private static final String CONCURRENCY_ARG = "--concurrency=";
    private static final String CONCURRENCY_ENV = "RECOVERY_CONCURRENCY";
    private static final String CONCURRENCY_PROPERTY = "recovery.concurrency";

    public RecoveryConfiguration {
        Preconditions.checkArgument(concurrency > 0, "'concurrency' must be strictly positive");
    }

    public static RecoveryConfiguration parse(String[] args) {
        return new RecoveryConfiguration(
            option(args, RESTORE_AFTER_ARG, RESTORE_AFTER_ENV, RESTORE_AFTER_PROPERTY).map(RecoveryConfiguration::parseInstant),
            option(args, HEADER_BLOB_PREFIX_ARG, HEADER_BLOB_PREFIX_ENV, HEADER_BLOB_PREFIX_PROPERTY).orElse(""),
            option(args, CONCURRENCY_ARG, CONCURRENCY_ENV, CONCURRENCY_PROPERTY).map(RecoveryConfiguration::parseConcurrency).orElse(DEFAULT_CONCURRENCY));
    }

    private static Optional<String> option(String[] args, String argPrefix, String envName, String propertyName) {
        return Arrays.stream(args)
            .filter(arg -> arg.startsWith(argPrefix))
            .map(arg -> arg.substring(argPrefix.length()))
            .findFirst()
            .or(() -> Optional.ofNullable(System.getenv(envName)))
            .or(() -> Optional.ofNullable(System.getProperty(propertyName)))
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

    private static int parseConcurrency(String value) {
        try {
            int concurrency = Integer.parseInt(value);
            Preconditions.checkArgument(concurrency > 0);
            return concurrency;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid '" + CONCURRENCY_ARG + "' value: '" + value
                + "'. Expected a strictly positive integer", e);
        }
    }
}
