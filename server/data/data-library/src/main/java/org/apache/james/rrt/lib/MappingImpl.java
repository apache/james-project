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

import org.apache.james.core.Domain;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;


public class MappingImpl implements Mapping, Serializable {

    private static final long serialVersionUID = 1L;

    public static MappingImpl of(String mapping) {
        Type type = Mapping.detectType(mapping);
        return new MappingImpl(type, type.withoutPrefix(mapping));
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
    public Mapping appendDomain(Domain domain) {
        Preconditions.checkNotNull(domain);
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
    public String getAddress() {
        Preconditions.checkState(getType() == Type.Address);
        return mapping;
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