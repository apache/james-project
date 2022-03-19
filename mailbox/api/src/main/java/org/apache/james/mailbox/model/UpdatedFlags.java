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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Represent a Flag update for a message
 */
public class UpdatedFlags {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MessageUid uid;
        private Optional<MessageId> messageId = Optional.empty();
        private Flags oldFlags;
        private Flags newFlags;
        private Optional<ModSeq> modSeq = Optional.empty();

        private Builder() {
        }

        public Builder uid(MessageUid uid) {
            this.uid = uid;
            return this;
        }

        public Builder messageId(MessageId messageId) {
            this.messageId = Optional.of(messageId);
            return this;
        }

        public Builder messageId(Optional<MessageId> messageId) {
            this.messageId = messageId;
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

        public Builder modSeq(ModSeq modSeq) {
            this.modSeq = Optional.of(modSeq);
            return this;
        }

        public UpdatedFlags build() {
            Preconditions.checkNotNull(uid);
            Preconditions.checkNotNull(newFlags);
            Preconditions.checkNotNull(oldFlags);
            Preconditions.checkState(modSeq.isPresent());
            return new UpdatedFlags(uid, messageId, modSeq.get(), oldFlags, newFlags);
        }
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

    private final MessageUid uid;
    /**
     * The usage of Optional here is for backward compatibility (to be able to still dequeue older events)
     */
    private final Optional<MessageId> messageId;
    private final Flags oldFlags;
    private final Flags newFlags;
    private final Flags modifiedFlags;
    private final ModSeq modSeq;

    private UpdatedFlags(MessageUid uid, Optional<MessageId> messageId, ModSeq modSeq, Flags oldFlags, Flags newFlags) {
       this.uid = uid;
       this.messageId = messageId;
       this.modSeq = modSeq;
       this.oldFlags = oldFlags;
       this.newFlags = newFlags;
       this.modifiedFlags = new Flags();
       addModifiedSystemFlags(oldFlags, newFlags, modifiedFlags);
       addModifiedUserFlags(oldFlags, newFlags, modifiedFlags);
    }
    
    /**
     * Return the old {@link Flags} for the message
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
        return !isChanged(flag);
    }


    public boolean isChanged(Flags.Flag flag) {
        return isModifiedToSet(flag) || isModifiedToUnset(flag);
    }

    /**
     * Return the new {@link Flags} for the message
     */
    public Flags getNewFlags() {
        return newFlags;
    }
    
    /**
     * Return the uid for the message whichs {@link Flags} was updated
     */
    public MessageUid getUid() {
        return uid;
    }

    /**
     * Return the uid for the message whichs {@link Flags} was updated
     */
    public Optional<MessageId> getMessageId() {
        return messageId;
    }

    /**
     * Gets an iterator for the system flags changed.
     * 
     * @return <code>Flags.Flag</code> <code>Iterator</code>, not null
     */

    public List<Flags.Flag> modifiedSystemFlags() {
        return ImmutableList.copyOf(modifiedFlags.getSystemFlags());
    }

    public List<String> modifiedUserFlags() {
        return ImmutableList.copyOf(modifiedFlags.getUserFlags());
    }
    
    /**
     * Gets an iterator for the users flags changed.
     * 
     * @return <code>String</code> <code>Iterator</code>, not null
     */

    public Iterator<String> userFlagIterator() {
        return Arrays.asList(modifiedFlags.getUserFlags()).iterator();
    }

    public Stream<String> userFlagStream() {
        return Stream.of(modifiedFlags.getUserFlags());
    }

    
    /**
     * Return the new mod-sequence for the message
     */
    public ModSeq getModSeq() {
        return modSeq;
    }
    
    public static boolean flagsChanged(Flags flagsOld, Flags flagsNew) {
        Flags modifiedFlags = new Flags();
        addModifiedSystemFlags(flagsOld, flagsNew, modifiedFlags);
        addModifiedUserFlags(flagsOld, flagsNew, modifiedFlags);
        return modifiedFlags.getSystemFlags().length > 0 || modifiedFlags.getUserFlags().length > 0;
    }

    public boolean flagsChanged() {
        return modifiedFlags.getSystemFlags().length > 0 || modifiedFlags.getUserFlags().length > 0;
    }

    public boolean flagsChangedIgnoringRecent() {
        if (modifiedFlags.contains(Flags.Flag.RECENT)) {
            return modifiedFlags.getSystemFlags().length > 1 || modifiedFlags.getUserFlags().length > 0;
        }
        return modifiedFlags.getSystemFlags().length > 0 || modifiedFlags.getUserFlags().length > 0;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UpdatedFlags)) {
            return false;
        }

        UpdatedFlags that = (UpdatedFlags) other;

        return Objects.equals(uid, that.uid) &&
                Objects.equals(messageId, that.messageId) &&
                Objects.equals(oldFlags, that.oldFlags) &&
                Objects.equals(newFlags, that.newFlags) &&
                Objects.equals(modSeq, that.modSeq);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(uid, messageId, oldFlags, newFlags, modSeq);
    }
    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(UpdatedFlags.class)
            .add("uid", uid)
            .add("messageId", messageId)
            .add("oldFlags", oldFlags)
            .add("newFlags", newFlags)
            .add("modSeq", modSeq)
            .toString();
    }
}
