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

package org.apache.james.modules;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

class BlobExportImplChoice {

    enum BlobExportImplName {
        LOCAL_FILE("localFile");

        private static Optional<BlobExportImplName> from(String implNameString) {
            Preconditions.checkNotNull(implNameString);

            return Stream.of(values())
                .filter(impl -> impl.name.equals(implNameString))
                .findFirst();
        }

        private static ImmutableList<String> plainImplNames() {
            return Stream.of(values())
                .map(impl -> impl.name)
                .collect(Guavate.toImmutableList());
        }

        private final String name;

        BlobExportImplName(String name) {
            this.name = name;
        }

        String getImplName() {
            return name;
        }
    }

    static BlobExportImplChoice localFile() {
        return new BlobExportImplChoice(BlobExportImplName.LOCAL_FILE);
    }

    static BlobExportImplChoice from(Configuration configuration) throws ConfigurationException {
        String blobExportImpl = configuration.getString(BLOB_EXPORT_MECHANISM_IMPL);

        String sanitizedImplName = Optional.ofNullable(blobExportImpl)
            .map(String::trim)
            .orElseThrow(() -> new ConfigurationException(BLOB_EXPORT_MECHANISM_IMPL + " property is mandatory"));

        return BlobExportImplName.from(sanitizedImplName)
            .map(BlobExportImplChoice::new)
            .orElseThrow(() -> new ConfigurationException(unknownBlobExportErrorMessage(blobExportImpl)));
    }

    private static String unknownBlobExportErrorMessage(String blobExportImpl) {
        return String.format("unknown blob export mechanism '%s', please choose one in supported implementations(%s)",
            blobExportImpl,
            Joiner.on(",").join(BlobExportImplName.plainImplNames()));
    }

    private static final String BLOB_EXPORT_MECHANISM_IMPL = "blob.export.implementation";

    private final BlobExportImplName impl;

    private BlobExportImplChoice(BlobExportImplName implName) {
        this.impl = implName;
    }

    public BlobExportImplName getImpl() {
        return impl;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof BlobExportImplChoice) {
            BlobExportImplChoice that = (BlobExportImplChoice) o;

            return Objects.equals(this.impl, that.impl);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(impl);
    }
}
