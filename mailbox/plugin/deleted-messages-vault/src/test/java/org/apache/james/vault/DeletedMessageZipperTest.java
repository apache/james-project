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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.james.mailbox.backup.MessageIdExtraField;
import org.apache.james.mailbox.backup.SizeExtraField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

class DeletedMessageZipperTest {

    private class ZipArchiveStreamCaptor implements Answer<ZipArchiveOutputStream> {

        ZipArchiveOutputStream captured;

        @Override
        public ZipArchiveOutputStream answer(InvocationOnMock invocation) throws Throwable {
            ZipArchiveOutputStream zipArchiveOutputStream = (ZipArchiveOutputStream) invocation.callRealMethod();
            captured = spy(zipArchiveOutputStream);
            return captured;
        }
    }

    private class LoadedResourcesStreamCaptor implements Answer<Optional<DeletedMessageWithContent>> {

        List<DeletedMessageWithContent> captured = new ArrayList<>();

        @Override
        public Optional<DeletedMessageWithContent> answer(InvocationOnMock invocation) throws Throwable {
            Optional<DeletedMessageWithContent> messageWithContent = (Optional<DeletedMessageWithContent>) invocation.callRealMethod();
            return messageWithContent.map(this::returnSpied);
        }

        private DeletedMessageWithContent returnSpied(DeletedMessageWithContent message) {
            DeletedMessageWithContent spied = spy(message);
            captured.add(spied);
            return spied;
        }
    }

    private static final DeletedMessageContentLoader CONTENT_LOADER = message -> Mono.just(new ByteArrayInputStream(CONTENT));
    private static final String MESSAGE_CONTENT = new String(CONTENT, StandardCharsets.UTF_8);
    private DeletedMessageZipper zipper;

    @BeforeEach
    void beforeEach() {
        zipper = spy(new DeletedMessageZipper());
    }

    @Test
    void zipShouldPutEntriesToOutputStream() throws Exception {
        ByteArrayOutputStream messageContents = zip(Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2));
        try (ZipFile zipFile = zipFile(messageContents)) {
            assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(MESSAGE_ID.serialize()).hasStringContent(MESSAGE_CONTENT),
                    hasName(MESSAGE_ID_2.serialize()).hasStringContent(MESSAGE_CONTENT));
        }
    }

    @Test
    void zipShouldPutExtraFields() throws Exception {
        ByteArrayOutputStream messageContents = zip(Stream.of(DELETED_MESSAGE));
        try (ZipFile zipFile = zipFile(messageContents)) {
            assertThatZip(zipFile)
                .containsOnlyEntriesMatching(
                    hasName(MESSAGE_ID.serialize())
                        .containsExtraFields(new MessageIdExtraField(MESSAGE_ID))
                        .containsExtraFields(new SizeExtraField(CONTENT.length)));
        }
    }

    @Test
    void constructorShouldNotFailWhenCalledMultipleTimes() {
        assertThatCode(() -> {
                new DeletedMessageZipper();
                new DeletedMessageZipper();
            }).doesNotThrowAnyException();
    }

    @Test
    void zipShouldCloseAllResourcesStreamWhenFinishZipping() throws Exception {
        LoadedResourcesStreamCaptor captor = new LoadedResourcesStreamCaptor();
        doAnswer(captor)
            .when(zipper).messageWithContent(any(), any());

        zip(Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2));

        List<DeletedMessageWithContent> loadedResources = captor.captured;
        assertThat(loadedResources)
            .hasSize(2);
        loadedResources.stream()
            .forEach(Throwing.consumer(spiedMessage -> verify(spiedMessage, times(1)).close()));
    }

    @Test
    void zipShouldTerminateZipArchiveStreamWhenFinishZipping() throws Exception {
        ZipArchiveStreamCaptor captor = new ZipArchiveStreamCaptor();
        doAnswer(captor)
            .when(zipper).newZipArchiveOutputStream(any());

        zip(Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2));

        ZipArchiveOutputStream captured = captor.captured;
        verify(captured, times(1)).finish();
        verify(captured, times(1)).close();
    }

    @Test
    void zipShouldThrowWhenCreateEntryGetException() throws Exception {
        doThrow(new IOException("mocked exception"))
            .when(zipper).createEntry(any(), any());

        assertThatThrownBy(() -> zip(Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2)))
            .isInstanceOf(IOException.class);
    }

    @Test
    void zipShouldThrowWhenPutMessageToEntryGetException() throws Exception {
        doThrow(new IOException("mocked exception"))
            .when(zipper).putMessageToEntry(any(), any());

        assertThatThrownBy(() -> zip(Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2)))
            .isInstanceOf(IOException.class);
    }

    @Test
    void zipShouldStopLoadingResourcesWhenGettingException() throws Exception {
        doThrow(new IOException("mocked exception"))
            .when(zipper).createEntry(any(), any());

        LoadedResourcesStreamCaptor captor = new LoadedResourcesStreamCaptor();
        doAnswer(captor)
            .when(zipper).messageWithContent(any(), any());

        assertThatThrownBy(() -> zip(Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2)))
            .isInstanceOf(IOException.class);

        List<DeletedMessageWithContent> loadedResources = captor.captured;
        assertThat(loadedResources)
            .hasSize(1);
        loadedResources.stream()
            .forEach(Throwing.consumer(spiedMessage -> verify(spiedMessage, times(1)).close()));
    }

    @Test
    void zipShouldCloseParameterOutputStreamWhenGettingException() throws Exception {
        doThrow(new IOException("mocked exception"))
            .when(zipper).putMessageToEntry(any(), any());

        ByteArrayOutputStream outputStream = spy(new ByteArrayOutputStream());
        assertThatThrownBy(() -> zipper.zip(CONTENT_LOADER, Stream.of(DELETED_MESSAGE), outputStream))
            .isInstanceOf(IOException.class);

        verify(outputStream, times(1)).close();
    }

    @Test
    void zipShouldTerminateZipArchiveStreamWhenGettingException() throws Exception {
        doThrow(new IOException("mocked exception"))
            .when(zipper).putMessageToEntry(any(), any());

        ZipArchiveStreamCaptor captor = new ZipArchiveStreamCaptor();
        doAnswer(captor)
            .when(zipper).newZipArchiveOutputStream(any());

        assertThatThrownBy(() -> zip(Stream.of(DELETED_MESSAGE, DELETED_MESSAGE_2)))
            .isInstanceOf(IOException.class);

        ZipArchiveOutputStream captured = captor.captured;
        verify(captured, times(1)).finish();
        verify(captured, times(1)).close();
    }

    private ByteArrayOutputStream zip(Stream<DeletedMessage> deletedMessages, DeletedMessageZipper zipper) throws IOException {
        ByteArrayOutputStream zipOutputStream = new ByteArrayOutputStream();
        zipper.zip(CONTENT_LOADER, deletedMessages, zipOutputStream);
        return zipOutputStream;
    }

    private ByteArrayOutputStream zip(Stream<DeletedMessage> deletedMessages) throws IOException {
        return zip(deletedMessages, zipper);
    }

    private ZipFile zipFile(ByteArrayOutputStream output) throws IOException {
        return new ZipFile(new SeekableInMemoryByteChannel(output.toByteArray()));
    }
}