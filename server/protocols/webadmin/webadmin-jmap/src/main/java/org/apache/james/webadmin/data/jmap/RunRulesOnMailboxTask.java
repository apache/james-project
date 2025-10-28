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

package org.apache.james.webadmin.data.jmap;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.webadmin.validation.MailboxName;

import com.google.common.base.MoreObjects;

public class RunRulesOnMailboxTask implements Task {
    public static class Context {
        public static class Snapshot {
            private final long rulesOnMessagesApplySuccessfully;
            private final long rulesOnMessagesApplyFailed;
            private final boolean maximumAppliedActionExceeded;
            private final long processedMessagesCount;

            private Snapshot(long rulesOnMessagesApplySuccessfully, long rulesOnMessagesApplyFailed, boolean maximumAppliedActionExceeded, long processedMessagesCount) {
                this.rulesOnMessagesApplySuccessfully = rulesOnMessagesApplySuccessfully;
                this.rulesOnMessagesApplyFailed = rulesOnMessagesApplyFailed;
                this.maximumAppliedActionExceeded = maximumAppliedActionExceeded;
                this.processedMessagesCount = processedMessagesCount;
            }

            public long getRulesOnMessagesApplySuccessfully() {
                return rulesOnMessagesApplySuccessfully;
            }

            public long getRulesOnMessagesApplyFailed() {
                return rulesOnMessagesApplyFailed;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Context.Snapshot) {
                    Context.Snapshot that = (Context.Snapshot) o;

                    return Objects.equals(this.rulesOnMessagesApplySuccessfully, that.rulesOnMessagesApplySuccessfully)
                        && Objects.equals(this.rulesOnMessagesApplyFailed, that.rulesOnMessagesApplyFailed)
                        && Objects.equals(this.maximumAppliedActionExceeded, that.maximumAppliedActionExceeded)
                        && Objects.equals(this.processedMessagesCount, that.processedMessagesCount);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(rulesOnMessagesApplySuccessfully, rulesOnMessagesApplyFailed, maximumAppliedActionExceeded,
                    processedMessagesCount);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("rulesOnMessagesApplySuccessfully", rulesOnMessagesApplySuccessfully)
                    .add("rulesOnMessagesApplyFailed", rulesOnMessagesApplyFailed)
                    .add("maximumAppliedActionExceeded", maximumAppliedActionExceeded)
                    .add("processedMessagesCount", processedMessagesCount)
                    .toString();
            }
        }

        private final AtomicLong rulesOnMessagesApplySuccessfully;
        private final AtomicLong rulesOnMessagesApplyFailed;
        private final AtomicBoolean maximumAppliedActionExceeded;
        private final AtomicLong processedMessagesCount;

        public Context() {
            this.rulesOnMessagesApplySuccessfully = new AtomicLong();
            this.rulesOnMessagesApplyFailed = new AtomicLong();
            this.maximumAppliedActionExceeded = new AtomicBoolean();
            this.processedMessagesCount = new AtomicLong();
        }

        public Context(long rulesOnMessagesApplySuccessfully, long rulesOnMessagesApplyFailed, boolean maximumAppliedActionExceeded,
                       long processedMessagesCount) {
            this.rulesOnMessagesApplySuccessfully = new AtomicLong(rulesOnMessagesApplySuccessfully);
            this.rulesOnMessagesApplyFailed = new AtomicLong(rulesOnMessagesApplyFailed);
            this.maximumAppliedActionExceeded = new AtomicBoolean(maximumAppliedActionExceeded);
            this.processedMessagesCount = new AtomicLong(processedMessagesCount);
        }

        public void incrementSuccesses() {
            rulesOnMessagesApplySuccessfully.incrementAndGet();
        }

        public void incrementFails() {
            rulesOnMessagesApplyFailed.incrementAndGet();
        }

        public void setMaximumAppliedActionExceeded() {
            maximumAppliedActionExceeded.set(true);
        }

        public void increaseProcessedMessagesCount() {
            processedMessagesCount.incrementAndGet();
        }

        public Context.Snapshot snapshot() {
            return new Context.Snapshot(rulesOnMessagesApplySuccessfully.get(), rulesOnMessagesApplyFailed.get(), maximumAppliedActionExceeded.get(),
                processedMessagesCount.get());
        }
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private static AdditionalInformation from(Username username,
                                                  MailboxName mailboxName,
                                                  RunRulesOnMailboxTask.Context context) {
            Context.Snapshot snapshot = context.snapshot();
            return new AdditionalInformation(username, mailboxName, Clock.systemUTC().instant(), snapshot.rulesOnMessagesApplySuccessfully,
                snapshot.rulesOnMessagesApplyFailed, snapshot.maximumAppliedActionExceeded, snapshot.processedMessagesCount);
        }

        private final Username username;
        private final MailboxName mailboxName;
        private final Instant timestamp;
        private final long rulesOnMessagesApplySuccessfully;
        private final long rulesOnMessagesApplyFailed;
        private final boolean maximumAppliedActionExceeded;
        private final long processedMessagesCount;

        public AdditionalInformation(Username username,
                                     MailboxName mailboxName,
                                     Instant timestamp,
                                     long rulesOnMessagesApplySuccessfully,
                                     long rulesOnMessagesApplyFailed,
                                     boolean maximumAppliedActionExceeded,
                                     long processedMessagesCount) {
            this.username = username;
            this.mailboxName = mailboxName;
            this.timestamp = timestamp;
            this.rulesOnMessagesApplySuccessfully = rulesOnMessagesApplySuccessfully;
            this.rulesOnMessagesApplyFailed = rulesOnMessagesApplyFailed;
            this.maximumAppliedActionExceeded = maximumAppliedActionExceeded;
            this.processedMessagesCount = processedMessagesCount;
        }

        public Username getUsername() {
            return username;
        }

        public MailboxName getMailboxName() {
            return mailboxName;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public long getRulesOnMessagesApplySuccessfully() {
            return rulesOnMessagesApplySuccessfully;
        }

        public long getRulesOnMessagesApplyFailed() {
            return rulesOnMessagesApplyFailed;
        }

        public boolean maximumAppliedActionExceeded() {
            return maximumAppliedActionExceeded;
        }

        public long getProcessedMessagesCount() {
            return processedMessagesCount;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static final TaskType TASK_TYPE = TaskType.of("RunRulesOnMailboxTask");

    private final Context context;
    private final Username username;
    private final MailboxPath mailboxPath;
    private final Rules rules;
    private final RunRulesOnMailboxService runRulesOnMailboxService;

    public RunRulesOnMailboxTask(Username username,
                                 MailboxPath mailboxPath,
                                 Rules rules,
                                 RunRulesOnMailboxService runRulesOnMailboxService) {
        this.username = username;
        this.mailboxPath = mailboxPath;
        this.rules = rules;
        this.runRulesOnMailboxService = runRulesOnMailboxService;
        this.context = new Context();
    }

    public RunRulesOnMailboxTask(Username username,
                                 MailboxName mailboxName,
                                 Rules rules,
                                 RunRulesOnMailboxService runRulesOnMailboxService) {
        this(username, MailboxPath.forUser(username, mailboxName.asString()), rules, runRulesOnMailboxService);
    }

    @Override
    public Result run() {
        return runRulesOnMailboxService.runRulesOnMailbox(username, mailboxPath, rules, context)
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(username, new MailboxName(mailboxPath.getName()), context));
    }

    public Username getUsername() {
        return username;
    }

    public MailboxPath getMailboxPath() {
        return mailboxPath;
    }

    public Rules getRules() {
        return rules;
    }
}
