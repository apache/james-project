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

package org.apache.james.modules.blobstore;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;

import com.google.common.base.MoreObjects;

public class BlobStoreChoosingConfiguration {

    public enum BlobStoreImplName {
        CASSANDRA("cassandra"),
        OBJECTSTORAGE("objectstorage"),
        HYBRID("hybrid");

        static String supportedImplNames() {
            return Stream.of(BlobStoreImplName.values())
                .map(BlobStoreImplName::getName)
                .collect(Collectors.joining(", "));
        }

        static BlobStoreImplName from(String name) {
            return Stream.of(values())
                .filter(blobName -> blobName.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("%s is not a valid name of BlobStores, " +
                    "please use one of supported values in: %s", name, supportedImplNames())));
        }

        private final String name;

        BlobStoreImplName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    static final String BLOBSTORE_IMPLEMENTATION_PROPERTY = "implementation";

    static BlobStoreChoosingConfiguration from(Configuration configuration) {
        BlobStoreImplName blobStoreImplName = Optional.ofNullable(configuration.getString(BLOBSTORE_IMPLEMENTATION_PROPERTY))
            .filter(StringUtils::isNotBlank)
            .map(StringUtils::trim)
            .map(BlobStoreImplName::from)
            .orElseThrow(() -> new IllegalStateException(String.format("%s property is missing please use one of " +
                "supported values in: %s", BLOBSTORE_IMPLEMENTATION_PROPERTY, BlobStoreImplName.supportedImplNames())));

        return new BlobStoreChoosingConfiguration(blobStoreImplName);
    }

    public static BlobStoreChoosingConfiguration cassandra() {
        return new BlobStoreChoosingConfiguration(BlobStoreImplName.CASSANDRA);
    }

    public static BlobStoreChoosingConfiguration objectStorage() {
        return new BlobStoreChoosingConfiguration(BlobStoreImplName.OBJECTSTORAGE);
    }

    public static BlobStoreChoosingConfiguration hybrid() {
        return new BlobStoreChoosingConfiguration(BlobStoreImplName.HYBRID);
    }

    private final BlobStoreImplName implementation;

    BlobStoreChoosingConfiguration(BlobStoreImplName implementation) {
        this.implementation = implementation;
    }

    BlobStoreImplName getImplementation() {
        return implementation;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof BlobStoreChoosingConfiguration) {
            BlobStoreChoosingConfiguration that = (BlobStoreChoosingConfiguration) o;

            return Objects.equals(this.implementation, that.implementation);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(implementation);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("implementation", implementation)
            .toString();
    }
}
