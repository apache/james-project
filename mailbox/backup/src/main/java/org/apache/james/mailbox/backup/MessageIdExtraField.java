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

package org.apache.james.mailbox.backup;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipShort;

public class MessageIdExtraField implements ZipExtraField {

    public static final ZipShort ID = new ZipShort(0x6C61); // "al" in little-endian

    private Optional<String> messageId;

    public MessageIdExtraField() {
        this(Optional.empty());
    }

    public MessageIdExtraField(String messageId) {
        this(Optional.of(messageId));
    }

    public MessageIdExtraField(Optional<String> messageId) {
        this.messageId = messageId;
    }

    @Override
    public ZipShort getHeaderId() {
        return ID;
    }

    @Override
    public ZipShort getLocalFileDataLength() {
        return messageId
            .map(value -> value.getBytes(StandardCharsets.UTF_8).length)
            .map(ZipShort::new)
            .orElseThrow(() -> new RuntimeException("Value must by initialized"));
    }

    @Override
    public ZipShort getCentralDirectoryLength() {
        return getLocalFileDataLength();
    }

    @Override
    public byte[] getLocalFileDataData() {
        return messageId
            .map(value -> value.getBytes(StandardCharsets.UTF_8))
            .orElseThrow(() -> new RuntimeException("Value must by initialized"));
    }

    @Override
    public byte[] getCentralDirectoryData() {
        return getLocalFileDataData();
    }

    @Override
    public void parseFromLocalFileData(byte[] buffer, int offset, int length) {
        messageId = Optional.of(new String(buffer, offset, length, StandardCharsets.UTF_8));
    }

    @Override
    public void parseFromCentralDirectoryData(byte[] buffer, int offset, int length) {
        parseFromLocalFileData(buffer, offset, length);
    }

    public Optional<String> getMessageId() {
        return messageId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MessageIdExtraField) {
            MessageIdExtraField that = (MessageIdExtraField) o;

            return Objects.equals(this.messageId, that.messageId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(messageId);
    }
}
