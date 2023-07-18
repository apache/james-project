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
package org.apache.james.filesystem.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import spark.Request;
import spark.Response;
import spark.Service;

@RunWith(JUnitParamsRunner.class)
public abstract class AbstractFileSystemTest {
    private static final int RANDOM_PORT = 0;
    private static final String FAKE_DIRECTORY = "b7b73e3a-5234-11e5-87f2-9b171f273b49/";
    private static final String FAKE_FILE = "d9091ae6-521f-11e5-b666-bb11fef67c2a";
    private static final String EXISTING_CLASSPATH_FILE = "classpathTest.txt";
    private static final String EXISTING_CLASSPATH_FILE_WITH_SPACES = "class path Test.txt";

    @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

    protected FileSystem fileSystem;
    
    private Service httpServer;
    private File rootDirectory;
    
    protected abstract FileSystem buildFileSystem(String configurationRootDirectory);

    @Before
    public void setUp() throws Exception {
        httpServer = Service.ignite().port(RANDOM_PORT);
        httpServer.get("/", (Request request, Response response) -> "content");
        httpServer.awaitInitialization();
        
        rootDirectory = tmpFolder.getRoot();
        createSubFolderWithAFileIn("conf", "conf.txt", "confcontent");
        createSubFolderWithAFileIn("var", "var.txt", "varcontent");
        
        fileSystem = buildFileSystem(rootDirectory.getAbsolutePath());
    }

    private void createSubFolderWithAFileIn(String folderName, String fileName, String fileContent) throws IOException {
        File folder = tmpFolder.newFolder(folderName);
        File file = new File(folder.getAbsolutePath() + "/" + fileName);
        try (var out = new FileOutputStream(file)) {
            out.write(fileContent.getBytes(StandardCharsets.UTF_8));
        }
    }

    @After
    public void tearDown() throws Exception {
        httpServer.stop();
    }

    @Test
    public final void getBaseDirShouldReturnParentDir() throws Exception {
        File basedir = fileSystem.getBasedir();
        assertThat(basedir.getPath()).isEqualTo(rootDirectory.getAbsolutePath());
    }

    @Test(expected = NullPointerException.class)
    public final void nullInputShouldThrowNullPointerException() throws Exception {
        fileSystem.getFile(null);
    }

    public final void emptyInputShouldThrowReturnEmptyPathFile() throws Exception {
        File file = fileSystem.getFile("");
        assertThat(file.getPath()).isEmpty();
    }

    @Test
    public final void protocolOnlyShouldReturnEmptyPathFile() throws Exception {
        File file = fileSystem.getFile("file:");
        assertThat(file.getPath()).isEmpty();
    }

    @Test
    public final void protocolWithDoubleSlashesOnlyShouldReturnDir() throws Exception {
        File file = fileSystem.getFile("file://");
        assertThat(file).isDirectory();
    }

    public static class UrlsAsFileThrowingFileNotFoundExceptionProvider {
        public static Object[] provides() {
            return toArray(
                    toArray("bad://file"),
                    toArray("classpath:" + FAKE_FILE),
                    toArray("classpath:/" + FAKE_FILE),
                    toArray("http://localhost:$PORT$/"),
                    toArray("classpath:" + new File(ClassLoader.getSystemResource(EXISTING_CLASSPATH_FILE).getFile()).getAbsolutePath()),
                    toArray("classpath:java/lang/String.class")
            );
        }
    }

    @Test(expected = FileNotFoundException.class)
    @Parameters(source = UrlsAsFileThrowingFileNotFoundExceptionProvider.class)
    public final void urlAsFileThrowingFileNotFoundException(String url) throws Exception {
        url = replacePort(url);
        
        fileSystem.getFile(url);
    }

    public static class NonExistingFilesProvider {
        public static Object[] provides() {
            return toArray(
                    toArray("file:///" + FAKE_FILE),
                    toArray("file:///" + FAKE_DIRECTORY + FAKE_FILE),
                    toArray("file://conf/" + FAKE_FILE),
                    toArray("file://var/" + FAKE_FILE)
            );
        }
    }

    @Test
    @Parameters(source = NonExistingFilesProvider.class)
    public void nonExistingFilesShouldNotExist(String url) throws Exception {
        File f = fileSystem.getFile(url);
        assertThat(f).doesNotExist();
    }

    public static class NonAvailableStreamsProvider {
        public static Object[] provide() {
            return toArray(
                    toArray("http://localhost:$PORT$/" + FAKE_FILE),
                    toArray("classpath:java/lang/" + FAKE_FILE + ".clas"),
                    toArray("classpath:" + FAKE_FILE)
            );
        }
    }

    @Test(expected = FileNotFoundException.class)
    @Parameters(source = NonAvailableStreamsProvider.class)
    public final void getFakeHttpResourceAsInputStreamShouldThrow(String url) throws Exception {
        url = replacePort(url);
        
        fileSystem.getResource(url);
    }

    public static class AvailableStreamsProvider {
        public static Object[] provide() {
            return toArray(
                    toArray("http://localhost:$PORT$/"),
                    toArray("classpath:java/lang/String.class"),
                    toArray("classpath:" + EXISTING_CLASSPATH_FILE),
                    toArray("classpath:" + EXISTING_CLASSPATH_FILE_WITH_SPACES),
                    toArray("classpath:/" + EXISTING_CLASSPATH_FILE),
                    toArray("classpath:/" + EXISTING_CLASSPATH_FILE_WITH_SPACES)
            );
        }
    }

    @Test
    @Parameters(source = AvailableStreamsProvider.class)
    public final void availableInputStreamShouldReturnANonEmptyStream(String url) throws Exception {
        url = replacePort(url);
        try (InputStream inputStream = fileSystem.getResource(url)) {
            assertThat(inputStream.readAllBytes().length).isGreaterThan(0);
        }
    }

    private String replacePort(String url) {
        return url.replace("$PORT$", String.valueOf(httpServer.port()));
    }

    public static class FileToCreateProvider {
        public static Object[] provide() {
            return toArray(
                    toArray("fileSystemTest", ".txt"),
                    toArray("file System Test", ".txt")
            );
        }
    }

    @Test
    @Parameters(source = FileToCreateProvider.class)
    public final void createdFilesShouldExist(String name, String extension) throws Exception {
        File temp = createTempFile(name, extension);
        try {
            File expected = fileSystem.getFile("file:" + temp.getAbsolutePath());
            assertThat(expected).exists();
        } finally {
            temp.delete();
        }
    }

    @Test
    @Parameters(source = FileToCreateProvider.class)
    public final void createdFilesShouldExistWhenAccessedWithTwoSlashes(String name, String extension) throws Exception {
        File temp = createTempFile(name, extension);
        try {
            File expected = fileSystem.getFile("file://" + temp.getAbsolutePath());
            assertThat(expected).exists();
        } finally {
            temp.delete();
        }
    }

    @Test
    @Parameters(source = FileToCreateProvider.class)
    public final void createdFilesAsInputStreamShouldBeAvailable(String name, String extension) throws Exception {
        File temp = createTempFile(name, extension);
        try (InputStream inputStream = fileSystem.getResource("file:" + temp.getAbsolutePath())) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("content");
        } finally {
            temp.delete();
        }
    }

    @Test
    @Parameters(source = FileToCreateProvider.class)
    public final void createdFilesAsInputStreamShouldBeAvailableWhenAccessedWithTwoSlashes(String name, String extension) throws Exception {
        File temp = createTempFile(name, extension);
        try (InputStream inputStream = fileSystem.getResource("file://" + temp.getAbsolutePath())) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("content");
        } finally {
            temp.delete();
        }
    }

    private File createTempFile(String name, String extension) throws IOException {
        File temp = File.createTempFile(name, extension);
        try (var out = new FileOutputStream(temp)) {
            out.write("content".getBytes(StandardCharsets.UTF_8));
        }
        return temp;
    }

    @Test
    public void testConfProtocolSouldReturnConfFile() throws Exception {
        File file = fileSystem.getFile("file://conf/conf.txt");

        assertThat(file).hasContent("confcontent");
    }
    
    @Test
    public void testVarProtocolSouldReturnVarFile() throws Exception {
        File file = fileSystem.getFile("file://var/var.txt");

        assertThat(file).hasContent("varcontent");
    }
    
    private static Object[] toArray(Object... params) {
        return params;
    }
}
