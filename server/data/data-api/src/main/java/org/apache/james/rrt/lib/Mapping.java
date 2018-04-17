/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.rrt.lib;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.rrt.api.RecipientRewriteTableException;

import com.google.common.base.Preconditions;

public interface Mapping {

    static Type detectType(String input) {
        if (input.startsWith(Type.Regex.asPrefix())) {
            return Type.Regex;
        }
        if (input.startsWith(Type.Domain.asPrefix())) {
            return Type.Domain;
        }
        if (input.startsWith(Type.Error.asPrefix())) {
            return Type.Error;
        }
        if (input.startsWith(Type.Forward.asPrefix())) {
            return Type.Forward;
        }
        if (input.startsWith(Type.Group.asPrefix())) {
            return Type.Group;
        }
        return Type.Address;
    }

    Optional<MailAddress> asMailAddress();

    enum Type {
        Regex("regex:"),
        Domain("domain:"),
        Error("error:"),
        Forward("forward:"),
        Group("group:"),
        Address("");

        private final String asPrefix;

        Type(String asPrefix) {
            this.asPrefix = asPrefix;
        }

        public String asPrefix() {
            return asPrefix;
        }

        public String withoutPrefix(String input) {
            Preconditions.checkArgument(input.startsWith(asPrefix));
            return input.substring(asPrefix.length());
        }

        public static boolean hasPrefix(String mapping) {
            return mapping.startsWith(Regex.asPrefix())
                || mapping.startsWith(Domain.asPrefix())
                || mapping.startsWith(Error.asPrefix())
                || mapping.startsWith(Forward.asPrefix())
                || mapping.startsWith(Group.asPrefix());
        }
    }

    Type getType();
    
    String asString();

    boolean hasDomain();

    interface ThrowingDomainSupplier {
        Domain get() throws RecipientRewriteTableException;
    }

    Mapping appendDomainFromThrowingSupplierIfNone(ThrowingDomainSupplier supplier) throws RecipientRewriteTableException;

    Mapping appendDomainIfNone(Supplier<Domain> domainSupplier);

    String getErrorMessage();

}