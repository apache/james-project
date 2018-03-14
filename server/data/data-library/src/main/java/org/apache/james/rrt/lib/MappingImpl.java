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
import org.apache.james.rrt.api.RecipientRewriteTable;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;


public class MappingImpl implements Mapping, Serializable {

    private static final long serialVersionUID = 1L;

    private static final String ADDRESS_PREFIX = "";

    public static MappingImpl of(String mapping) {
        return new MappingImpl("", mapping);
    }
    
    public static MappingImpl address(String mapping) {
        return new MappingImpl(ADDRESS_PREFIX, mapping);
    }

    public static MappingImpl regex(String mapping) {
        return new MappingImpl(RecipientRewriteTable.REGEX_PREFIX, mapping);
    }

    public static MappingImpl error(String mapping) {
        return new MappingImpl(RecipientRewriteTable.ERROR_PREFIX, mapping);
    }

    public static MappingImpl domain(Domain mapping) {
        return new MappingImpl(RecipientRewriteTable.ALIASDOMAIN_PREFIX, mapping.asString());
    }
    
    private final String mapping;

    private MappingImpl(String prefix, String mapping) {
        Preconditions.checkNotNull(mapping);
        this.mapping = prefix + mapping;
    }
    
    @Override
    public String asString() {
        return mapping;
    }
    
    @Override
    public boolean hasDomain() {
        return mapping.contains("@");
    }
    
    @Override
    public Mapping appendDomain(Domain domain) {
        Preconditions.checkNotNull(domain);
        return new MappingImpl("", mapping + "@" + domain.asString());
    }
    
    @Override
    public Type getType() {
        if (mapping.startsWith(RecipientRewriteTable.ALIASDOMAIN_PREFIX)) {
            return Type.Domain;
        } else if (mapping.startsWith(RecipientRewriteTable.REGEX_PREFIX)) {
            return Type.Regex;
        } else if (mapping.startsWith(RecipientRewriteTable.ERROR_PREFIX)) {
            return Type.Error;
        } else {
            return Type.Address;
        }
    }
    
    @Override
    public String getErrorMessage() {
        Preconditions.checkState(getType() == Type.Error);
        return mapping.substring(RecipientRewriteTable.ERROR_PREFIX.length());
    }

    @Override
    public String getAddress() {
        Preconditions.checkState(getType() == Type.Address);
        return mapping;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof MappingImpl) {
            MappingImpl otherMapping = (MappingImpl) other;
            return Objects.equal(mapping, otherMapping.mapping);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mapping);
    }

    @Override
    public String toString() {
        return "MappingImpl{mapping=" + mapping + "}";
    }
}