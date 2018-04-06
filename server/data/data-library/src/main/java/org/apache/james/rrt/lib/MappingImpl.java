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
import java.util.Optional;
import java.util.function.Supplier;

import javax.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.rrt.api.RecipientRewriteTableException;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class MappingImpl implements Mapping, Serializable {

    private static final long serialVersionUID = 1L;

    public static MappingImpl of(String mapping) {
        Type type = Mapping.detectType(mapping);
        return new MappingImpl(type, type.withoutPrefix(mapping));
    }

    public static MappingImpl of(Type type, String mapping) {
        return new MappingImpl(type, mapping);
    }
    
    public static MappingImpl address(String mapping) {
        return new MappingImpl(Type.Address, mapping);
    }

    public static MappingImpl regex(String mapping) {
        return new MappingImpl(Type.Regex, mapping);
    }

    public static MappingImpl error(String mapping) {
        return new MappingImpl(Type.Error, mapping);
    }

    public static MappingImpl domain(Domain mapping) {
        return new MappingImpl(Type.Domain, mapping.asString());
    }

    public static MappingImpl forward(String mapping) {
        return new MappingImpl(Type.Forward, mapping);
    }

    public static MappingImpl group(String mapping) {
        return new MappingImpl(Type.Group, mapping);
    }
    
    private final Type type;
    private final String mapping;

    private MappingImpl(Type type, String mapping) {
        Preconditions.checkNotNull(type);
        Preconditions.checkNotNull(mapping);
        this.type = type;
        this.mapping = mapping;
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
    public Mapping appendDomainFromThrowingSupplierIfNone(ThrowingDomainSupplier supplier) throws RecipientRewriteTableException {
        Preconditions.checkNotNull(supplier);
        if (hasDomain()) {
            return this;
        }
        return appendDomain(supplier.get());
    }

    @Override
    public Mapping appendDomainIfNone(Supplier<Domain> domain) {
        Preconditions.checkNotNull(domain);
        if (hasDomain()) {
            return this;
        }
        return appendDomain(domain.get());
    }

    private MappingImpl appendDomain(Domain domain) {
        return new MappingImpl(type, mapping + "@" + domain.asString());
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
    public Optional<MailAddress> asMailAddress() {
        if (type != Type.Address && type != Type.Forward && type != Type.Group) {
            return Optional.empty();
        }
        try {
            return Optional.of(new MailAddress(mapping));
        } catch (AddressException e) {
            return Optional.empty();
        }
    }

    @Override
    public final boolean equals(Object other) {
        if (other instanceof MappingImpl) {
            MappingImpl otherMapping = (MappingImpl) other;
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
        return "MappingImpl{type=" + type + " mapping=" + mapping + "}";
    }
}