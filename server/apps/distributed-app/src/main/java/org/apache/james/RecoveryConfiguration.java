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
 * <p>The optional {@code family} and {@code generation} narrow the walk to a single generation of the
 * generation aware blob ids, and are pushed down to the object store as a listing prefix rather than
 * filtered client side. Recovering a large deployment is therefore best sharded by generation, one
 * process each. Because the generation is the second component of a blob id, there is no prefix for a
 * generation alone: {@code --generation} requires {@code --family}. They come from
 * {@code --family=<int>} / {@code --generation=<long>}, the {@code RECOVERY_FAMILY} /
 * {@code RECOVERY_GENERATION} environment variables, or the {@code recovery.family} /
 * {@code recovery.generation} system properties, and default to walking the whole bucket.</p>
 *
 * <p>The prefix separator depends on the configured blob id strategy: {@code _} for
 * {@code GenerationAwareBlobId}, {@code /} for {@code MinIOGenerationAwareBlobId}. Deployments using the
 * latter must say so with {@code --minio-separator}, the {@code RECOVERY_MINIO_SEPARATOR} environment
 * variable, or the {@code recovery.minio.separator} system property.</p>
 *
 * <p>The {@code concurrency} controls how many messages are restored in parallel. Since the dominant
 * cost is the per-message work (blob reads plus a full re-store through the mailbox), this is the main
 * lever on recovery wall-clock time. It defaults to {@value #DEFAULT_CONCURRENCY} and can be provided
 * as a {@code --concurrency=<n>} program argument, the {@code RECOVERY_CONCURRENCY} environment
 * variable, or the {@code recovery.concurrency} system property.</p>
 */
public record RecoveryConfiguration(Optional<Instant> restoreAfter, Optional<Integer> family,
                                    Optional<Long> generation, boolean minioSeparator, int concurrency) {
    public static final int DEFAULT_CONCURRENCY = 8;
    private static final String GENERATION_AWARE_SEPARATOR = "_";
    private static final String MINIO_SEPARATOR = "/";
    private static final String RESTORE_AFTER_ARG = "--restore-after=";
    private static final String RESTORE_AFTER_ENV = "RESTORE_MESSAGES_AFTER";
    private static final String RESTORE_AFTER_PROPERTY = "restore.messages.after";
    private static final String FAMILY_ARG = "--family=";
    private static final String FAMILY_ENV = "RECOVERY_FAMILY";
    private static final String FAMILY_PROPERTY = "recovery.family";
    private static final String GENERATION_ARG = "--generation=";
    private static final String GENERATION_ENV = "RECOVERY_GENERATION";
    private static final String GENERATION_PROPERTY = "recovery.generation";
    private static final String MINIO_SEPARATOR_ARG = "--minio-separator";
    private static final String MINIO_SEPARATOR_ENV = "RECOVERY_MINIO_SEPARATOR";
    private static final String MINIO_SEPARATOR_PROPERTY = "recovery.minio.separator";
    private static final String CONCURRENCY_ARG = "--concurrency=";
    private static final String CONCURRENCY_ENV = "RECOVERY_CONCURRENCY";
    private static final String CONCURRENCY_PROPERTY = "recovery.concurrency";

    public RecoveryConfiguration {
        Preconditions.checkArgument(concurrency > 0, "'concurrency' must be strictly positive");
        Preconditions.checkArgument(generation.isEmpty() || family.isPresent(),
            "'" + GENERATION_ARG + "' requires '" + FAMILY_ARG + "': the generation is the second component of a blob id, "
                + "there is no listing prefix for a generation on its own");
    }

    public static RecoveryConfiguration parse(String[] args) {
        return new RecoveryConfiguration(
            option(args, RESTORE_AFTER_ARG, RESTORE_AFTER_ENV, RESTORE_AFTER_PROPERTY).map(RecoveryConfiguration::parseInstant),
            option(args, FAMILY_ARG, FAMILY_ENV, FAMILY_PROPERTY).map(RecoveryConfiguration::parseFamily),
            option(args, GENERATION_ARG, GENERATION_ENV, GENERATION_PROPERTY).map(RecoveryConfiguration::parseGeneration),
            flag(args, MINIO_SEPARATOR_ARG, MINIO_SEPARATOR_ENV, MINIO_SEPARATOR_PROPERTY),
            option(args, CONCURRENCY_ARG, CONCURRENCY_ENV, CONCURRENCY_PROPERTY).map(RecoveryConfiguration::parseConcurrency).orElse(DEFAULT_CONCURRENCY));
    }

    /**
     * The listing prefix restricting the walk to the requested family and generation, empty when the
     * whole bucket is to be walked.
     */
    public String headerBlobPrefix() {
        String separator = minioSeparator ? MINIO_SEPARATOR : GENERATION_AWARE_SEPARATOR;
        return family
            .map(familyValue -> familyValue + separator
                + generation.map(generationValue -> generationValue + separator).orElse(""))
            .orElse("");
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

    private static boolean flag(String[] args, String argName, String envName, String propertyName) {
        return Arrays.asList(args).contains(argName)
            || Optional.ofNullable(System.getenv(envName))
                .or(() -> Optional.ofNullable(System.getProperty(propertyName)))
                .map(String::trim)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid '" + RESTORE_AFTER_ARG + "' value: '" + value
                + "'. Expected an ISO-8601 instant, e.g. 2026-01-01T00:00:00Z", e);
        }
    }

    private static int parseFamily(String value) {
        try {
            int family = Integer.parseInt(value);
            Preconditions.checkArgument(family > 0);
            return family;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid '" + FAMILY_ARG + "' value: '" + value
                + "'. Expected a strictly positive integer", e);
        }
    }

    private static long parseGeneration(String value) {
        try {
            long generation = Long.parseLong(value);
            Preconditions.checkArgument(generation >= 0);
            return generation;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid '" + GENERATION_ARG + "' value: '" + value
                + "'. Expected a non negative integer", e);
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
