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

package org.apache.james.mailbox.indexer;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Task;

public interface ReIndexer {

    class RunningOptions {
        public static class Builder {
            private Optional<Integer> messagesPerSecond;
            private Optional<Mode> mode;

            public Builder() {
                this.messagesPerSecond = Optional.empty();
                this.mode = Optional.empty();
            }

            public Builder messagesPerSeconds(Optional<Integer> messagesPerSecond) {
                this.messagesPerSecond = messagesPerSecond;
                return this;
            }

            public Builder mode(Optional<Mode> mode) {
                this.mode = mode;
                return this;
            }

            public Builder mode(Mode mode) {
                return mode(Optional.of(mode));
            }

            public RunningOptions build() {
                return new RunningOptions(
                    messagesPerSecond.orElse(DEFAULT_MESSAGES_PER_SECONDS),
                    mode.orElse(DEFAULT_MODE)
                );
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public enum Mode {
            REBUILD_ALL("rebuildAll"),
            REBUILD_ALL_NO_CLEANUP("rebuildAllNoCleanup"),
            FIX_OUTDATED("fixOutdated");

            private final String value;

            Mode(String value) {
                this.value = value;
            }

            String getValue() {
                return value;
            }

            static Optional<Mode> fromString(String optionalMode) {
                return Stream.of(values())
                    .filter(mode -> mode.getValue().equalsIgnoreCase(optionalMode))
                    .findFirst();
            }
        }

        public static Optional<Mode> parseMode(String optionalMode) {
            return Optional.ofNullable(optionalMode)
                .flatMap(Mode::fromString);
        }

        private static final Mode DEFAULT_MODE = Mode.REBUILD_ALL;
        private static final int DEFAULT_MESSAGES_PER_SECONDS = 50;

        public static final RunningOptions DEFAULT = builder().build();

        private final int messagesPerSecond;
        private final Mode mode;

        private RunningOptions(int messagesPerSecond, Mode mode) {
            this.messagesPerSecond = messagesPerSecond;
            this.mode = mode;
        }

        public int getMessagesPerSecond() {
            return messagesPerSecond;
        }

        public Mode getMode() {
            return mode;
        }
    }

    Task reIndex(Username username, RunningOptions runningOptions) throws MailboxException;

    Task reIndex(MailboxPath path, RunningOptions runningOptions) throws MailboxException;

    Task reIndex(MailboxId mailboxId, RunningOptions runningOptions) throws MailboxException;

    Task reIndex(RunningOptions runningOptions) throws MailboxException;

    Task reIndex(MailboxPath path, MessageUid uid) throws MailboxException;

    Task reIndex(MailboxId mailboxId, MessageUid uid) throws MailboxException;

    Task reIndex(ReIndexingExecutionFailures previousFailures, RunningOptions runningOptions) throws MailboxException;

}
