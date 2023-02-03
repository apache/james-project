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

import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.field.ContentTypeFieldLenientImpl;
import org.apache.james.mime4j.field.contenttype.parser.ContentTypeParser;
import org.apache.james.mime4j.field.contenttype.parser.ParseException;
import org.apache.james.mime4j.stream.RawField;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;


/**
 * Follows syntax and usage as defined in https://tools.ietf.org/html/rfc2045#section-5
 * Thus includes mime type, defined by its media type and subtype as well as contentType fields parameters,
 * including charset
 *
 * Example: text/plain; charset=utf-8
 */
public class ContentType {
    public static class MimeType {
        public static MimeType of(String value) {
            ContentTypeParser parser = new ContentTypeParser(new StringReader(value));
            try {
                parser.parseAll();
            } catch (ParseException e) {
                throw new IllegalArgumentException("Invalid mimeType", e);
            }
            return new MimeType(
                new MediaType(parser.getType()),
                new SubType(parser.getSubType()));
        }

        public static MimeType of(MediaType mediaType, SubType subType) {
            return new MimeType(mediaType, subType);
        }

        private final MediaType mediaType;
        private final SubType subType;

        private MimeType(MediaType mediaType, SubType subType) {
            this.mediaType = mediaType;
            this.subType = subType;
        }

        public String asString() {
            return mediaType.asString() + "/" + subType.asString();
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof MimeType) {
                MimeType mimeType = (MimeType) o;

                return Objects.equals(this.mediaType, mimeType.mediaType)
                    && Objects.equals(this.subType, mimeType.subType);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mediaType, subType);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("mediaType", mediaType)
                .add("subType", subType)
                .toString();
        }
    }

    public static class MediaType {

        public static MediaType TEXT = MediaType.of("text");

        public static MediaType of(String value) {
            Preconditions.checkState(!Strings.isNullOrEmpty(value), "'media type' is mandatory");
            return new MediaType(value);
        }

        private final String value;

        private MediaType(String value) {
            this.value = value;
        }

        public String asString() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof MediaType) {
                MediaType mediaType = (MediaType) o;

                return Objects.equals(this.value, mediaType.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("value", value)
                .toString();
        }
    }

    public static class SubType {
        public static SubType of(String value) {
            Preconditions.checkState(!Strings.isNullOrEmpty(value), "'sub type' is mandatory");
            return new SubType(value);
        }

        private final String value;

        private SubType(String value) {
            this.value = value;
        }

        public String asString() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof SubType) {
                SubType subType = (SubType) o;

                return Objects.equals(this.value, subType.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("value", value)
                .toString();
        }
    }

    public static ContentType of(String value) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(value), "'content type' is mandatory");
        return new ContentType(value, Optional.empty(), Optional.empty());
    }

    public static ContentType of(MimeType mimeType) {
        return new ContentType(mimeType.asString(), Optional.of(mimeType), Optional.empty());
    }

    public static ContentType of(MimeType mimeType, Optional<Charset> charset) {
        return new ContentType(
            charset.map(value -> mimeType.asString() + "; charset=" + value.name())
                .orElse(mimeType.asString()),
            Optional.of(mimeType),
            charset);
    }

    private final String value;
    private final Optional<MimeType> mimeType;
    private final Optional<Charset> charset;

    private ContentType(String value, Optional<MimeType> mimeType, Optional<Charset> charset) {
        this.value = value;
        this.mimeType = mimeType;
        this.charset = charset;
    }

    public ContentTypeField asMime4J() {
        return ContentTypeFieldLenientImpl.PARSER.parse(new RawField("Content-Type", value), DecodeMonitor.SILENT);
    }

    public MimeType mimeType() {
        return mimeType.orElseGet(() -> {
            ContentTypeField contentTypeField = asMime4J();
            return MimeType.of(
                    MediaType.of(contentTypeField.getMediaType()),
                    SubType.of(contentTypeField.getSubType()));
        });
    }

    public MediaType mediaType() {
        return mimeType().mediaType;
    }

    public SubType subType() {
        return mimeType().subType;
    }

    public Optional<Charset> charset() {
        return charset.or(() -> Optional.ofNullable(asMime4J().getCharset())
            .filter(s -> !s.isBlank())
            .map(Charset::forName));
    }

    public String asString() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ContentType) {
            ContentType that = (ContentType) o;

            return Objects.equals(this.value, that.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value);
    }
}
