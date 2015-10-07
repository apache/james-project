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

    private long unchangedSince;

    public StoreRequest(final ImapCommand command, final IdRange[] idSet, final boolean silent, final Flags flags, final boolean useUids, final String tag, final Boolean sign, final long unchangedSince) {
        super(tag, command);
        this.idSet = idSet;
        this.silent = silent;
        this.flags = flags;
        this.useUids = useUids;
        if (sign == null) {
            signedMinus = false;
            signedPlus = false;
        } else if (sign.booleanValue()) {
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
        final StringBuffer buffer = new StringBuffer(100);
        buffer.append("STORE ");
        if (isUseUids()) {
            buffer.append("UID ");
        }
        if (isSilent()) {
            buffer.append("SILENT ");

        }
        if (isSignedPlus()) {
            buffer.append("+ ");
        }
        if (isSignedMinus()) {
            buffer.append("- ");
        }
        if (flags.contains(Flags.Flag.ANSWERED)) {
            buffer.append(" ANSWERED");
        }
        if (flags.contains(Flags.Flag.DELETED)) {
            buffer.append(" DELETED");
        }
        if (flags.contains(Flags.Flag.FLAGGED)) {
            buffer.append(" FLAGGED");
        }
        if (flags.contains(Flags.Flag.DRAFT)) {
            buffer.append(" DRAFT");
        }
        if (flags.contains(Flags.Flag.SEEN)) {
            buffer.append(" SEEN");
        }
        if (flags.contains(Flags.Flag.RECENT)) {
            buffer.append(" RECEN");
        }
        return buffer.toString();
    }
}
