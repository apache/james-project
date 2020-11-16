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

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.configuration2.Configuration;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public enum  BlobExportImplChoice {
    LOCAL_FILE("localFile"),
    LINSHARE("linshare");

    private static Optional<BlobExportImplChoice> from(String implNameString) {
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

    static Optional<BlobExportImplChoice> from(Configuration configuration) {
        String blobExportImpl = configuration.getString(BLOB_EXPORT_MECHANISM_IMPL);

        Optional<String> sanitizedImplName = Optional.ofNullable(blobExportImpl)
            .map(String::trim);

        return sanitizedImplName.map(name -> BlobExportImplChoice.from(name)
            .orElseThrow(() -> new IllegalArgumentException(unknownBlobExportErrorMessage(name))));
    }

    private static String unknownBlobExportErrorMessage(String blobExportImpl) {
        return String.format("unknown blob export mechanism '%s', please choose one in supported implementations(%s)",
            blobExportImpl,
            Joiner.on(",").join(BlobExportImplChoice.plainImplNames()));
    }

    private static final String BLOB_EXPORT_MECHANISM_IMPL = "blob.export.implementation";

    private final String name;

    BlobExportImplChoice(String name) {
        this.name = name;
    }

    String getImplName() {
        return name;
    }
}
