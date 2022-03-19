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

package org.apache.james.imap.api.message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import jakarta.mail.Flags;

/**
 * The set of flags associated with a message.
 * jakarta.mail.Flags instead of having our own.
 * 
 * <p>
 * Reference: RFC 2060 - para 2.3
 * </p>
 */
public class MessageFlags {
    public static final String SEEN_OUTPUT_CAPITALISED = "\\Seen";

    public static final String RECENT_OUTPUT_CAPITALISED = "\\Recent";

    public static final String FLAGGED_OUTPUT_CAPITALISED = "\\Flagged";

    public static final String DRAFT_OUTPUT_CAPITALISED = "\\Draft";

    public static final String DELETED_OUTPUT_CAPITALISED = "\\Deleted";

    public static final String ANSWERED_OUTPUT_CAPITALISED = "\\Answered";

    public static final String USER_OUTPUT_CAPITALISED = "\\*";

    
    public static final Flags ALL_FLAGS = new Flags();
    
    static {
        ALL_FLAGS.add(Flags.Flag.ANSWERED);
        ALL_FLAGS.add(Flags.Flag.DELETED);
        ALL_FLAGS.add(Flags.Flag.DRAFT);
        ALL_FLAGS.add(Flags.Flag.FLAGGED);
        ALL_FLAGS.add(Flags.Flag.RECENT);
        ALL_FLAGS.add(Flags.Flag.SEEN);
    }

    public static final String ANSWERED_ALL_CAPS = "\\ANSWERED";

    public static final String DELETED_ALL_CAPS = "\\DELETED";

    public static final String DRAFT_ALL_CAPS = "\\DRAFT";

    public static final String FLAGGED_ALL_CAPS = "\\FLAGGED";

    public static final String SEEN_ALL_CAPS = "\\SEEN";

    public static final String RECENT_ALL_CAPS = "\\RECENT";
    

    /**
     * Returns IMAP formatted String naming flags.
     * 
     * @return <code>Collection</code> of <code>String</code>'s naming the
     *         flags.
     */
    public static Collection<String> names(Flags flags) {
        final Collection<String> results = new ArrayList<>();
        if (flags.contains(Flags.Flag.ANSWERED)) {
            results.add(ANSWERED_OUTPUT_CAPITALISED);
        }
        if (flags.contains(Flags.Flag.DELETED)) {
            results.add(DELETED_OUTPUT_CAPITALISED);
        }
        if (flags.contains(Flags.Flag.DRAFT)) {
            results.add(DRAFT_OUTPUT_CAPITALISED);
        }
        if (flags.contains(Flags.Flag.FLAGGED)) {
            results.add(FLAGGED_OUTPUT_CAPITALISED);
        }
        if (flags.contains(Flags.Flag.RECENT)) {
            results.add(RECENT_OUTPUT_CAPITALISED);
        }
        if (flags.contains(Flags.Flag.SEEN)) {
            results.add(SEEN_OUTPUT_CAPITALISED);
        }
        
        // Add user flags
        String[] userFlags = flags.getUserFlags();
        Collections.addAll(results, userFlags);

        if (flags.contains(Flags.Flag.USER)) {
            results.add(USER_OUTPUT_CAPITALISED);
        }
        return results;
    }
}
