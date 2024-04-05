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
package org.apache.james.jmap.draft.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.model.MessageProperties.MessageProperty;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableList;

public class SetMessagesError extends SetError {

    public static SetMessagesError.Builder builder() {
        return new Builder();
    }
    
    public static class Builder extends SetError.Builder {
        
        private List<BlobId> attachmentsNotFound;

        private Builder() {
            super();
            attachmentsNotFound = new ArrayList<>();
        }

        @Override
        public Builder description(String description) {
            return (Builder) super.description(description);
        }
        
        @Override
        public Builder properties(MessageProperty... properties) {
            return (Builder) super.properties(properties);
        }
        
        @Override
        public Builder properties(Set<MessageProperty> properties) {
            return (Builder) super.properties(properties);
        }
        
        @Override
        public Builder type(Type type) {
            return (Builder) super.type(type);
        }

        public Builder attachmentsNotFound(BlobId... attachmentIds) {
            return attachmentsNotFound(Arrays.asList(attachmentIds));
        }
        
        public Builder attachmentsNotFound(List<BlobId> attachmentIds) {
            this.attachmentsNotFound.addAll(attachmentIds);
            return this;
        }
        
        @Override
        public SetError build() {
            return new SetMessagesError(super.build(), ImmutableList.copyOf(attachmentsNotFound));
        }
    }

    private ImmutableList<BlobId> attachmentsNotFound;
    
    public SetMessagesError(SetError setError, ImmutableList<BlobId> attachmentsNotFound) {
        super(setError);
        this.attachmentsNotFound = attachmentsNotFound;
    }
    
    @JsonSerialize
    public List<BlobId> getAttachmentsNotFound() {
        return attachmentsNotFound;
    }
}
