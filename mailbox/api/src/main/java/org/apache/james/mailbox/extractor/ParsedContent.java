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

package org.apache.james.mailbox.extractor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;

public class ParsedContent {
    public static ParsedContent empty() {
        return new ParsedContent(Optional.empty(), ImmutableMap.of());
    }

    public static ParsedContent of(String textualContent) {
        return new ParsedContent(Optional.of(textualContent), ImmutableMap.of());
    }

    public static ParsedContent of(Optional<String> textualContent) {
        return new ParsedContent(textualContent, ImmutableMap.of());
    }

    public static ParsedContent of(Optional<String> textualContent, Map<String, List<String>> metadata) {
        return new ParsedContent(textualContent, metadata);
    }

    private final Optional<String> textualContent;
    private final Map<String, List<String>> metadata;

    private ParsedContent(Optional<String> textualContent, Map<String, List<String>> metadata) {
        this.textualContent = textualContent;
        this.metadata = metadata;
    }

    public Optional<String> getTextualContent() {
        return textualContent;
    }

    public  Map<String, List<String>> getMetadata() {
        return metadata;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof  ParsedContent) {
            ParsedContent that = (ParsedContent) o;

            return Objects.equals(this.textualContent, that.textualContent)
                && Objects.equals(this.metadata, that.metadata);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(textualContent, metadata);
    }
}
