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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipException;

import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipShort;

public abstract class LongExtraField implements ZipExtraField {

    private Optional<Long> value;

    LongExtraField() {
        this(Optional.empty());
    }

    LongExtraField(long value) {
        this(Optional.of(value));
    }

    LongExtraField(Optional<Long> value) {
        this.value = value;
    }

    @Override
    public ZipShort getLocalFileDataLength() {
        return new ZipShort(Long.BYTES);
    }

    @Override
    public ZipShort getCentralDirectoryLength() {
        return getLocalFileDataLength();
    }

    @Override
    public byte[] getLocalFileDataData() {
        long value = this.value.orElseThrow(() -> new RuntimeException("Value must by initialized"));
        return ByteBuffer.allocate(Long.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(value)
            .array();
    }

    @Override
    public byte[] getCentralDirectoryData() {
        return getLocalFileDataData();
    }

    @Override
    public void parseFromLocalFileData(byte[] buffer, int offset, int length) throws ZipException {
        if (length != Long.BYTES) {
            throw new ZipException("Unexpected data length for ExtraField. Expected " + Long.BYTES + " but got " + length + ".");
        }
        value = Optional.of(ByteBuffer
            .wrap(buffer, offset, Long.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .getLong());
    }

    @Override
    public void parseFromCentralDirectoryData(byte[] buffer, int offset, int length) throws ZipException {
        parseFromLocalFileData(buffer, offset, length);
    }

    public Optional<Long> getValue() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof LongExtraField) {
            LongExtraField that = (LongExtraField) o;

            return Objects.equals(this.value, that.value)
                && Objects.equals(this.getHeaderId(), that.getHeaderId());
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value, getHeaderId());
    }
}
