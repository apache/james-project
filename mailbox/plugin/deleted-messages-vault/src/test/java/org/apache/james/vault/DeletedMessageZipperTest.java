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

package org.apache.james.vault;

import static org.apache.james.mailbox.backup.ZipAssert.EntryChecks.hasName;
import static org.apache.james.mailbox.backup.ZipAssert.assertThatZip;
import static org.apache.james.vault.DeletedMessageFixture.CONTENT;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.DeletedMessageFixture.MESSAGE_ID;
import static org.apache.james.vault.DeletedMessageFixture.MESSAGE_ID_2;
import static org.apache.james.vault.DeletedMessageZipper.DeletedMessageContentLoader;
import static org.apache.james.vault.DeletedMessageZipper.EML_FILE_EXTENSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.james.mailbox.backup.zip.MessageIdExtraField;
import org.apache.james.mailbox.backup.zip.SizeExtraField;
import org.apache.james.mailbox.backup.ZipAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import com.github.fge.lambdas.Throwing;

class DeletedMessageZipperTest {
    private static final DeletedMessageContentLoader CONTENT_LOADER = message -> Optional.of(new ByteArrayInputStream(CONTENT));
    private static final String MESSAGE_CONTENT = new String(CONTENT, StandardCharsets.UTF_8);
    private DeletedMessageZipper zipper;

    @BeforeEach
    void beforeEach() {
        zipper = spy(new DeletedMessageZipper());
    }

    @Nested
    class NormalBehaviourTest {
        @Test
        void constructorShouldNotFailWhenCalledMultipleTimes() {
            assertThatCode(() -> {
                new DeletedMessageZipper();
                new DeletedMessageZipper();
            }).doesNotThrowAnyException();
        }

        @Test
        void zipShouldPutEntriesToOutputStream() throws Exception {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            zipper.zip(CONTENT_LOADER, Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2), outputStream);

            try (ZipAssert zipAssert = assertThatZip(outputStream)) {
                zipAssert.containsOnlyEntriesMatching(
                        hasName(MESSAGE_ID.serialize() + EML_FILE_EXTENSION).hasStringContent(MESSAGE_CONTENT),
                        hasName(MESSAGE_ID_2.serialize() + EML_FILE_EXTENSION).hasStringContent(MESSAGE_CONTENT));
            }
        }

        @Test
        void zipShouldPutExtraFields() throws Exception {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            zipper.zip(CONTENT_LOADER, Stream.of(DELETED_MESSAGE), outputStream);

            try (ZipAssert zipAssert = assertThatZip(outputStream)) {
                zipAssert.containsOnlyEntriesMatching(
                        hasName(MESSAGE_ID.serialize() + EML_FILE_EXTENSION)
                            .containsExtraFields(new MessageIdExtraField(MESSAGE_ID))
                            .containsExtraFields(new SizeExtraField(CONTENT.length)));
            }
        }

        @Test
        void zipShouldTerminateZipArchiveStreamWhenFinishZipping() throws Exception {
            AtomicReference<ZipArchiveOutputStream> zipOutputStreamReference = new AtomicReference<>();
            when(zipper.newZipArchiveOutputStream(any())).thenAnswer(spyZipOutPutStream(zipOutputStreamReference));

            zipper.zip(CONTENT_LOADER, Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2), new ByteArrayOutputStream());

            verify(zipOutputStreamReference.get(), times(1)).finish();
            verify(zipOutputStreamReference.get(), times(1)).close();
        }

        @Test
        void zipShouldCloseAllResourcesStreamWhenFinishZipping() throws Exception {
            Collection<InputStream> loadedContents = new ConcurrentLinkedQueue<>();

            zipper.zip(spyLoadedContents(loadedContents), Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2), new ByteArrayOutputStream());

            assertThat(loadedContents)
                .hasSize(2)
                .allSatisfy(Throwing.consumer(content -> verify(content, times(1)).close()));
        }
    }

    @Nested
    class FailingBehaviourTest {
        @Test
        void zipShouldThrowWhenCreateEntryGetException() throws Exception {
            doThrow(new IOException("mocked exception")).when(zipper).createEntry(any(), any());

            assertThatThrownBy(() -> zipper.zip(CONTENT_LOADER, Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2), new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class);
        }

        @Test
        void zipShouldThrowWhenPutMessageToEntryGetException() throws Exception {
            doThrow(new IOException("mocked exception")).when(zipper).putMessageToEntry(any(), any(), any());

            assertThatThrownBy(() -> zipper.zip(CONTENT_LOADER, Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2), new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class);
        }

        @Test
        void zipShouldTerminateZipArchiveStreamWhenGettingException() throws Exception {
            doThrow(new IOException("mocked exception")).when(zipper).putMessageToEntry(any(), any(), any());
            AtomicReference<ZipArchiveOutputStream> zipOutputStreamReference = new AtomicReference<>();
            when(zipper.newZipArchiveOutputStream(any())).thenAnswer(spyZipOutPutStream(zipOutputStreamReference));

            try {
                zipper.zip(CONTENT_LOADER, Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2), new ByteArrayOutputStream());
            } catch (Exception e) {
                // ignored
            }

            verify(zipOutputStreamReference.get(), times(1)).finish();
            verify(zipOutputStreamReference.get(), times(1)).close();
        }

        @Test
        void zipShouldCloseParameterOutputStreamWhenGettingException() throws Exception {
            doThrow(new IOException("mocked exception")).when(zipper).putMessageToEntry(any(), any(), any());
            ByteArrayOutputStream outputStream = spy(new ByteArrayOutputStream());

            try {
                zipper.zip(CONTENT_LOADER, Stream.of(DELETED_MESSAGE), outputStream);
            } catch (Exception e) {
                // ignored
            }

            verify(outputStream, times(1)).close();
        }

        @Test
        void zipShouldStopLoadingResourcesWhenGettingException() throws Exception {
            doThrow(new IOException("mocked exception")).when(zipper).createEntry(any(), any());
            // lambdas are final and thus can't be spied
            DeletedMessageContentLoader contentLoader = spy(new DeletedMessageContentLoader() {
                @Override
                public Optional<InputStream> load(DeletedMessage deletedMessage) {
                    return Optional.of(new ByteArrayInputStream(CONTENT));
                }
            });

            try {
                zipper.zip(contentLoader, Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2), new ByteArrayOutputStream());
            } catch (Exception e) {
                // ignored
            }

            verify(contentLoader, times(1)).load(any());
        }

        @Test
        void zipShouldNotPutEntryIfContentLoaderReturnsEmptyResult() throws Exception {
            DeletedMessageContentLoader contentLoader = message -> Optional.empty();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            zipper.zip(contentLoader, Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2), outputStream);

            try (ZipAssert zipAssert = assertThatZip(outputStream)) {
                zipAssert.hasNoEntry();
            }
        }
    }

    private DeletedMessageZipper.DeletedMessageContentLoader spyLoadedContents(Collection<InputStream> loadedContents) {
        Answer<Optional<InputStream>> spyedContent = invocationOnMock -> {
            InputStream spied = spy(new ByteArrayInputStream(CONTENT));
            loadedContents.add(spied);
            return Optional.of(spied);
        };
        DeletedMessageContentLoader contentLoader = mock(DeletedMessageContentLoader.class);
        when(contentLoader.load(any())).thenAnswer(spyedContent);
        return contentLoader;
    }

    private Answer<ZipArchiveOutputStream> spyZipOutPutStream(AtomicReference<ZipArchiveOutputStream> spiedZipArchiveOutputStream) {
        return invocation -> {
            ZipArchiveOutputStream zipArchiveOutputStream = spy((ZipArchiveOutputStream) invocation.callRealMethod());
            spiedZipArchiveOutputStream.set(zipArchiveOutputStream);
            return zipArchiveOutputStream;
        };
    }
}