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

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

public class MailRepositoryPath implements Comparable<MailRepositoryPath> {

    private static final String PATH_DELIMITER = "/";

    public static MailRepositoryPath fromEncoded(String encodedPath) throws UnsupportedEncodingException {
        Preconditions.checkNotNull(encodedPath, "Supplied MailRepositoryPath value is null");

        return from(URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.displayName()));
    }

    public static MailRepositoryPath from(String path) {
        Preconditions.checkNotNull(path, "Supplied MailRepositoryPath value is null");

        return new MailRepositoryPath(sanitizePath(path));
    }

    private static String sanitizePath(String path) {
        return StringUtils.stripEnd(path, "/");
    }

    private final String value;

    private MailRepositoryPath(String value) {
        Preconditions.checkNotNull(value);

        this.value = value;
    }

    public MailRepositoryPath subPath(String suffix) {
        Preconditions.checkArgument(!suffix.startsWith(PATH_DELIMITER), "The suffix used can not start by the path delimiter");

        return from(value + PATH_DELIMITER + suffix);
    }

    public boolean hasPrefix(MailRepositoryPath other) {
        if (value.isEmpty()) {
            return false;
        }
        if (other.value.isEmpty()) {
            return true;
        }
        return value.startsWith(other.value + PATH_DELIMITER);
    }

    public List<String> parts() {
        return Splitter.on(PATH_DELIMITER)
            .omitEmptyStrings()
            .splitToList(value);
    }

    public String asString() {
        return value;
    }

    public String urlEncoded() throws UnsupportedEncodingException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.displayName());
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailRepositoryPath) {
            MailRepositoryPath that = (MailRepositoryPath) o;

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

    @Override
    public int compareTo(MailRepositoryPath that) {
        return this.value.compareTo(that.value);
    }
}
