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

package org.apache.james.mailrepository.api;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class MailRepositoryUrl {
    private static final int PROTOCOL_PART = 0;
    private static final String PROTOCOL_SEPARATOR = "://";
    private static final int SKIP_PROTOCOL = 1;

    public static MailRepositoryUrl fromEncoded(String encodedUrl) throws UnsupportedEncodingException {
        return from(URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.displayName()));
    }

    public static MailRepositoryUrl from(String url) {
        Preconditions.checkNotNull(url);
        Preconditions.checkArgument(url.contains(PROTOCOL_SEPARATOR),
            "The expected format is: <protocol> \"%s\" <path>", PROTOCOL_SEPARATOR);

        List<String> urlParts = Splitter.on(PROTOCOL_SEPARATOR).splitToList(url);

        Protocol protocol = new Protocol(urlParts.get(PROTOCOL_PART));
        MailRepositoryPath path = MailRepositoryPath.from(
            Joiner.on(PROTOCOL_SEPARATOR)
                .join(Iterables.skip(urlParts, SKIP_PROTOCOL)));

        return new MailRepositoryUrl(path, protocol);
    }

    public static MailRepositoryUrl fromPathAndProtocol(MailRepositoryPath path, String protocol) {
        return new MailRepositoryUrl(path, new Protocol(protocol));
    }

    public static MailRepositoryUrl fromPathAndProtocol(Protocol protocol, MailRepositoryPath path) {
        return new MailRepositoryUrl(path, protocol);
    }

    private final String value;
    private final MailRepositoryPath path;
    private final Protocol protocol;

    private MailRepositoryUrl(MailRepositoryPath path, Protocol protocol) {
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(protocol);
        this.path = path;
        this.protocol = protocol;
        this.value = protocol.getValue()  + PROTOCOL_SEPARATOR + path.asString();
    }

    public String asString() {
        return value;
    }

    public MailRepositoryPath getPath() {
        return path;
    }

    public MailRepositoryUrl subUrl(String suffix) {
        return new MailRepositoryUrl(path.subPath(suffix), protocol);
    }

    public String urlEncoded() throws UnsupportedEncodingException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.displayName());
    }

    public boolean hasPrefix(MailRepositoryUrl other) {
        return Objects.equals(this.protocol, other.protocol)
            && this.path.hasPrefix(other.path);
    }

    public Protocol getProtocol() {
       return protocol;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailRepositoryUrl) {
            MailRepositoryUrl that = (MailRepositoryUrl) o;

            return Objects.equals(this.value, that.value);
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
