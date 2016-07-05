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

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class MailboxAnnotation {

    public static MailboxAnnotation nil(MailboxAnnotationKey key) {
        return new MailboxAnnotation(key, Optional.<String> absent());
    }

    public static MailboxAnnotation newInstance(MailboxAnnotationKey key, String value) {
        return new MailboxAnnotation(key, Optional.of(value));
    }

    private final MailboxAnnotationKey key;
    private final Optional<String> value;

    private MailboxAnnotation(MailboxAnnotationKey key, Optional<String> value) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(value);
        Preconditions.checkArgument(key.isValid(),
            "Key must start with '/' and not end with '/' and does not contain charater with hex from '\u0000' to '\u00019' or {'*', '%', two consecutive '/'} ");
        this.key = key;
        this.value = value;
    }

    public MailboxAnnotationKey getKey() {
        return key;
    }

    public Optional<String> getValue() {
        return value;
    }

    public int size() {
        if (isNil()) {
            return 0;
        }
        return value.get().length();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key, value);
    }

    public boolean isNil() {
        return !value.isPresent();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MailboxAnnotation) {
            MailboxAnnotation o = (MailboxAnnotation) obj;
            return Objects.equal(key, o.getKey()) && Objects.equal(value, o.getValue());
        } else {
            return false;
        }
    }

}
