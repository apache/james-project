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

package org.apache.james.app.spring;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes sure the JMX password file holds a password of this installation: a random one is generated, in a file
 * readable by its owner only, when the file holds none, or still holds the value published with older releases.
 */
public class JmxPasswordFile {
    private static final Logger LOGGER = LoggerFactory.getLogger(JmxPasswordFile.class);
    private static final String USER = "james-admin";
    private static final String PUBLISHED_PASSWORD = "changeme";
    private static final String PASSWORD_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int PASSWORD_LENGTH = 20;
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Like {@link #ensurePassword(Path)}, without failing the start of James: a read only conf directory is logged,
     * and the JMX connector reads the file as it is.
     *
     * @return false when the password could not be ensured
     */
    public static boolean tryEnsurePassword(Path passwordFile) {
        try {
            ensurePassword(passwordFile);
            return true;
        } catch (IOException e) {
            LOGGER.error("Could not generate the JMX password in {}: the JMX connector uses the file as it is", passwordFile, e);
            return false;
        }
    }

    public static void ensurePassword(Path passwordFile) throws IOException {
        List<String> credentials = credentialLines(passwordFile);
        if (credentials.contains(USER + " " + PUBLISHED_PASSWORD)) {
            LOGGER.warn("{} holds the JMX password published with older releases of James: a random one is generated", passwordFile);
        } else if (!credentials.isEmpty()) {
            return;
        }
        // The other lines, comments or credentials of other users, are kept
        List<String> keptLines = Files.exists(passwordFile, LinkOption.NOFOLLOW_LINKS)
            ? Files.readAllLines(passwordFile, StandardCharsets.UTF_8).stream()
                .filter(line -> !isPublishedCredential(line))
                .toList()
            : List.of();
        String content = Stream.concat(keptLines.stream(), Stream.of(USER + " " + randomPassword()))
            .collect(Collectors.joining("\n", "", "\n"));
        Files.deleteIfExists(passwordFile);
        Files.createDirectories(passwordFile.toAbsolutePath().getParent());
        createOwnerOnly(passwordFile);
        try {
            Files.writeString(passwordFile, content, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            Files.deleteIfExists(passwordFile);
            throw e;
        }
        LOGGER.info("Generated the JMX password of {} in {}", USER, passwordFile);
    }

    private static boolean isPublishedCredential(String line) {
        return line.trim().replaceAll("\\s+", " ").equals(USER + " " + PUBLISHED_PASSWORD);
    }

    private static List<String> credentialLines(Path passwordFile) throws IOException {
        if (!Files.exists(passwordFile, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        return Files.readAllLines(passwordFile, StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .map(line -> line.replaceAll("\\s+", " "))
            .toList();
    }

    private static String randomPassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARACTERS.charAt(RANDOM.nextInt(PASSWORD_CHARACTERS.length())));
        }
        return password.toString();
    }

    /**
     * Readable by its owner only where the file system supports POSIX permissions. Elsewhere (Windows), the file gets
     * the access rights of its directory.
     */
    private static void createOwnerOnly(Path file) throws IOException {
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
        } else {
            Files.createFile(file);
        }
    }
}
