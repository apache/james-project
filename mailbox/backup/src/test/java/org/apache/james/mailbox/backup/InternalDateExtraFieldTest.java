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
import java.util.Date;
import java.util.zip.ZipException;

import org.apache.james.mailbox.backup.zip.InternalDateExtraField;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.base.Charsets;
import com.google.common.primitives.Bytes;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

class InternalDateExtraFieldTest {
    private static final byte[] ZERO_AS_BYTE_ARRAY = {0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] _123456789ABCDEF0_AS_LE_BYTE_ARRAY = new byte[] {(byte) 0xF0, (byte) 0xDE, (byte) 0xBC, (byte) 0x9A, 0x78, 0x56, 0x34, 0x12};
    private static final byte[] FEDCBA9876543210_AS_LE_BYTE_ARRAY = new byte[] {0x10, 0x32, 0x54, 0x76, (byte) 0x98, (byte) 0xBA, (byte) 0xDC, (byte) 0xFE};
    private static final byte[] UNUSED = new byte[] {(byte) 0xDE, (byte) 0xAD};

    private static final long DEFAULT_DATE_TIMESTAMP = 1529559708381L;
    private static final byte[] DEFAULT_DATE_LE_BYTE_ARRAY = {(byte) 0xdd, (byte) 0xf2, (byte) 0xdc, 0x20, 0x64, 0x01, 0x00, 0x00 };
    private static final Date DEFAULT_DATE = new Date(DEFAULT_DATE_TIMESTAMP);

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(InternalDateExtraField.class)
            .suppress(Warning.NONFINAL_FIELDS)
            .verify();
    }

    @Nested
    class GetHeaderId {

        @Test
        void getHeaderIdShouldReturnSpecificStringInLittleEndian() {
            InternalDateExtraField testee = new InternalDateExtraField();

            ByteBuffer byteBuffer = ByteBuffer.wrap(testee.getHeaderId().getBytes())
                .order(ByteOrder.LITTLE_ENDIAN);
            assertThat(Charsets.US_ASCII.decode(byteBuffer).toString())
                .isEqualTo("ao");
        }
    }

    @Nested
    class GetLocalFileDataLength {
        @Test
        void getLocalFileDataLengthShouldReturnIntegerSize() {
            InternalDateExtraField testee = new InternalDateExtraField();

            assertThat(testee.getLocalFileDataLength().getValue())
                .isEqualTo(Long.BYTES);
        }
    }

    @Nested
    class GetCentralDirectoryLength {

        @Test
        void getCentralDirectoryLengthShouldReturnIntegerSize() {
            InternalDateExtraField testee = new InternalDateExtraField();

            assertThat(testee.getCentralDirectoryLength().getValue())
                .isEqualTo(Long.BYTES);
        }


        @Test
        void getCentralDirectoryDataShouldThrowWhenNoValue() {
            InternalDateExtraField testee = new InternalDateExtraField();

            assertThatThrownBy(() -> testee.getCentralDirectoryData())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getCentralDirectoryDataShouldReturnZeroWhenZero() {
            byte[] actual = new InternalDateExtraField(0).getCentralDirectoryData();
            assertThat(actual).isEqualTo(ZERO_AS_BYTE_ARRAY);
        }

        @Test
        void getCentralDirectoryDataShouldReturnValueInLittleIndianWhen123456789ABCDEF0() {
            byte[] actual = new InternalDateExtraField(0x123456789ABCDEF0L).getCentralDirectoryData();
            assertThat(actual).isEqualTo(_123456789ABCDEF0_AS_LE_BYTE_ARRAY);
        }

        @Test
        void getCentralDirectoryDataShouldReturnValueInLittleIndianWhenFEDCBA9876543210() {
            byte[] actual = new InternalDateExtraField(0xFEDCBA9876543210L).getCentralDirectoryData();
            assertThat(actual).isEqualTo(FEDCBA9876543210_AS_LE_BYTE_ARRAY);
        }

        @Test
        void getCentralDirectoryDataShouldReturnDefaultDateWhenPassDefaultDateInByteArray() {
            byte[] actual = new InternalDateExtraField(DEFAULT_DATE).getCentralDirectoryData();

            assertThat(actual).isEqualTo(DEFAULT_DATE_LE_BYTE_ARRAY);
        }
    }

    @Nested
    class GetLocalFileDataData {

        @Test
        void getLocalFileDataDataShouldThrowWhenNoValue() {
            InternalDateExtraField testee = new InternalDateExtraField();

            assertThatThrownBy(() -> testee.getLocalFileDataData())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getLocalFileDataDataShouldReturnZeroWhenZero() {
            byte[] actual = new InternalDateExtraField(0).getLocalFileDataData();
            assertThat(actual).isEqualTo(ZERO_AS_BYTE_ARRAY);
        }

        @Test
        void getLocalFileDataDataShouldReturnValueInLittleIndianWhen123456789ABCDEF0() {
            byte[] actual = new InternalDateExtraField(0x123456789ABCDEF0L).getLocalFileDataData();
            assertThat(actual).isEqualTo(_123456789ABCDEF0_AS_LE_BYTE_ARRAY);
        }

        @Test
        void getLocalFileDataDataShouldReturnValueInLittleIndianWhenFEDCBA9876543210() {
            byte[] actual = new InternalDateExtraField(0xFEDCBA9876543210L).getLocalFileDataData();
            assertThat(actual).isEqualTo(FEDCBA9876543210_AS_LE_BYTE_ARRAY);
        }

        @Test
        void getLocalFileDataDataShouldReturnDefaultDateWhenPassDefaultDateInByteArray() {
            byte[] actual = new InternalDateExtraField(DEFAULT_DATE).getLocalFileDataData();

            assertThat(actual).isEqualTo(DEFAULT_DATE_LE_BYTE_ARRAY);
        }
    }

    @Nested
    class ParseFromLocalFileData {

        @Test
        void parseFromLocalFileDataShouldThrownWhenLengthIsSmallerThan8() {
            InternalDateExtraField testee = new InternalDateExtraField();

            byte[] input = new byte[] {0, 0, 0, 0, 0, 0, 0};
            assertThatThrownBy(() -> testee.parseFromLocalFileData(input, 0, 7))
                .isInstanceOf(ZipException.class);
        }

        @Test
        void parseFromLocalFileDataShouldThrownWhenLengthIsBiggerThan8() {
            InternalDateExtraField testee = new InternalDateExtraField();

            byte[] input = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0};
            assertThatThrownBy(() -> testee.parseFromLocalFileData(input, 0, 9))
                .isInstanceOf(ZipException.class);
        }

        @Test
        void parseFromLocalFileDataShouldParseWhenZero() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();

            testee.parseFromLocalFileData(ZERO_AS_BYTE_ARRAY, 0, 8);
            assertThat(testee.getValue())
                .contains(0L);
        }

        @Test
        void parseFromLocalFileDataShouldParseWhen123456789ABCDEF0InLittleEndian() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();

            testee.parseFromLocalFileData(_123456789ABCDEF0_AS_LE_BYTE_ARRAY, 0, 8);
            assertThat(testee.getValue())
                .contains(0x123456789ABCDEF0L);
        }

        @Test
        void parseFromLocalFileDataShouldParseWhenFEDCBA9876543210InLittleEndian() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();

            byte[] input = FEDCBA9876543210_AS_LE_BYTE_ARRAY;
            testee.parseFromLocalFileData(input, 0, 8);
            assertThat(testee.getValue())
                .contains(0xFEDCBA9876543210L);
        }

        @Test
        void parseFromLocalFileDataShouldHandleOffset() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();

            byte[] input = Bytes.concat(UNUSED, _123456789ABCDEF0_AS_LE_BYTE_ARRAY);
            testee.parseFromLocalFileData(input, 2, 8);
            assertThat(testee.getValue())
                .contains(0x123456789ABCDEF0L);
        }

        @Test
        void parseFromLocalFileDataShouldReturnZeroDayWhenZero() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();

            testee.parseFromLocalFileData(ZERO_AS_BYTE_ARRAY, 0, 8);

            assertThat(testee.getDateValue())
                .contains(new Date(0L));
        }

        @Test
        void parseFromLocalFileDataShouldReturnDefaultDateWhenPassDefaultUTCDateByteArray() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();
            testee.parseFromLocalFileData(DEFAULT_DATE_LE_BYTE_ARRAY, 0, 8);

            assertThat(testee.getDateValue())
                .contains(DEFAULT_DATE);
        }
    }

    @Nested
    class ParseFromCentralDirectoryData {

        @Test
        void parseFromCentralDirectoryDataShouldThrownWhenLengthIsSmallerThan8() {
            InternalDateExtraField testee = new InternalDateExtraField();
            byte[] input = new byte[7];

            assertThatThrownBy(() -> testee.parseFromCentralDirectoryData(input, 0, 7))
                .isInstanceOf(ZipException.class);
        }

        @Test
        void parseFromCentralDirectoryDataShouldThrownWhenLengthIsBiggerThan8() {
            InternalDateExtraField testee = new InternalDateExtraField();
            byte[] input = new byte[9];

            assertThatThrownBy(() -> testee.parseFromCentralDirectoryData(input, 0, 9))
                .isInstanceOf(ZipException.class);
        }

        @Test
        void parseFromCentralDirectoryDataShouldParseWhenZero() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();

            testee.parseFromCentralDirectoryData(ZERO_AS_BYTE_ARRAY, 0, 8);
            assertThat(testee.getValue())
                .contains(0L);
        }

        @Test
        void parseFromCentralDirectoryDataShouldParseWhen123456789ABCDEF0InLittleEndian() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();

            testee.parseFromCentralDirectoryData(_123456789ABCDEF0_AS_LE_BYTE_ARRAY, 0, 8);
            assertThat(testee.getValue())
                .contains(0x123456789ABCDEF0L);
        }

        @Test
        void parseFromCentralDirectoryDataShouldParseWhenFEDCBA9876543210InLittleEndian() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();
            byte[] input = FEDCBA9876543210_AS_LE_BYTE_ARRAY;

            testee.parseFromCentralDirectoryData(input, 0, 8);
            assertThat(testee.getValue())
                .contains(0xFEDCBA9876543210L);
        }

        @Test
        void parseFromCentralDirectoryDataShouldHandleOffset() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();
            byte[] input = Bytes.concat(UNUSED, _123456789ABCDEF0_AS_LE_BYTE_ARRAY);

            testee.parseFromCentralDirectoryData(input, 2, 8);
            assertThat(testee.getValue())
                .contains(0x123456789ABCDEF0L);
        }

        @Test
        void parseFromCentralDirectoryDataShouldReturnZeroDayWhenZero() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();

            testee.parseFromCentralDirectoryData(ZERO_AS_BYTE_ARRAY, 0, 8);

            assertThat(testee.getDateValue())
                .contains(new Date(0L));
        }

        @Test
        void parseFromCentralDirectoryDataShouldReturnDefaultDateWhenPassDefaultUTCDateByteArray() throws Exception {
            InternalDateExtraField testee = new InternalDateExtraField();
            testee.parseFromCentralDirectoryData(DEFAULT_DATE_LE_BYTE_ARRAY, 0, 8);

            assertThat(testee.getDateValue())
                .contains(DEFAULT_DATE);
        }
    }
}
