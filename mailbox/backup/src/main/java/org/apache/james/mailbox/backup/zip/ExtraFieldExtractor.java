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
package org.apache.james.mailbox.backup.zip;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipShort;

public class ExtraFieldExtractor {

    public static Optional<String> getStringExtraField(ZipShort id, ZipEntry entry) throws ZipException {
        ZipExtraField[] extraFields = ExtraFieldUtils.parse(entry.getExtra());
        return Arrays.stream(extraFields)
            .filter(field -> field.getHeaderId().equals(id))
            .map(StringExtraField.class::cast)
            .map(StringExtraField::getValue)
            .findFirst()
            .flatMap(Function.identity());
    }

    public static Optional<ZipEntryType> getEntryType(ZipEntry entry) {
        try {
            ZipExtraField[] extraFields = ExtraFieldUtils.parse(entry.getExtra());
            return Arrays.stream(extraFields)
                .filter(field -> field.getHeaderId().equals(EntryTypeExtraField.ID_AQ))
                .flatMap(extraField -> ((EntryTypeExtraField) extraField).getEnumValue().stream())
                .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

}
