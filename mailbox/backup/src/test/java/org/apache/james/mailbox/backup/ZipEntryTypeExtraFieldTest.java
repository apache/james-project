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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.zip.ZipException;

import org.apache.james.mailbox.backup.zip.EntryTypeExtraField;
import org.apache.james.mailbox.backup.zip.ZipEntryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.base.Charsets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

class ZipEntryTypeExtraFieldTest {
    private static final byte[] ZERO_AS_BYTE_ARRAY = {0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] ONE_AS_BYTE_ARRAY = {1, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] TWO_AS_BYTE_ARRAY = {2, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] THREE_AS_BYTE_ARRAY = {3, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] FOUR_AS_BYTE_ARRAY = {4, 0, 0, 0, 0, 0, 0, 0};

    private EntryTypeExtraField testee;

    @BeforeEach
    void setUp() {
        testee = new EntryTypeExtraField();
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(EntryTypeExtraField.class)
            .suppress(Warning.NONFINAL_FIELDS)
            .verify();
    }

    @Test
    void getLocalFileDataLengthShouldReturnIntegerSize() {
        assertThat(testee.getLocalFileDataLength().getValue())
            .isEqualTo(Long.BYTES);
    }

    @Test
    void getCentralDirectoryLengthShouldReturnIntegerSize() {
        assertThat(testee.getCentralDirectoryLength().getValue())
            .isEqualTo(Long.BYTES);
    }

    @Test
    void getHeaderIdShouldReturnSpecificStringInLittleEndian() {
        ByteBuffer byteBuffer = ByteBuffer.wrap(testee.getHeaderId().getBytes())
            .order(ByteOrder.LITTLE_ENDIAN);
        assertThat(Charsets.US_ASCII.decode(byteBuffer).toString())
            .isEqualTo("aq");
    }

    @Test
    void getLocalFileDataDataShouldThrowWhenNoValue() {
        assertThatThrownBy(() -> testee.getLocalFileDataData())
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getLocalFileDataShouldReturnValueInLittleIndianWhenMailbox() {
        byte[] actual = new EntryTypeExtraField(ZipEntryType.MAILBOX).getLocalFileDataData();
        assertThat(actual).isEqualTo(ZERO_AS_BYTE_ARRAY);
    }

    @Test
    void getLocalFileDataShouldReturnValueInLittleIndianWhenMailboxAnnotationDir() {
        byte[] actual = new EntryTypeExtraField(ZipEntryType.MAILBOX_ANNOTATION_DIR).getLocalFileDataData();
        assertThat(actual).isEqualTo(ONE_AS_BYTE_ARRAY);
    }

    @Test
    void getLocalFileDataShouldReturnValueInLittleIndianWhenMailboxAnnotation() {
        byte[] actual = new EntryTypeExtraField(ZipEntryType.MAILBOX_ANNOTATION).getLocalFileDataData();
        assertThat(actual).isEqualTo(TWO_AS_BYTE_ARRAY);
    }

    @Test
    void getLocalFileDataShouldReturnValueInLittleIndianWhenMessage() {
        byte[] actual = new EntryTypeExtraField(ZipEntryType.MESSAGE).getLocalFileDataData();
        assertThat(actual).isEqualTo(THREE_AS_BYTE_ARRAY);
    }

    @Test
    void getCentralDirectoryDataShouldThrowWhenNoValue() {
        assertThatThrownBy(() -> testee.getCentralDirectoryData())
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getCentralDirectoryDataShouldReturnValueInLittleIndianWhenMailbox() {
        byte[] actual = new EntryTypeExtraField(ZipEntryType.MAILBOX).getCentralDirectoryData();
        assertThat(actual).isEqualTo(ZERO_AS_BYTE_ARRAY);
    }

    @Test
    void getCentralDirectoryDataShouldReturnValueInLittleIndianWhenMailboxAnnotationDir() {
        byte[] actual = new EntryTypeExtraField(ZipEntryType.MAILBOX_ANNOTATION_DIR).getCentralDirectoryData();
        assertThat(actual).isEqualTo(ONE_AS_BYTE_ARRAY);
    }

    @Test
    void getCentralDirectoryDataShouldReturnValueInLittleIndianWhenMailboxAnnotation() {
        byte[] actual = new EntryTypeExtraField(ZipEntryType.MAILBOX_ANNOTATION).getCentralDirectoryData();
        assertThat(actual).isEqualTo(TWO_AS_BYTE_ARRAY);
    }

    @Test
    void getCentralDirectoryDataShouldReturnValueInLittleIndianWhenMessage() {
        byte[] actual = new EntryTypeExtraField(ZipEntryType.MESSAGE).getCentralDirectoryData();
        assertThat(actual).isEqualTo(THREE_AS_BYTE_ARRAY);
    }

    @Test
    void parseFromLocalFileDataShouldThrownWhenLengthIsSmallerThan8() {
        byte[] input = new byte[7];
        assertThatThrownBy(() -> testee.parseFromLocalFileData(input, 0, 7))
            .isInstanceOf(ZipException.class);
    }

    @Test
    void parseFromLocalFileDataShouldThrownWhenLengthIsBiggerThan8() {
        byte[] input = new byte[8];
        assertThatThrownBy(() -> testee.parseFromLocalFileData(input, 0, 9))
            .isInstanceOf(ZipException.class);
    }

    @Test
    void parseFromCentralDirectoryDataShouldThrownWhenLengthIsSmallerThan8() {
        byte[] input = new byte[7];
        assertThatThrownBy(() -> testee.parseFromCentralDirectoryData(input, 0, 7))
            .isInstanceOf(ZipException.class);
    }

    @Test
    void parseFromCentralDirectoryDataShouldThrownWhenLengthIsBiggerThan8() {
        byte[] input = new byte[9];
        assertThatThrownBy(() -> testee.parseFromCentralDirectoryData(input, 0, 9))
            .isInstanceOf(ZipException.class);
    }

    @Test
    void parseFromCentralDirectoryDataShouldParseWhenNotAnEnumValue() throws Exception {
        testee.parseFromCentralDirectoryData(FOUR_AS_BYTE_ARRAY, 0, 8);
        assertThat(testee.getValue())
            .contains(4L);

        assertThat(testee.getEnumValue())
            .isEqualTo(Optional.empty());
    }

}
