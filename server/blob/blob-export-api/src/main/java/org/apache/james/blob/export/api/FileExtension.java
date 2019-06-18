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

package org.apache.james.blob.export.api;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class FileExtension {
    static final String ZIP_EXTENSION_STRING = "zip";
    public static final FileExtension ZIP = new FileExtension(ZIP_EXTENSION_STRING);
    private static final String EXTENSION_SEPARATOR = ".";

    public static FileExtension of(String extension) {
        return new FileExtension(extension);
    }

    private final String extension;

    @VisibleForTesting
    FileExtension(String extension) {
        Preconditions.checkNotNull(extension, "'extension' can not be null");
        Preconditions.checkArgument(StringUtils.isNotBlank(extension), "'extension' can not be blank");

        this.extension = extension;
    }

    public String appendExtension(String filePath) {
        Preconditions.checkArgument(StringUtils.isNotBlank(filePath), "filePath cannot be null or blank");

        return filePath + asFileSuffix();
    }

    public String asFileSuffix() {
        return EXTENSION_SEPARATOR + extension;
    }

    public String getExtension() {
        return extension;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof FileExtension) {
            FileExtension that = (FileExtension) o;

            return Objects.equals(this.extension, that.extension);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(extension);
    }
}