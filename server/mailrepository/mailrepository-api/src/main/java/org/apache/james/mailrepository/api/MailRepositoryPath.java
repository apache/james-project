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
import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class MailRepositoryPath implements Comparable<MailRepositoryPath> {
    public static final MailRepositoryPath fromEncoded(String encodedPath) throws UnsupportedEncodingException {
        return new MailRepositoryPath(URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.displayName()));
    }

    public static final MailRepositoryPath from(String path) {
        return new MailRepositoryPath(path);
    }

    private final String value;

    private MailRepositoryPath(String value) {
        Preconditions.checkNotNull(value);
        this.value = value;
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
