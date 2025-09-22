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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.Rules;
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

            private Snapshot(long rulesOnMessagesApplySuccessfully, long rulesOnMessagesApplyFailed) {
                this.rulesOnMessagesApplySuccessfully = rulesOnMessagesApplySuccessfully;
                this.rulesOnMessagesApplyFailed = rulesOnMessagesApplyFailed;
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
                        && Objects.equals(this.rulesOnMessagesApplyFailed, that.rulesOnMessagesApplyFailed);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(rulesOnMessagesApplySuccessfully, rulesOnMessagesApplyFailed);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("rulesOnMessagesApplySuccessfully", rulesOnMessagesApplySuccessfully)
                    .add("rulesOnMessagesApplyFailed", rulesOnMessagesApplyFailed)
                    .toString();
            }
        }

        private final AtomicLong rulesOnMessagesApplySuccessfully;
        private final AtomicLong rulesOnMessagesApplyFailed;

        public Context() {
            this.rulesOnMessagesApplySuccessfully = new AtomicLong();
            this.rulesOnMessagesApplyFailed = new AtomicLong();
        }

        public Context(long rulesOnMessagesApplySuccessfully, long rulesOnMessagesApplyFailed) {
            this.rulesOnMessagesApplySuccessfully = new AtomicLong(rulesOnMessagesApplySuccessfully);
            this.rulesOnMessagesApplyFailed = new AtomicLong(rulesOnMessagesApplyFailed);
        }

        public void incrementSuccesses() {
            rulesOnMessagesApplySuccessfully.incrementAndGet();
        }


        public void incrementFails() {
            rulesOnMessagesApplyFailed.incrementAndGet();
        }

        public Context.Snapshot snapshot() {
            return new Context.Snapshot(rulesOnMessagesApplySuccessfully.get(), rulesOnMessagesApplyFailed.get());
        }
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private static AdditionalInformation from(Username username,
                                                                        MailboxName mailboxName,
                                                                        RunRulesOnMailboxTask.Context context) {
            Context.Snapshot snapshot = context.snapshot();
            return new AdditionalInformation(username, mailboxName, Clock.systemUTC().instant(), snapshot.rulesOnMessagesApplySuccessfully, snapshot.rulesOnMessagesApplyFailed);
        }

        private final Username username;
        private final MailboxName mailboxName;
        private final Instant timestamp;
        private final long rulesOnMessagesApplySuccessfully;
        private final long rulesOnMessagesApplyFailed;

        public AdditionalInformation(Username username,
                                     MailboxName mailboxName,
                                     Instant timestamp,
                                     long rulesOnMessagesApplySuccessfully,
                                     long rulesOnMessagesApplyFailed) {
            this.username = username;
            this.mailboxName = mailboxName;
            this.timestamp = timestamp;
            this.rulesOnMessagesApplySuccessfully = rulesOnMessagesApplySuccessfully;
            this.rulesOnMessagesApplyFailed = rulesOnMessagesApplyFailed;
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

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static final TaskType TASK_TYPE = TaskType.of("RunRulesOnMailboxTask");

    private final Context context;
    private final Username username;
    private final MailboxName mailboxName;
    private final Rules rules;
    private final RunRulesOnMailboxService runRulesOnMailboxService;

    public RunRulesOnMailboxTask(Username username,
                                 MailboxName mailboxName,
                                 Rules rules,
                                 RunRulesOnMailboxService runRulesOnMailboxService) {
        this.username = username;
        this.mailboxName = mailboxName;
        this.rules = rules;
        this.runRulesOnMailboxService = runRulesOnMailboxService;
        this.context = new Context();
    }

    @Override
    public Result run() {
        return runRulesOnMailboxService.runRulesOnMailbox(username, mailboxName, rules, context)
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(username, mailboxName, context));
    }

    public Username getUsername() {
        return username;
    }

    public MailboxName getMailboxName() {
        return mailboxName;
    }

    public Rules getRules() {
        return rules;
    }
}
