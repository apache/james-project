/******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                 *
 * or more contributor license agreements.  See the NOTICE file               *
 * distributed with this work for additional information                      *
 * regarding copyright ownership.  The ASF licenses this file                 *
 * to you under the Apache License, Version 2.0 (the                          *
 * "License"); you may not use this file except in compliance                 *
 * with the License.  You may obtain a copy of the License at                 *
 *                                                                            *
 *   http://www.apache.org/licenses/LICENSE-2.0                               *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing,                 *
 * software distributed under the License is distributed on an                *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                     *
 * KIND, either express or implied.  See the License for the                  *
 * specific language governing permissions and limitations                    *
 * under the License.                                                         *
 ******************************************************************************/

package org.apache.james.jdkim.mailets;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class CRLFInputStreamTest {
    @Nested
    class ByteByByteRead {
        @Test
        void should_leave_no_stranded_cr_or_lf() {
            var originalText = "line 1\r\n" +
                    "line2\r" +
                    "line3\n" +
                    "line4";
            var expectedText = "line 1\r\n" +
                    "line2\r\n" +
                    "line3\r\n" +
                    "line4";
            read(originalText, expectedText);

        }

        @Test
        void should_leave_no_stranded_cr_at_end_of_stream() {
            var originalText = "line 1\r";
            var expectedText = "line 1\r\n";
            read(originalText, expectedText);
        }

        @Test
        void should_leave_no_stranded_lf_at_end_of_stream() {
            var originalText = "line 1\n";
            var expectedText = "line 1\r\n";
            read(originalText, expectedText);
        }

        private static void read(String originalText, String expectedText) {
            try (CRLFInputStream crlfInputStream = new CRLFInputStream(new ByteArrayInputStream(originalText.getBytes(StandardCharsets.UTF_8)))) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                int read;
                while ((read = crlfInputStream.read()) != -1) {
                    byteArrayOutputStream.write(read);
                }
                crlfInputStream.close();
                byteArrayOutputStream.close();
                var actualText = byteArrayOutputStream.toString(StandardCharsets.UTF_8);
                assertThat(actualText).isEqualTo(expectedText);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Nested
    class BufferRead {
        @Test
        void should_leave_no_stranded_cr_or_lf() {
            var originalText = "line 1\r\n" +
                    "line2\r" +
                    "line3\n" +
                    "line4";
            var expectedText = "line 1\r\n" +
                    "line2\r\n" +
                    "line3\r\n" +
                    "line4";
            read(originalText, expectedText);

        }

        @Test
        void should_leave_no_stranded_cr_at_end_of_stream() {
            var originalText = "line 1\r";
            var expectedText = "line 1\r\n";
            read(originalText, expectedText);
        }

        @Test
        void should_leave_no_stranded_lf_at_end_of_stream() {
            var originalText = "line 1\n";
            var expectedText = "line 1\r\n";
            read(originalText, expectedText);
        }

        private static void read(String originalText, String expectedText) {
            try (CRLFInputStream in = new CRLFInputStream(new ByteArrayInputStream(originalText.getBytes(StandardCharsets.UTF_8)))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[6];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.close();
                var actualText = out.toString(StandardCharsets.UTF_8);
                assertThat(actualText).isEqualTo(expectedText);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nested
    class BufferOffsetRead {
        @Test
        void should_leave_no_stranded_cr_or_lf() {
            var originalText = "line 1\r\n" +
                    "line2\r" +
                    "line3\n" +
                    "line4";
            var expectedText = "line 1\r\n" +
                    "line2\r\n" +
                    "line3\r\n" +
                    "line4";
            read(originalText, expectedText);

        }

        @Test
        void should_leave_no_stranded_cr_at_end_of_stream() {
            var originalText = "line 1\r";
            var expectedText = "line 1\r\n";
            read(originalText, expectedText);
        }

        @Test
        void should_leave_no_stranded_lf_at_end_of_stream() {
            var originalText = "line 1\n";
            var expectedText = "line 1\r\n";
            read(originalText, expectedText);
        }

        private static void read(String originalText, String expectedText) {
            try (CRLFInputStream in = new CRLFInputStream(new ByteArrayInputStream(originalText.getBytes(StandardCharsets.UTF_8)))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                in.transferTo(out);
                in.close();
                out.close();
                var actualText = out.toString(StandardCharsets.UTF_8);
                assertThat(actualText).isEqualTo(expectedText);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}