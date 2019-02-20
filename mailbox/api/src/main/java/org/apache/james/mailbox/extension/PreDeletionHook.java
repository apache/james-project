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

package org.apache.james.mailbox.extension;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.james.mailbox.MetadataWithMailboxId;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;

public interface PreDeletionHook {

    class DeletionId {

        public static DeletionId random() {
            return new DeletionId(UUID.randomUUID());
        }

        public static DeletionId of(UUID uuid) {
            return new DeletionId(uuid);
        }

        private final UUID id;

        private DeletionId(UUID id) {
            this.id = id;
        }

        public String asString() {
            return id.toString();
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof DeletionId) {
                DeletionId that = (DeletionId) o;
                return Objects.equals(this.id, that.id);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(id);
        }
    }

    class DeleteOperation {

        public static DeleteOperation from(List<MetadataWithMailboxId> deletionMetadataList) {
            return new DeleteOperation(DeletionId.random(), deletionMetadataList);
        }

        private final DeletionId deletionId;
        private final List<MetadataWithMailboxId> deletionMetadataList;

        private DeleteOperation(DeletionId deletionId, List<MetadataWithMailboxId> deletionMetadataList) {
            this.deletionId = deletionId;
            this.deletionMetadataList = deletionMetadataList;
        }

        public DeletionId getDeletionId() {
            return deletionId;
        }

        public List<MetadataWithMailboxId> getDeletionMetadataList() {
            return deletionMetadataList;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof DeleteOperation) {
                DeleteOperation that = (DeleteOperation) o;

                return Objects.equals(this.deletionId, that.deletionId)
                    && Objects.equals(this.deletionMetadataList, that.deletionMetadataList);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(deletionId, deletionMetadataList);
        }
    }

    Set<PreDeletionHook> NO_PRE_DELETION_HOOK = ImmutableSet.of();

    Publisher<Void> notifyDelete(DeleteOperation deleteOperation);
}
