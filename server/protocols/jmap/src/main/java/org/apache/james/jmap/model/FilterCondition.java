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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

@JsonDeserialize(builder = FilterCondition.Builder.class)
public class FilterCondition implements Filter {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private Optional<List<String>> inMailboxes;
        private Optional<List<String>> notInMailboxes;
        private ZonedDateTime before;
        private ZonedDateTime after;
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
        private Header header;
        private Optional<String> hasKeyword;
        private Optional<String> notKeyword;

        private Builder() {
            inMailboxes = Optional.empty();
            notInMailboxes = Optional.empty();
            hasKeyword = Optional.empty();
            notKeyword = Optional.empty();
        }

        public Builder inMailboxes(String... inMailboxes) {
            this.inMailboxes = Optional.of(ImmutableList.copyOf(inMailboxes));
            return this;
        }

        @JsonDeserialize
        public Builder inMailboxes(Optional<List<String>> inMailboxes) {
            this.inMailboxes = inMailboxes.map(ImmutableList::copyOf);
            return this;
        }

        public Builder notInMailboxes(String... notInMailboxes) {
            this.notInMailboxes = Optional.of(ImmutableList.copyOf(notInMailboxes));
            return this;
        }

        @JsonDeserialize
        public Builder notInMailboxes(Optional<List<String>> notInMailboxes) {
            this.notInMailboxes = notInMailboxes.map(ImmutableList::copyOf);
            return this;
        }

        @JsonDeserialize
        public Builder hasKeyword(Optional<String> hasKeyword) {
            this.hasKeyword = hasKeyword;
            return this;
        }

        @JsonDeserialize
        public Builder notKeyword(Optional<String> notKeyword) {
            this.notKeyword = notKeyword;
            return this;
        }

        public Builder before(ZonedDateTime before) {
            this.before = before;
            return this;
        }

        public Builder after(ZonedDateTime after) {
            this.after = after;
            return this;
        }

        public Builder minSize(int minSize) {
            this.minSize = minSize;
            return this;
        }

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder isFlagged(boolean isFlagged) {
            this.isFlagged = isFlagged;
            return this;
        }

        public Builder isUnread(boolean isUnread) {
            this.isUnread = isUnread;
            return this;
        }

        public Builder isAnswered(boolean isAnswered) {
            this.isAnswered = isAnswered;
            return this;
        }

        public Builder isDraft(boolean isDraft) {
            this.isDraft = isDraft;
            return this;
        }

        public Builder hasAttachment(boolean hasAttachment) {
            this.hasAttachment = hasAttachment;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder cc(String cc) {
            this.cc = cc;
            return this;
        }

        public Builder bcc(String bcc) {
            this.bcc = bcc;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder header(Header header) {
            this.header = header;
            return this;
        }

        public FilterCondition build() {
            Preconditions.checkArgument(!hasKeyword.isPresent() || (new Keyword(hasKeyword.get()) != null), "hasKeyword is not valid");
            Preconditions.checkArgument(!notKeyword.isPresent() || (new Keyword(notKeyword.get()) != null), "notKeyword is not valid");
            return new FilterCondition(inMailboxes, notInMailboxes, Optional.ofNullable(before), Optional.ofNullable(after), Optional.ofNullable(minSize), Optional.ofNullable(maxSize),
                    Optional.ofNullable(isFlagged), Optional.ofNullable(isUnread), Optional.ofNullable(isAnswered), Optional.ofNullable(isDraft), Optional.ofNullable(hasAttachment),
                    Optional.ofNullable(text), Optional.ofNullable(from), Optional.ofNullable(to), Optional.ofNullable(cc), Optional.ofNullable(bcc), Optional.ofNullable(subject),
                    Optional.ofNullable(body), Optional.ofNullable(header), hasKeyword, notKeyword);
        }
    }

    private final Optional<List<String>> inMailboxes;
    private final Optional<List<String>> notInMailboxes;
    private final Optional<ZonedDateTime> before;
    private final Optional<ZonedDateTime> after;
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
    private final Optional<Header> header;
    private final Optional<String> hasKeyword;
    private final Optional<String> notKeyword;

    @VisibleForTesting FilterCondition(Optional<List<String>> inMailboxes, Optional<List<String>> notInMailboxes, Optional<ZonedDateTime> before, Optional<ZonedDateTime> after, Optional<Integer> minSize, Optional<Integer> maxSize,
                                       Optional<Boolean> isFlagged, Optional<Boolean> isUnread, Optional<Boolean> isAnswered, Optional<Boolean> isDraft, Optional<Boolean> hasAttachment,
                                       Optional<String> text, Optional<String> from, Optional<String> to, Optional<String> cc, Optional<String> bcc, Optional<String> subject,
                                       Optional<String> body, Optional<Header> header, Optional<String> hasKeyword, Optional<String> notKeyword) {

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
        this.hasKeyword = hasKeyword;
        this.notKeyword = notKeyword;
    }

    public Optional<List<String>> getInMailboxes() {
        return inMailboxes;
    }

    public Optional<List<String>> getNotInMailboxes() {
        return notInMailboxes;
    }

    public Optional<ZonedDateTime> getBefore() {
        return before;
    }

    public Optional<ZonedDateTime> getAfter() {
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

    public Optional<Header> getHeader() {
        return header;
    }

    public Optional<String> getHasKeyword() {
        return hasKeyword;
    }

    public Optional<String> getNotKeyword() {
        return notKeyword;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof FilterCondition) {
            FilterCondition other = (FilterCondition) obj;
            return Objects.equals(this.inMailboxes, other.inMailboxes)
                && Objects.equals(this.notInMailboxes, other.notInMailboxes)
                && Objects.equals(this.before, other.before)
                && Objects.equals(this.after, other.after)
                && Objects.equals(this.minSize, other.minSize)
                && Objects.equals(this.maxSize, other.maxSize)
                && Objects.equals(this.isFlagged, other.isFlagged)
                && Objects.equals(this.isUnread, other.isUnread)
                && Objects.equals(this.isAnswered, other.isAnswered)
                && Objects.equals(this.isDraft, other.isDraft)
                && Objects.equals(this.hasAttachment, other.hasAttachment)
                && Objects.equals(this.text, other.text)
                && Objects.equals(this.from, other.from)
                && Objects.equals(this.to, other.to)
                && Objects.equals(this.cc, other.cc)
                && Objects.equals(this.bcc, other.bcc)
                && Objects.equals(this.subject, other.subject)
                && Objects.equals(this.body, other.body)
                && Objects.equals(this.header, other.header)
                && Objects.equals(this.hasKeyword, other.hasKeyword)
                && Objects.equals(this.notKeyword, other.notKeyword);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(inMailboxes, notInMailboxes, before, after, minSize, maxSize, isFlagged, isUnread, isAnswered, isDraft, hasAttachment,
                text, from, to, cc, bcc, subject, body, header, hasKeyword, notKeyword);
    }

    @Override
    public String toString() {
        ToStringHelper helper = MoreObjects.toStringHelper(getClass());
        inMailboxes.ifPresent(x -> helper.add("inMailboxes", x));
        notInMailboxes.ifPresent(x -> helper.add("notInMailboxes", x));
        before.ifPresent(x -> helper.add("before", x));
        after.ifPresent(x -> helper.add("after", x));
        minSize.ifPresent(x -> helper.add("minSize", x));
        maxSize.ifPresent(x -> helper.add("maxSize", x));
        isFlagged.ifPresent(x -> helper.add("isFlagged", x));
        isUnread.ifPresent(x -> helper.add("isUnread", x));
        isAnswered.ifPresent(x -> helper.add("isAnswered", x));
        isDraft.ifPresent(x -> helper.add("isDraft", x));
        hasAttachment.ifPresent(x -> helper.add("hasAttachment", x));
        text.ifPresent(x -> helper.add("text", x));
        from.ifPresent(x -> helper.add("from", x));
        to.ifPresent(x -> helper.add("to", x));
        cc.ifPresent(x -> helper.add("cc", x));
        bcc.ifPresent(x -> helper.add("bcc", x));
        subject.ifPresent(x -> helper.add("subject", x));
        body.ifPresent(x -> helper.add("body", x));
        header.ifPresent(x -> helper.add("header", x));
        hasKeyword.ifPresent(x -> helper.add("hasKeyword", x));
        notKeyword.ifPresent(x -> helper.add("notKeyword", x));
        return helper.toString();
    }

    @Override
    public String prettyPrint(String indentation) {
        return indentation + toString() + "\n";
    }
}
