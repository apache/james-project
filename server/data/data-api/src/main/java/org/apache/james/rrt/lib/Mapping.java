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
import java.util.stream.Stream;

import javax.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.User;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public interface Mapping {

    static Mapping of(String mapping) {
        Type type = Mapping.detectType(mapping);
        return of(type, type.withoutPrefix(mapping));
    }

    static Mapping of(Type type, String mapping) {
        UserRewritter.MappingUserRewriter rewriter = selectRewriter(type);
        IdentityMappingPolicy identityMappingPolicy = selectIdentityPolicy(type);
        MailAddressConversionPolicy mailAddressConversionPolicy = selectMailAddressConversionPolicy(type);
        return new Impl(type, mapping, rewriter.generateUserRewriter(mapping), identityMappingPolicy, mailAddressConversionPolicy);
    }

    static UserRewritter.MappingUserRewriter selectRewriter(Type type) {
        switch (type) {
            case Regex:
                return new UserRewritter.RegexRewriter();
            case Domain:
                return new UserRewritter.DomainRewriter();
            case Error:
                return new UserRewritter.ThrowingRewriter();
            case Forward:
            case Group:
            case Address:
                return new UserRewritter.ReplaceRewriter();
        }
        throw new IllegalStateException("unhandle enum type");
    }

    static IdentityMappingPolicy selectIdentityPolicy(Type type) {
        switch (type) {
            case Regex:
            case Domain:
            case Error:
            case Group:
            case Address:
                return IdentityMappingPolicy.Throw;
            case Forward:
                return IdentityMappingPolicy.ReturnIdentity;
        }
        throw new IllegalStateException("unhandle enum type");
    }

    enum MailAddressConversionPolicy {
        ToEmpty {
            @Override
            Optional<MailAddress> convert(String mapping) {
                return Optional.empty();
            }
        },
        ToMailAddress {
            @Override
            Optional<MailAddress> convert(String mapping) {
                try {
                    return Optional.of(new MailAddress(mapping));
                } catch (AddressException e) {
                    return Optional.empty();
                }
            }
        };

        abstract Optional<MailAddress> convert(String mapping);
    }

    static MailAddressConversionPolicy selectMailAddressConversionPolicy(Type type) {
        switch (type) {
            case Regex:
            case Domain:
            case Error:
                return MailAddressConversionPolicy.ToEmpty;
            case Forward:
            case Group:
            case Address:
                return MailAddressConversionPolicy.ToMailAddress;
            }
        throw new IllegalStateException("unhandle enum type");
    }

    static Mapping address(String mapping) {
        return of(Type.Address, mapping);
    }

    static Mapping regex(String mapping) {
        return of(Type.Regex, mapping);
    }

    static Mapping error(String mapping) {
        return of(Type.Error, mapping);
    }

    static Mapping domain(Domain mapping) {
        return of(Type.Domain, mapping.asString());
    }

    static Mapping forward(String mapping) {
        return of(Type.Forward, mapping);
    }

    static Mapping group(String mapping) {
        return of(Type.Group, mapping);
    }

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

    enum IdentityMappingPolicy {
        Throw {
            @Override
            public Stream<Mapping> handleIdentity(Stream<Mapping> mapping) {
                throw new SkipMappingProcessingException();
            }
        },
        ReturnIdentity {
            @Override
            public Stream<Mapping> handleIdentity(Stream<Mapping> mapping) {
                return mapping;
            }
        };

        public abstract Stream<Mapping> handleIdentity(Stream<Mapping> mapping);
    }

    Optional<MailAddress> asMailAddress();

    Stream<Mapping> handleIdentity(Stream<Mapping> nonRecursiveResult);

    class Impl implements Mapping {

        private final Type type;
        private final String mapping;
        private final UserRewritter rewriter;
        private final IdentityMappingPolicy identityMappingPolicy;
        private final MailAddressConversionPolicy mailAddressConversionPolicy;

        private Impl(Type type,
                     String mapping,
                     UserRewritter rewriter,
                     IdentityMappingPolicy identityMappingBehaviour,
                     MailAddressConversionPolicy mailAddressConversionPolicy) {
            Preconditions.checkNotNull(type);
            Preconditions.checkNotNull(mapping);
            this.type = type;
            this.mapping = mapping;
            this.rewriter = rewriter;
            this.identityMappingPolicy = identityMappingBehaviour;
            this.mailAddressConversionPolicy = mailAddressConversionPolicy;
        }

        @Override
        public String asString() {
            return type.asPrefix() + mapping;
        }

        @Override
        public boolean hasDomain() {
            return mapping.contains("@");
        }

        @Override
        public Mapping appendDomainIfNone(Supplier<Domain> domain) {
            Preconditions.checkNotNull(domain);
            if (hasDomain()) {
                return this;
            }
            return appendDomain(domain.get());
        }

        @Override
        public Mapping appendDomainFromThrowingSupplierIfNone(ThrowingDomainSupplier supplier) throws RecipientRewriteTableException {
            Preconditions.checkNotNull(supplier);
            if (hasDomain()) {
                return this;
            }
            return appendDomain(supplier.get());
        }

        private Mapping appendDomain(Domain domain) {
            return of(type, mapping + "@" + domain.asString());
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public String getErrorMessage() {
            Preconditions.checkState(getType() == Type.Error);
            return mapping;
        }

        @Override
        public Optional<User> rewriteUser(User user) throws AddressException, RecipientRewriteTable.ErrorMappingException {
            return rewriter.rewrite(user);
        }

        @Override
        public Stream<Mapping> handleIdentity(Stream<Mapping> nonRecursiveResult) {
            return identityMappingPolicy.handleIdentity(nonRecursiveResult);
        }

        @Override
        public Optional<MailAddress> asMailAddress() {
            return mailAddressConversionPolicy.convert(mapping);
        }

        @Override
        public final boolean equals(Object other) {
            if (other instanceof Impl) {
                Impl otherMapping = (Impl) other;
                return Objects.equal(type, otherMapping.type)
                    && Objects.equal(mapping, otherMapping.mapping);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(type, mapping);
        }

        @Override
        public String toString() {
            return "Mapping{type=" + type + " mapping=" + mapping + "}";
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

    Optional<User> rewriteUser(User user) throws AddressException, RecipientRewriteTable.ErrorMappingException;

}