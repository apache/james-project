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

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.james.mailbox.backup.zip.MessageIdExtraField;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.base.Charsets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

class MessageIdExtraFieldTest {

    private static final String DEFAULT_MESSAGE_ID = "123456789ABCDEF0";
    private static final byte[] DEFAULT_MESSAGE_ID_BYTE_ARRAY = new byte[] {0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x30};
    private static final byte [] EMPTY_BYTE_ARRAY = {};

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MessageIdExtraField.class)
            .suppress(Warning.NONFINAL_FIELDS)
            .verify();
    }

    @Nested
    class GetHeaderId {

        @Test
        void getHeaderIdShouldReturnSpecificStringInLittleEndian() {
            MessageIdExtraField testee = new MessageIdExtraField();
            ByteBuffer byteBuffer = ByteBuffer.wrap(testee.getHeaderId().getBytes())
                .order(ByteOrder.LITTLE_ENDIAN);

            assertThat(Charsets.US_ASCII.decode(byteBuffer).toString())
                .isEqualTo("al");
        }
    }

    @Nested
    class GetLocalFileDataLength {

        @Test
        void getLocalFileDataLengthShouldThrowWhenNoValue() {
            MessageIdExtraField testee = new MessageIdExtraField();

            assertThatThrownBy(() -> testee.getLocalFileDataLength().getValue())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getLocalFileDataLengthShouldReturnIntegerSize() {
            MessageIdExtraField testee = new MessageIdExtraField(DEFAULT_MESSAGE_ID);

            assertThat(testee.getLocalFileDataLength().getValue())
                .isEqualTo(16);
        }
    }

    @Nested
    class GetCentralDirectoryLength {

        @Test
        void getCentralDirectoryLengthShouldThrowWhenNoValue() {
            MessageIdExtraField testee = new MessageIdExtraField();

            assertThatThrownBy(() -> testee.getCentralDirectoryLength().getValue())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getCentralDirectoryLengthShouldReturnIntegerSize() {
            MessageIdExtraField testee = new MessageIdExtraField(DEFAULT_MESSAGE_ID);

            assertThat(testee.getCentralDirectoryLength().getValue())
                .isEqualTo(16);
        }
    }

    @Nested
    class GetLocalFileDataData {

        @Test
        void getLocalFileDataDataShouldThrowWhenNoValue() {
            MessageIdExtraField testee = new MessageIdExtraField();

            assertThatThrownBy(() -> testee.getLocalFileDataData())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getLocalFileDataDataShouldReturnEmptyArrayWhenValueIsEmpty() {
            byte[] actual = new MessageIdExtraField(EMPTY).getLocalFileDataData();

            assertThat(actual).isEqualTo(EMPTY_BYTE_ARRAY);
        }

        @Test
        void getLocalFileDataDataShouldReturnValueInByteArray() {
            byte[] actual = new MessageIdExtraField(DEFAULT_MESSAGE_ID).getLocalFileDataData();

            assertThat(actual).isEqualTo(DEFAULT_MESSAGE_ID_BYTE_ARRAY);
        }
    }

    @Nested
    class GetCentralDirectoryData {

        @Test
        void getCentralDirectoryDataShouldThrowWhenNoValue() {
            MessageIdExtraField testee = new MessageIdExtraField();

            assertThatThrownBy(() -> testee.getCentralDirectoryData())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getCentralDirectoryDataShouldReturnEmptyArrayWhenValueIsEmpty() {
            byte[] actual = new MessageIdExtraField(EMPTY).getCentralDirectoryData();
            assertThat(actual).isEqualTo(EMPTY_BYTE_ARRAY);
        }

        @Test
        void getCentralDirectoryDataShouldReturnValueInByteArray() {
            byte[] actual = new MessageIdExtraField(DEFAULT_MESSAGE_ID).getCentralDirectoryData();
            assertThat(actual).isEqualTo(DEFAULT_MESSAGE_ID_BYTE_ARRAY);
        }
    }

    @Nested
    class ParseFromLocalFileData {

        @Test
        void parseFromLocalFileDataShouldParseWhenZero() {
            MessageIdExtraField testee = new MessageIdExtraField();

            testee.parseFromLocalFileData(EMPTY_BYTE_ARRAY, 0, 0);

            assertThat(testee.getValue())
                .contains(EMPTY);
        }

        @Test
        void parseFromLocalFileDataShouldParseByteArray() {
            MessageIdExtraField testee = new MessageIdExtraField();

            testee.parseFromLocalFileData(DEFAULT_MESSAGE_ID_BYTE_ARRAY, 0, 16);

            assertThat(testee.getValue())
                .contains(DEFAULT_MESSAGE_ID);
        }

        @Test
        void parseFromLocalFileDataShouldHandleOffset() {
            MessageIdExtraField testee = new MessageIdExtraField();

            testee.parseFromLocalFileData(DEFAULT_MESSAGE_ID_BYTE_ARRAY, 2, 14);

            assertThat(testee.getValue())
                .contains("3456789ABCDEF0");
        }
    }

    @Nested
    class ParseFromCentralDirectoryData {

        @Test
        void parseFromCentralDirectoryDataShouldParseWhenZero() {
            MessageIdExtraField testee = new MessageIdExtraField();

            testee.parseFromCentralDirectoryData(EMPTY_BYTE_ARRAY, 0, 0);

            assertThat(testee.getValue())
                .contains(EMPTY);
        }

        @Test
        void parseFromCentralDirectoryDataShouldParseByteArray() {
            MessageIdExtraField testee = new MessageIdExtraField();

            testee.parseFromCentralDirectoryData(DEFAULT_MESSAGE_ID_BYTE_ARRAY, 0, 16);

            assertThat(testee.getValue())
                .contains(DEFAULT_MESSAGE_ID);
        }

        @Test
        void parseFromCentralDirectoryDataShouldHandleOffset() {
            MessageIdExtraField testee = new MessageIdExtraField();

            testee.parseFromCentralDirectoryData(DEFAULT_MESSAGE_ID_BYTE_ARRAY, 2, 14);

            assertThat(testee.getValue())
                .contains("3456789ABCDEF0");
        }
    }
    
}
