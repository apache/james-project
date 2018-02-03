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

package org.apache.james.mailbox.model;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * Represent a Flag update for a message
 * 
 *
 */
public class UpdatedFlags {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MessageUid uid;
        private Flags oldFlags;
        private Flags newFlags;
        private Optional<Long> modSeq = Optional.empty();

        private Builder() {
        }

        public Builder uid(MessageUid uid) {
            this.uid = uid;
            return this;
        }

        public Builder oldFlags(Flags oldFlags) {
            this.oldFlags = oldFlags;
            return this;
        }

        public Builder newFlags(Flags newFlags) {
            this.newFlags = newFlags;
            return this;
        }

        public Builder modSeq(long modSeq) {
            this.modSeq = Optional.of(modSeq);
            return this;
        }

        public UpdatedFlags build() {
            Preconditions.checkState(uid != null);
            Preconditions.checkState(newFlags != null);
            Preconditions.checkState(oldFlags != null);
            Preconditions.checkState(modSeq.isPresent());
            return new UpdatedFlags(uid, modSeq.get(), oldFlags, newFlags);
        }
    }

    private final MessageUid uid;
    private final Flags oldFlags;
    private final Flags newFlags;
    private final Flags modifiedFlags;
    private final long modSeq;

    private UpdatedFlags(MessageUid uid, long modSeq, Flags oldFlags, Flags newFlags) {
       this.uid = uid;
       this.modSeq = modSeq;
       this.oldFlags = oldFlags;
       this.newFlags = newFlags;
       this.modifiedFlags = new Flags();
       addModifiedSystemFlags(oldFlags, newFlags, modifiedFlags);
       addModifiedUserFlags(oldFlags, newFlags, modifiedFlags);
    }
    
    private static void addModifiedSystemFlags(Flags oldFlags, Flags newFlags, Flags modifiedFlags) {
        if (isChanged(oldFlags, newFlags, Flags.Flag.ANSWERED)) {
            modifiedFlags.add(Flags.Flag.ANSWERED);
        }
        if (isChanged(oldFlags, newFlags, Flags.Flag.DELETED)) {
            modifiedFlags.add(Flags.Flag.DELETED);
        }
        if (isChanged(oldFlags, newFlags, Flags.Flag.DRAFT)) {
            modifiedFlags.add(Flags.Flag.DRAFT);
        }
        if (isChanged(oldFlags, newFlags, Flags.Flag.FLAGGED)) {
            modifiedFlags.add(Flags.Flag.FLAGGED);
        }
        if (isChanged(oldFlags, newFlags, Flags.Flag.RECENT)) {
            modifiedFlags.add(Flags.Flag.RECENT);
        }
        if (isChanged(oldFlags, newFlags, Flags.Flag.SEEN)) {
            modifiedFlags.add(Flags.Flag.SEEN);
        }
    }
    
    private static void addModifiedUserFlags(Flags oldFlags, Flags newFlags, Flags modifiedFlags) {
        addModifiedUserFlags(oldFlags, newFlags, oldFlags.getUserFlags(), modifiedFlags);
        addModifiedUserFlags(oldFlags, newFlags, newFlags.getUserFlags(), modifiedFlags);

    }
    

    private static void addModifiedUserFlags(Flags oldFlags, Flags newFlags, String[] userflags, Flags modifiedFlags) {
        for (String userFlag : userflags) {
            if (isChanged(oldFlags, newFlags, userFlag)) {
                modifiedFlags.add(userFlag);
            }
        }
    }
    
    private static boolean isChanged(Flags original, Flags updated, Flags.Flag flag) {
        return original != null && updated != null && (original.contains(flag) ^ updated.contains(flag));
    }

    private static boolean isChanged(Flags original, Flags updated, String userFlag) {
        return original != null && updated != null && (original.contains(userFlag) ^ updated.contains(userFlag));
    }

    
    /**
     * Return the old {@link Flags} for the message
     * 
     * @return oldFlags
     */
    public Flags getOldFlags() {
        return oldFlags;
    }

    public boolean isModifiedToSet(Flags.Flag flag) {
        return newFlags.contains(flag) && !oldFlags.contains(flag);
    }

    public boolean isModifiedToUnset(Flags.Flag flag) {
        return !newFlags.contains(flag) && oldFlags.contains(flag);
    }

    public boolean isUnchanged(Flags.Flag flag) {
        return !isModifiedToSet(flag) && !isModifiedToUnset(flag);
    }

    /**
     * Return the new {@link Flags} for the message
     * 
     * @return newFlags
     */
    public Flags getNewFlags() {
        return newFlags;
    }
    
    /**
     * Return the uid for the message whichs {@link Flags} was updated
     * 
     * @return uid
     */
    public MessageUid getUid() {
        return uid;
    }
   

    /**
     * Gets an iterator for the system flags changed.
     * 
     * @return <code>Flags.Flag</code> <code>Iterator</code>, not null
     */

    public Iterator<Flags.Flag> systemFlagIterator() {
        return Arrays.asList(modifiedFlags.getSystemFlags()).iterator();
    }
    
    /**
     * Gets an iterator for the users flags changed.
     * 
     * @return <code>String</code> <code>Iterator</code>, not null
     */

    public Iterator<String> userFlagIterator() {
        return Arrays.asList(modifiedFlags.getUserFlags()).iterator();
    }
    
    
    /**
     * Return the new mod-sequence for the message
     * 
     * @return mod-seq
     */
    public long getModSeq() {
        return modSeq;
    }
    
    public static boolean flagsChanged(Flags flagsOld, Flags flagsNew) {
        Flags modifiedFlags = new Flags();
        addModifiedSystemFlags(flagsOld, flagsNew, modifiedFlags);
        addModifiedUserFlags(flagsOld, flagsNew, modifiedFlags);
        if (modifiedFlags.getSystemFlags().length > 0 || modifiedFlags.getUserFlags().length > 0) {
            return true;
        } else {
            return false;
        }
    }
    
    public boolean flagsChanged() {
        if (modifiedFlags.getSystemFlags().length > 0 || modifiedFlags.getUserFlags().length > 0) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UpdatedFlags)) {
            return false;
        }

        UpdatedFlags that = (UpdatedFlags) other;

        if (!uid.equals(that.uid)) {
            return false;
        }
        if (modSeq != that.modSeq) {
            return false;
        }
        if (oldFlags != null ? !oldFlags.equals(that.oldFlags) : that.oldFlags != null) {
            return false;
        }
        return !(newFlags != null ? !newFlags.equals(that.newFlags) : that.newFlags != null);

    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uid, oldFlags, newFlags, modSeq);
    }
    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(UpdatedFlags.class)
                .add("uid", uid)
                .add("oldFlags", oldFlags)
                .add("newFlags", newFlags)
                .add("modSeq", modSeq)
                .toString();
    }
}
