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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.metrics.api.MetricFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TikaTextExtractor implements TextExtractor {

    private final MetricFactory metricFactory;
    private final TikaHttpClient tikaHttpClient;
    private final ObjectMapper objectMapper;

    @Inject
    public TikaTextExtractor(MetricFactory metricFactory, TikaHttpClient tikaHttpClient) {
        this.metricFactory = metricFactory;
        this.tikaHttpClient = tikaHttpClient;
        this.objectMapper = initializeObjectMapper();
    }

    private ObjectMapper initializeObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule mapModule = new SimpleModule();
        mapModule.addDeserializer(ContentAndMetadata.class, new ContentAndMetadataDeserializer());
        objectMapper.registerModule(mapModule);
        return objectMapper;
    }

    @Override
    public ParsedContent extractContent(InputStream inputStream, String contentType) throws Exception {
        return metricFactory.runPublishingTimerMetric("tikaTextExtraction", Throwing.supplier(
            () -> performContentExtraction(inputStream, contentType))
            .sneakyThrow());
    }

    public ParsedContent performContentExtraction(InputStream inputStream, String contentType) throws IOException {
        ContentAndMetadata contentAndMetadata = convert(tikaHttpClient.recursiveMetaDataAsJson(inputStream, contentType));
        return new ParsedContent(contentAndMetadata.getContent(), contentAndMetadata.getMetadata());
    }

    private ContentAndMetadata convert(Optional<InputStream> maybeInputStream) throws IOException, JsonParseException, JsonMappingException {
        return maybeInputStream
                .map(Throwing.function(inputStream -> objectMapper.readValue(inputStream, ContentAndMetadata.class)))
                .orElse(ContentAndMetadata.empty());
    }

    @VisibleForTesting
    static class ContentAndMetadataDeserializer extends JsonDeserializer<ContentAndMetadata> {

        @Override
        public ContentAndMetadata deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            TreeNode treeNode = jsonParser.getCodec().readTree(jsonParser);
            Preconditions.checkState(treeNode.isArray() && treeNode.size() >= 1, "The response should be an array with at least one element");
            Preconditions.checkState(treeNode.get(0).isObject(), "The element should be a Json object");
            ObjectNode node = (ObjectNode) treeNode.get(0);
            return ContentAndMetadata.from(ImmutableList.copyOf(node.fields())
                .stream()
                .collect(Guavate.toImmutableMap(Entry::getKey, entry -> asListOfString(entry.getValue()))));
        }

        @VisibleForTesting List<String> asListOfString(JsonNode jsonNode) {
            if (jsonNode.isArray()) {
                return ImmutableList.copyOf(jsonNode.elements()).stream()
                    .map(JsonNode::asText)
                    .collect(Guavate.toImmutableList());
            }
            return ImmutableList.of(jsonNode.asText());
        }

    }

    private static class ContentAndMetadata {

        private static final String TIKA_HEADER = "X-TIKA";
        private static final String CONTENT_METADATA_HEADER_NAME = TIKA_HEADER + ":content";

        public static ContentAndMetadata empty() {
            return new ContentAndMetadata();
        }

        public static ContentAndMetadata from(Map<String, List<String>> contentAndMetadataMap) {
            return new ContentAndMetadata(Optional.ofNullable(content(contentAndMetadataMap)),
                    contentAndMetadataMap.entrySet().stream()
                        .filter(allHeadersButTika())
                        .collect(Guavate.toImmutableMap(Entry::getKey, Entry::getValue)));
        }

        private static Predicate<? super Entry<String, List<String>>> allHeadersButTika() {
            return entry -> !entry.getKey().startsWith(TIKA_HEADER);
        }

        private static String content(Map<String, List<String>> contentAndMetadataMap) {
            List<String> content = contentAndMetadataMap.get(CONTENT_METADATA_HEADER_NAME);
            if (content == null) {
                return null;
            }
            String onlySpaces = null;
            return StringUtils.stripStart(content.get(0), onlySpaces);
        }

        private final Optional<String> content;
        private final Map<String, List<String>> metadata;

        private ContentAndMetadata() {
            this(Optional.empty(), ImmutableMap.of());
        }

        private ContentAndMetadata(Optional<String> content, Map<String, List<String>> metadata) {
            this.content = content;
            this.metadata = metadata;
        }

        public Optional<String> getContent() {
            return content;
        }

        public Map<String, List<String>> getMetadata() {
            return metadata;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof ContentAndMetadata) {
                ContentAndMetadata other = (ContentAndMetadata) o;
                return Objects.equals(content, other.content)
                    && Objects.equals(metadata, other.metadata);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(content, metadata);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("content", content)
                .add("metadata", metadata)
                .toString();
        }
    }
}
