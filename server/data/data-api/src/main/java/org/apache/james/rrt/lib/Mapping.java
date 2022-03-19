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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
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
        return new Impl(type, mapping);
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

    static Mapping domainAlias(Domain mapping) {
        return of(Type.DomainAlias, mapping.asString());
    }

    static Mapping forward(String mapping) {
        return of(Type.Forward, mapping);
    }

    static Mapping group(String mapping) {
        return of(Type.Group, mapping);
    }

    static Mapping alias(String mapping) {
        return of(Type.Alias, mapping);
    }

    static Type detectType(String input) {
        return Arrays.stream(Type.values())
            .filter(Type::hasPrefix)
            .filter(type -> input.startsWith(type.asPrefix()))
            .findAny()
            .orElse(Type.Address);
    }

    enum TypeOrder {
        TYPE_ORDER_1,
        TYPE_ORDER_2,
        TYPE_ORDER_3,
        TYPE_ORDER_4
    }

    enum Type {
        /**
         * Applies the regex on the supplied address.
         */
        Regex("regex:", new UserRewritter.RegexRewriter(), IdentityMappingPolicy.Throw,
            MailAddressConversionPolicy.ToEmpty, TypeOrder.TYPE_ORDER_4),
        /**
         * Rewrites the domain of mail addresses.
         *
         * Use it for technical purposes.
         */
        Domain("domain:", new UserRewritter.DomainRewriter(), IdentityMappingPolicy.Throw,
            MailAddressConversionPolicy.ToEmpty, TypeOrder.TYPE_ORDER_1),
        /**
         * Rewrites the domain of mail addresses.
         *
         * User will be able to use this domain as if it was the one of his mail address.
         */
        DomainAlias("domainAlias:", new UserRewritter.DomainRewriter(), IdentityMappingPolicy.Throw,
            MailAddressConversionPolicy.ToEmpty, TypeOrder.TYPE_ORDER_1),
        /**
         * Throws an error upon processing
         */
        Error("error:", new UserRewritter.ThrowingRewriter(), IdentityMappingPolicy.Throw,
            MailAddressConversionPolicy.ToEmpty, TypeOrder.TYPE_ORDER_4),
        /**
         * Replaces the source address by another one.
         *
         * Vehicles the intent of forwarding incoming mails to other users.
         */
        Forward("forward:", new UserRewritter.ReplaceRewriter(), IdentityMappingPolicy.ReturnIdentity,
            MailAddressConversionPolicy.ToMailAddress, TypeOrder.TYPE_ORDER_3),
        /**
         * Replaces the source address by another one.
         *
         * Vehicles the intent of a group registration: group address will be swapped by group member addresses.
         * (Feature poor mailing list)
         */
        Group("group:", new UserRewritter.ReplaceRewriter(), IdentityMappingPolicy.Throw,
            MailAddressConversionPolicy.ToMailAddress, TypeOrder.TYPE_ORDER_2),
        /**
         * Replaces the source address by another one.
         *
         * Represents user owned mail address, with which he can interact as if it was his main mail address.
         */
        Alias("alias:", new UserRewritter.ReplaceRewriter(), IdentityMappingPolicy.Throw,
            MailAddressConversionPolicy.ToMailAddress, TypeOrder.TYPE_ORDER_3),
        /**
         * Replaces the source address by another one.
         *
         * Use for technical purposes, this mapping type do not hold specific intent. Prefer using one of the above
         * mapping types.
         */
        Address("", new UserRewritter.ReplaceRewriter(), IdentityMappingPolicy.Throw,
            MailAddressConversionPolicy.ToMailAddress, TypeOrder.TYPE_ORDER_4);

        private final String asPrefix;
        private final UserRewritter.MappingUserRewriter rewriter;
        private final IdentityMappingPolicy identityMappingPolicy;
        private final MailAddressConversionPolicy mailAddressConversionPolicy;
        private final TypeOrder typeOrder;

        Type(String asPrefix, UserRewritter.MappingUserRewriter rewriter, IdentityMappingPolicy identityMappingPolicy,
             MailAddressConversionPolicy mailAddressConversionPolicy, TypeOrder typeOrder) {
            this.asPrefix = asPrefix;
            this.rewriter = rewriter;
            this.identityMappingPolicy = identityMappingPolicy;
            this.mailAddressConversionPolicy = mailAddressConversionPolicy;
            this.typeOrder = typeOrder;
        }

        public String asPrefix() {
            return asPrefix;
        }

        public int getTypeOrder() {
            return typeOrder.ordinal();
        }

        public String withoutPrefix(String input) {
            Preconditions.checkArgument(input.startsWith(asPrefix));
            return input.substring(asPrefix.length());
        }

        public boolean hasPrefix() {
            return !asPrefix.isEmpty();
        }

        public static boolean hasPrefix(String mapping) {
            return Arrays.stream(Type.values())
                .filter(Type::hasPrefix)
                .anyMatch(type -> mapping.startsWith(type.asPrefix()));
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

    class Impl implements Mapping, Serializable {

        private final Type type;
        private final String mapping;
        private final UserRewritter rewriter;

        private Impl(Type type, String mapping) {
            Preconditions.checkNotNull(type);
            Preconditions.checkNotNull(mapping);
            this.type = type;
            this.mapping = mapping;
            this.rewriter = type.rewriter.generateUserRewriter(mapping);
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
        public String getMappingValue() {
            return mapping;
        }

        @Override
        public String getErrorMessage() {
            Preconditions.checkState(getType() == Type.Error);
            return mapping;
        }

        @Override
        public Optional<Username> rewriteUser(Username username) throws AddressException, RecipientRewriteTable.ErrorMappingException {
            return rewriter.rewrite(username);
        }

        @Override
        public Stream<Mapping> handleIdentity(Stream<Mapping> nonRecursiveResult) {
            return type.identityMappingPolicy.handleIdentity(nonRecursiveResult);
        }

        @Override
        public Optional<MailAddress> asMailAddress() {
            return type.mailAddressConversionPolicy.convert(mapping);
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

    String getMappingValue();
    
    String asString();

    boolean hasDomain();

    interface ThrowingDomainSupplier {
        Domain get() throws RecipientRewriteTableException;
    }

    Mapping appendDomainFromThrowingSupplierIfNone(ThrowingDomainSupplier supplier) throws RecipientRewriteTableException;

    Mapping appendDomainIfNone(Supplier<Domain> domainSupplier);

    String getErrorMessage();

    Optional<Username> rewriteUser(Username username) throws AddressException, RecipientRewriteTable.ErrorMappingException;

}