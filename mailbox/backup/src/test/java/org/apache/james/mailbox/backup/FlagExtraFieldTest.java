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
import java.nio.charset.StandardCharsets;

import jakarta.mail.Flags;

import org.apache.james.mailbox.backup.zip.FlagsExtraField;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.base.Charsets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

class FlagExtraFieldTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(FlagsExtraField.class)
            .suppress(Warning.NONFINAL_FIELDS)
            .verify();
    }

    @Nested
    class GetHeaderId {

        @Test
        void getHeaderIdShouldReturnSpecificStringInLittleEndian() {
            FlagsExtraField testee = new FlagsExtraField();
            ByteBuffer byteBuffer = ByteBuffer.wrap(testee.getHeaderId().getBytes())
                .order(ByteOrder.LITTLE_ENDIAN);

            assertThat(Charsets.US_ASCII.decode(byteBuffer).toString())
                .isEqualTo("ap");
        }
    }

    @Nested
    class GetLocalFileDataLength {

        @Test
        void getLocalFileDataLengthShouldThrowWhenNoValue() {
            FlagsExtraField testee = new FlagsExtraField();

            assertThatThrownBy(() -> testee.getLocalFileDataLength().getValue())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getLocalFileDataLengthShouldReturnIntegerSize() {
            FlagsExtraField testee = new FlagsExtraField(new Flags());

            assertThat(testee.getLocalFileDataLength().getValue())
                .isEqualTo(0);
        }

        @Test
        void getLocalFileDataLengthShouldReturnIntegerSizeWhenSystemFlagSet() {
            Flags flags = new Flags();
            flags.add(Flags.Flag.SEEN);
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getLocalFileDataLength().getValue())
                .isEqualTo(5);
        }

        @Test
        void getLocalFileDataLengthShouldReturnIntegerSizeWhenUserFlagSet() {
            Flags flags = new Flags("myFlags");
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getLocalFileDataLength().getValue())
                .isEqualTo(7);
        }

        @Test
        void getLocalFileDataLengthShouldReturnIntegerSizeWhenSystemAndUserFlagSet() {
            Flags flags = new Flags("myFlags");
            flags.add(Flags.Flag.ANSWERED);
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getLocalFileDataLength().getValue())
                .isEqualTo(17);
        }
    }

    @Nested
    class GetCentralDirectoryLength {

        @Test
        void getCentralDirectoryLengthShouldThrowWhenNoValue() {
            FlagsExtraField testee = new FlagsExtraField();

            assertThatThrownBy(() -> testee.getCentralDirectoryLength().getValue())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getCentralDirectoryLengthShouldReturnIntegerSize() {
            FlagsExtraField testee = new FlagsExtraField(new Flags());

            assertThat(testee.getCentralDirectoryLength().getValue())
                .isEqualTo(0);
        }

        @Test
        void getCentralDirectoryLengthShouldReturnIntegerSizeWhenSystemFlagSet() {
            Flags flags = new Flags();
            flags.add(Flags.Flag.SEEN);
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getCentralDirectoryLength().getValue())
                .isEqualTo(5);
        }

        @Test
        void getCentralDirectoryLengthShouldReturnIntegerSizeWhenUserFlagSet() {
            Flags flags = new Flags("myFlags");
            flags.add("newFlag");
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getCentralDirectoryLength().getValue())
                .isEqualTo(15);
        }

        @Test
        void getLocalFileDataLengthShouldReturnIntegerSizeWhenSystemAndUserFlagSet() {
            Flags flags = new Flags("myFlags");
            flags.add(Flags.Flag.ANSWERED);
            flags.add(Flags.Flag.SEEN);
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getCentralDirectoryLength().getValue())
                .isEqualTo(23);
        }
    }

    @Nested
    class GetLocalFileData {

        @Test
        void getLocalFileDataDataShouldThrowWhenNoValue() {
            FlagsExtraField testee = new FlagsExtraField();

            assertThatThrownBy(() -> testee.getLocalFileDataData())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getLocalFileDataDataShouldReturnByteArraysOfSystemFlagSet() {
            Flags flags = new Flags();
            flags.add(Flags.Flag.SEEN);
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getLocalFileDataData())
                .isEqualTo("\\SEEN".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void getLocalFileDataDataShouldReturnByteArrayOfUserFlagSet() {
            Flags flags = new Flags("myFlags");
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getLocalFileDataData())
                .isEqualTo("myFlags".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void getLocalFileDataDataShouldReturnByteArrayOfSystemAndUserFlagSet() {
            Flags flags = new Flags("myFlags");
            flags.add(Flags.Flag.ANSWERED);
            flags.add(Flags.Flag.SEEN);
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getLocalFileDataData())
                .isEqualTo("\\ANSWERED%\\SEEN%myFlags".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nested
    class GetCentralDirectoryData {

        @Test
        void getCentralDirectoryDataShouldThrowWhenNoValue() {
            FlagsExtraField testee = new FlagsExtraField();

            assertThatThrownBy(() -> testee.getCentralDirectoryData())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void getCentralDirectoryDataShouldReturnByteArraysOfSystemFlagSet() {
            Flags flags = new Flags();
            flags.add(Flags.Flag.SEEN);
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getCentralDirectoryData())
                .isEqualTo("\\SEEN".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void getCentralDirectoryDataShouldReturnByteArrayOfUserFlagSet() {
            Flags flags = new Flags("myFlags");
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getCentralDirectoryData())
                .isEqualTo("myFlags".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void getCentralDirectoryDataShouldReturnByteArrayOfSystemAndUserFlagSet() {
            Flags flags = new Flags("myFlags");
            flags.add(Flags.Flag.ANSWERED);
            flags.add(Flags.Flag.SEEN);
            FlagsExtraField testee = new FlagsExtraField(flags);

            assertThat(testee.getCentralDirectoryData())
                .isEqualTo("\\ANSWERED%\\SEEN%myFlags".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nested
    class ParseFromLocalFileData {

        @Test
        void parseFromLocalFileDataShouldParseByteData() {
            String bufferContent = "\\ANSWERED%\\SEEN%myFlags";
            Flags flags = new Flags("myFlags");
            flags.add(Flags.Flag.ANSWERED);
            flags.add(Flags.Flag.SEEN);

            FlagsExtraField testee = new FlagsExtraField(new Flags());
            testee.parseFromLocalFileData(bufferContent
                .getBytes(StandardCharsets.UTF_8), 0, 23);
            assertThat(testee.getValue()).contains(bufferContent);
        }

        @Test
        void parseFromLocalFileDataShouldParseByteDataWhenOffsetSet() {
            String bufferContent = "\\ANSWERED%\\SEEN%myFlags";
            Flags flags = new Flags("myFlags");
            flags.add(Flags.Flag.SEEN);

            FlagsExtraField testee = new FlagsExtraField(new Flags());
            testee.parseFromLocalFileData(bufferContent
                .getBytes(StandardCharsets.UTF_8), 10, 13);
            assertThat(testee.getValue()).contains("\\SEEN%myFlags");
        }
    }

    @Nested
    class ParseFromCentralDirectoryData {

        @Test
        void parseFromCentralDirectoryDataShouldParseByteData() {
            String bufferContent = "\\ANSWERED%\\SEEN%myFlags";
            Flags flags = new Flags("myFlags");
            flags.add(Flags.Flag.ANSWERED);
            flags.add(Flags.Flag.SEEN);

            FlagsExtraField testee = new FlagsExtraField(new Flags());
            testee.parseFromCentralDirectoryData(bufferContent
                .getBytes(StandardCharsets.UTF_8), 0, 23);
            assertThat(testee.getValue()).contains(bufferContent);
        }

        @Test
        void parseFromCentralDirectoryDataShouldParseByteDataWhenOffsetSet() {
            String bufferContent = "\\ANSWERED%\\SEEN%myFlags";
            Flags flags = new Flags("myFlags");
            flags.add(Flags.Flag.SEEN);

            FlagsExtraField testee = new FlagsExtraField(new Flags());
            testee.parseFromCentralDirectoryData(bufferContent
                .getBytes(StandardCharsets.UTF_8), 10, 13);
            assertThat(testee.getValue()).contains("\\SEEN%myFlags");
        }
    }
}
