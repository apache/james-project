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
    String[] ALL = { ANSWERED, DELETED, DRAFT, RECENT, SEEN, FLAGGED, USER };
    String[] ALL_APPLICABLE_FLAG = { ANSWERED, DELETED, DRAFT, SEEN, FLAGGED };

    ImmutableMap<String, Flags.Flag> JAVAX_MAIL_FLAG = ImmutableMap.<String, Flags.Flag>builder()
        .put(ANSWERED, Flags.Flag.ANSWERED)
        .put(DELETED, Flags.Flag.DELETED)
        .put(DRAFT, Flags.Flag.DRAFT)
        .put(RECENT, Flags.Flag.RECENT)
        .put(SEEN, Flags.Flag.SEEN)
        .put(FLAGGED, Flags.Flag.FLAGGED)
        .put(USER, Flags.Flag.USER)
        .build();

    ImmutableMap<Flags.Flag, String> FLAG_TO_STRING_MAP = ImmutableMap.<Flags.Flag, String>builder()
        .put(Flags.Flag.ANSWERED, ANSWERED)
        .put(Flags.Flag.DELETED, DELETED)
        .put(Flags.Flag.DRAFT, DRAFT)
        .put(Flags.Flag.RECENT, RECENT)
        .put(Flags.Flag.SEEN, SEEN)
        .put(Flags.Flag.FLAGGED, FLAGGED)
        .put(Flags.Flag.USER, USER)
        .build();
}