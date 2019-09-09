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
package org.apache.james.imap.message.request;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.message.IdRange;

public class StoreRequest extends AbstractImapRequest {

    private final IdRange[] idSet;

    private final Flags flags;

    private final boolean useUids;

    private final boolean silent;

    private final boolean signedMinus;

    private final boolean signedPlus;

    private final long unchangedSince;

    public StoreRequest(ImapCommand command, IdRange[] idSet, boolean silent, Flags flags, boolean useUids, String tag, Boolean sign, long unchangedSince) {
        super(tag, command);
        this.idSet = idSet;
        this.silent = silent;
        this.flags = flags;
        this.useUids = useUids;
        if (sign == null) {
            signedMinus = false;
            signedPlus = false;
        } else if (sign) {
            signedMinus = false;
            signedPlus = true;
        } else {
            signedMinus = true;
            signedPlus = false;
        }
        this.unchangedSince = unchangedSince;
    }

    /**
     * Is this store silent?
     * 
     * @return true if store silent, false otherwise
     */
    public final boolean isSilent() {
        return silent;
    }

    /**
     * Is the store signed MINUS? Note that {@link #isSignedPlus()} must be
     * false when this property is true.
     * 
     * @return true if the store is subtractive
     */
    public final boolean isSignedMinus() {
        return signedMinus;
    }

    /**
     * Is the store signed PLUS? Note that {@link #isSignedMinus()} must be
     * false when this property is true.
     * 
     * @return true if the store is additive
     */
    public final boolean isSignedPlus() {
        return signedPlus;
    }

    public final Flags getFlags() {
        return flags;
    }

    public final IdRange[] getIdSet() {
        return idSet;
    }

    public final boolean isUseUids() {
        return useUids;
    }
    
    public final long getUnchangedSince() {
        return unchangedSince;
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder(100);
        builder.append("STORE ");
        if (isUseUids()) {
            builder.append("UID ");
        }
        if (isSilent()) {
            builder.append("SILENT ");

        }
        if (isSignedPlus()) {
            builder.append("+ ");
        }
        if (isSignedMinus()) {
            builder.append("- ");
        }
        if (flags.contains(Flags.Flag.ANSWERED)) {
            builder.append(" ANSWERED");
        }
        if (flags.contains(Flags.Flag.DELETED)) {
            builder.append(" DELETED");
        }
        if (flags.contains(Flags.Flag.FLAGGED)) {
            builder.append(" FLAGGED");
        }
        if (flags.contains(Flags.Flag.DRAFT)) {
            builder.append(" DRAFT");
        }
        if (flags.contains(Flags.Flag.SEEN)) {
            builder.append(" SEEN");
        }
        if (flags.contains(Flags.Flag.RECENT)) {
            builder.append(" RECEN");
        }
        return builder.toString();
    }
}
