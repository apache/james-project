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

package org.apache.james.jmap.model;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang.NotImplementedException;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

@JsonDeserialize(builder = FilterCondition.Builder.class)
public class FilterCondition implements Filter {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private final ImmutableList.Builder<String> inMailboxes;
        private final ImmutableList.Builder<String> notInMailboxes;
        private Date before;
        private Date after;
        private Integer minSize;
        private Integer maxSize;
        private Boolean isFlagged;
        private Boolean isUnread;
        private Boolean isAnswered;
        private Boolean isDraft;
        private Boolean hasAttachment;
        private String text;
        private String from;
        private String to;
        private String cc;
        private String bcc;
        private String subject;
        private String body;
        private final ImmutableList.Builder<String> header;

        private Builder() {
            inMailboxes = ImmutableList.builder();
            notInMailboxes = ImmutableList.builder();
            header = ImmutableList.builder();
        }

        public Builder inMailboxes(List<String> inMailboxes) {
            this.inMailboxes.addAll(inMailboxes);
            return this;
        }

        public Builder notInMailboxes(List<Filter> notInMailboxes) {
            throw new NotImplementedException();
        }

        public Builder before(Date before) {
            throw new NotImplementedException();
        }

        public Builder after(Date after) {
            throw new NotImplementedException();
        }

        public Builder minSize(int minSize) {
            throw new NotImplementedException();
        }

        public Builder maxSize(int maxSize) {
            throw new NotImplementedException();
        }

        public Builder isFlagged(boolean isFlagger) {
            throw new NotImplementedException();
        }

        public Builder isUnread(boolean isUnread) {
            throw new NotImplementedException();
        }

        public Builder isAnswered(boolean isAnswered) {
            throw new NotImplementedException();
        }

        public Builder isDraft(boolean isDraft) {
            throw new NotImplementedException();
        }

        public Builder hasAttachment(boolean hasAttachment) {
            throw new NotImplementedException();
        }

        public Builder text(String text) {
            throw new NotImplementedException();
        }

        public Builder from(String from) {
            throw new NotImplementedException();
        }

        public Builder to(String to) {
            throw new NotImplementedException();
        }

        public Builder cc(String cc) {
            throw new NotImplementedException();
        }

        public Builder bcc(String bcc) {
            throw new NotImplementedException();
        }

        public Builder subject(String subject) {
            throw new NotImplementedException();
        }

        public Builder body(String body) {
            throw new NotImplementedException();
        }

        public Builder header(List<String> body) {
            throw new NotImplementedException();
        }

        public FilterCondition build() {
            return new FilterCondition(inMailboxes.build(), notInMailboxes.build(), Optional.ofNullable(before), Optional.ofNullable(after), Optional.ofNullable(minSize), Optional.ofNullable(maxSize),
                    Optional.ofNullable(isFlagged), Optional.ofNullable(isUnread), Optional.ofNullable(isAnswered), Optional.ofNullable(isDraft), Optional.ofNullable(hasAttachment),
                    Optional.ofNullable(text), Optional.ofNullable(from), Optional.ofNullable(to), Optional.ofNullable(cc), Optional.ofNullable(bcc), Optional.ofNullable(subject), Optional.ofNullable(body), header.build());
        }
    }

    private final List<String> inMailboxes;
    private final List<String> notInMailboxes;
    private final Optional<Date> before;
    private final Optional<Date> after;
    private final Optional<Integer> minSize;
    private final Optional<Integer> maxSize;
    private final Optional<Boolean> isFlagged;
    private final Optional<Boolean> isUnread;
    private final Optional<Boolean> isAnswered;
    private final Optional<Boolean> isDraft;
    private final Optional<Boolean> hasAttachment;
    private final Optional<String> text;
    private final Optional<String> from;
    private final Optional<String> to;
    private final Optional<String> cc;
    private final Optional<String> bcc;
    private final Optional<String> subject;
    private final Optional<String> body;
    private final List<String> header;

    @VisibleForTesting FilterCondition(List<String> inMailboxes, List<String> notInMailboxes, Optional<Date> before, Optional<Date> after, Optional<Integer> minSize, Optional<Integer> maxSize,
            Optional<Boolean> isFlagged, Optional<Boolean> isUnread, Optional<Boolean> isAnswered, Optional<Boolean> isDraft, Optional<Boolean> hasAttachment,
            Optional<String> text, Optional<String> from, Optional<String> to, Optional<String> cc, Optional<String> bcc, Optional<String> subject, Optional<String> body, List<String> header) {

        this.inMailboxes = inMailboxes;
        this.notInMailboxes = notInMailboxes;
        this.before = before;
        this.after = after;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.isFlagged = isFlagged;
        this.isUnread = isUnread;
        this.isAnswered = isAnswered;
        this.isDraft = isDraft;
        this.hasAttachment = hasAttachment;
        this.text = text;
        this.from = from;
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.subject = subject;
        this.body = body;
        this.header = header;
    }

    public List<String> getInMailboxes() {
        return inMailboxes;
    }

    public List<String> getNotInMailboxes() {
        return notInMailboxes;
    }

    public Optional<Date> getBefore() {
        return before;
    }

    public Optional<Date> getAfter() {
        return after;
    }

    public Optional<Integer> getMinSize() {
        return minSize;
    }

    public Optional<Integer> getMaxSize() {
        return maxSize;
    }

    public Optional<Boolean> getIsFlagged() {
        return isFlagged;
    }

    public Optional<Boolean> getIsUnread() {
        return isUnread;
    }

    public Optional<Boolean> getIsAnswered() {
        return isAnswered;
    }

    public Optional<Boolean> getIsDraft() {
        return isDraft;
    }

    public Optional<Boolean> getHasAttachment() {
        return hasAttachment;
    }

    public Optional<String> getText() {
        return text;
    }

    public Optional<String> getFrom() {
        return from;
    }

    public Optional<String> getTo() {
        return to;
    }

    public Optional<String> getCc() {
        return cc;
    }

    public Optional<String> getBcc() {
        return bcc;
    }

    public Optional<String> getSubject() {
        return subject;
    }

    public Optional<String> getBody() {
        return body;
    }

    public List<String> getHeader() {
        return header;
    }
}
