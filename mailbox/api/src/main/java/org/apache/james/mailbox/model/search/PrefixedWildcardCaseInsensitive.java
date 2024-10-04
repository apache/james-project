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

package org.apache.james.mailbox.model.search;

import java.util.Objects;

import com.google.common.base.Preconditions;

public class PrefixedWildcardCaseInsensitive implements MailboxNameExpression {

    private final String prefix;

    public PrefixedWildcardCaseInsensitive(String prefix) {
        Preconditions.checkNotNull(prefix);
        this.prefix = prefix;
    }

    @Override
    public boolean isExpressionMatch(String name) {
        return name.toLowerCase().startsWith(prefix.toLowerCase());
    }

    @Override
    public String getCombinedName() {
        return prefix + FREEWILDCARD;
    }

    @Override
    public boolean isWild() {
        return true;
    }

    @Override
    public MailboxNameExpression includeChildren() {
        return this;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PrefixedWildcardCaseInsensitive) {
            PrefixedWildcardCaseInsensitive that = (PrefixedWildcardCaseInsensitive) o;

            return Objects.equals(this.prefix, that.prefix);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(prefix);
    }
}
