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

package org.apache.james.mailbox.tika;

import java.util.List;
import java.util.Objects;

import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.james.util.StreamUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class TextExtractorConfiguration {

    private static final String TEXT_EXTRACTOR_CONTENT_TYPE_BLACKLIST = "textextractor.contentType.blacklist";

    public static class Builder {
        private ImmutableList.Builder<String> contentTypeBlacklist;

        public Builder contentTypeBlacklist(List<String> contentTypeBlacklist) {
            Preconditions.checkNotNull(contentTypeBlacklist);
            this.contentTypeBlacklist.addAll(contentTypeBlacklist);
            return this;
        }

        private Builder() {
            contentTypeBlacklist = ImmutableList.builder();
        }

        public TextExtractorConfiguration build() {
            return new TextExtractorConfiguration(contentTypeBlacklist.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final ImmutableList<String> contentTypeBlacklist;

    public TextExtractorConfiguration(ImmutableList<String> contentTypeBlacklist) {
        this.contentTypeBlacklist = contentTypeBlacklist;
    }

    public ImmutableList<String> getContentTypeBlacklist() {
        return contentTypeBlacklist;
    }

    public static TextExtractorConfiguration readTextExtractorConfiguration(Configuration configuration) {
        AbstractConfiguration.setDefaultListDelimiter(',');

        List<String> contentTypeBlacklist = StreamUtils
            .ofNullable(configuration.getStringArray(TEXT_EXTRACTOR_CONTENT_TYPE_BLACKLIST))
            .map(String::trim)
            .collect(ImmutableList.toImmutableList());

        return TextExtractorConfiguration.builder()
            .contentTypeBlacklist(contentTypeBlacklist)
            .build();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof  TextExtractorConfiguration) {
            TextExtractorConfiguration that = (TextExtractorConfiguration) o;
            return Objects.equals(this.contentTypeBlacklist, that.contentTypeBlacklist);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(contentTypeBlacklist);
    }
}
