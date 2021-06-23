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
package org.apache.james.mailbox.cassandra.table;

import java.util.Locale;

import javax.mail.Flags;

import com.google.common.collect.ImmutableMap;

public interface Flag {

    String ANSWERED = "flagAnswered";
    String DELETED = "flagDeleted";
    String DRAFT = "flagDraft";
    String RECENT = "flagRecent";
    String SEEN = "flagSeen";
    String FLAGGED = "flagFlagged";
    String USER = "flagUser";
    String USER_FLAGS = "userFlags";
    String USER_FLAGS_LOWERCASE = USER_FLAGS.toLowerCase(Locale.US);

    String[] ALL_LOWERCASE = {
        ANSWERED.toLowerCase(Locale.US),
        DELETED.toLowerCase(Locale.US),
        DRAFT.toLowerCase(Locale.US),
        RECENT.toLowerCase(Locale.US),
        SEEN.toLowerCase(Locale.US),
        FLAGGED.toLowerCase(Locale.US),
        USER.toLowerCase(Locale.US)
    };

    ImmutableMap<String, Flags.Flag> JAVAX_MAIL_FLAG = ImmutableMap.<String, Flags.Flag>builder()
        .put(ANSWERED.toLowerCase(Locale.US), Flags.Flag.ANSWERED)
        .put(DELETED.toLowerCase(Locale.US), Flags.Flag.DELETED)
        .put(DRAFT.toLowerCase(Locale.US), Flags.Flag.DRAFT)
        .put(RECENT.toLowerCase(Locale.US), Flags.Flag.RECENT)
        .put(SEEN.toLowerCase(Locale.US), Flags.Flag.SEEN)
        .put(FLAGGED.toLowerCase(Locale.US), Flags.Flag.FLAGGED)
        .put(USER.toLowerCase(Locale.US), Flags.Flag.USER)
        .build();
}