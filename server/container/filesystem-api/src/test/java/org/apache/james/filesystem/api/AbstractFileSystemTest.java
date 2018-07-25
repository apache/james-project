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

import static junitparams.JUnitParamsRunner.$;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import com.google.common.base.Strings;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public abstract class AbstractFileSystemTest {
    private static final int RANDOM_PORT = 0;
    private static final String FAKE_DIRECTORY = "b7b73e3a-5234-11e5-87f2-9b171f273b49/";
    private static final String FAKE_FILE = "d9091ae6-521f-11e5-b666-bb11fef67c2a";
    private static final String EXISTING_CLASSPATH_FILE = "classpathTest.txt";
    private static final String EXISTING_CLASSPATH_FILE_WITH_SPACES = "class path Test.txt";

    @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

    protected FileSystem fileSystem;
    
    private Server httpServer;
    private File rootDirectory;
    
    protected abstract FileSystem buildFileSystem(String configurationRootDirectory);

    @Before
    public void setUp() throws Exception {
        httpServer = new Server(RANDOM_PORT);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        httpServer.setHandler(context);
        context.addServlet(new ServletHolder(new ContentServlet()), "/*");

        httpServer.start();

        rootDirectory = tmpFolder.getRoot();
        createSubFolderWithAFileIn("conf", "conf.txt", "confcontent");
        createSubFolderWithAFileIn("var", "var.txt", "varcontent");

        fileSystem = buildFileSystem(rootDirectory.getAbsolutePath());
    }

    private static class ContentServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            if (isRootPath(request.getPathInfo())) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println("content");
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }

        private boolean isRootPath(String pathInfo) {
            return Strings.isNullOrEmpty(pathInfo)
                || pathInfo == "/";
        }
    }

    private void createSubFolderWithAFileIn(String folderName, String fileName, String fileContent) throws IOException {
        File folder = tmpFolder.newFolder(folderName);
        File file = new File(folder.getAbsolutePath() + "/" + fileName);
        FileUtils.writeStringToFile(file, fileContent, StandardCharsets.UTF_8);
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
            return $(
                    $("bad://file"),
                    $("classpath:" + FAKE_FILE),
                    $("classpath:/" + FAKE_FILE),
                    $("http://localhost:$PORT$/"),
                    $("classpath:" + new File(ClassLoader.getSystemResource(EXISTING_CLASSPATH_FILE).getFile()).getAbsolutePath()),
                    $("classpath:java/lang/String.class")
            );
        }
    }

    @Test(expected = FileNotFoundException.class)
    @Parameters(source = UrlsAsFileThrowingFileNotFoundExceptionProvider.class)
    public final void urlAsFileThrowingFileNotFoundException(String url) throws Exception {
        fileSystem.getFile(replacePort(url));
    }

    public static class NonExistingFilesProvider {
        public static Object[] provides() {
            return $(
                    $("file:///" + FAKE_FILE),
                    $("file:///" + FAKE_DIRECTORY + FAKE_FILE),
                    $("file://conf/" + FAKE_FILE),
                    $("file://var/" + FAKE_FILE)
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
            return $(
                    $("http://localhost:$PORT$/" + FAKE_FILE),
                    $("classpath:java/lang/" + FAKE_FILE + ".clas"),
                    $("classpath:" + FAKE_FILE)
            );
        }
    }

    @Test(expected = FileNotFoundException.class)
    @Parameters(source = NonAvailableStreamsProvider.class)
    public final void getFakeHttpResourceAsInputStreamShouldThrow(String url) throws Exception {
        fileSystem.getResource(replacePort(url));
    }

    public static class AvailableStreamsProvider {
        public static Object[] provide() {
            return $(
                    $("http://localhost:$PORT$/"),
                    $("classpath:java/lang/String.class"),
                    $("classpath:" + EXISTING_CLASSPATH_FILE),
                    $("classpath:" + EXISTING_CLASSPATH_FILE_WITH_SPACES),
                    $("classpath:/" + EXISTING_CLASSPATH_FILE),
                    $("classpath:/" + EXISTING_CLASSPATH_FILE_WITH_SPACES)
            );
        }
    }

    @Test
    @Parameters(source = AvailableStreamsProvider.class)
    public final void availableInputStreamShouldReturnANonEmptyStream(String url) throws Exception {
        try (InputStream inputStream = fileSystem.getResource(replacePort(url))) {
            assertThat(IOUtils.toByteArray(inputStream).length).isGreaterThan(0);
        }
    }

    private String replacePort(String url) {
        int port = ((ServerConnector) httpServer.getConnectors()[0]).getLocalPort();
        return url.replace("$PORT$", String.valueOf(port));
    }

    public static class FileToCreateProvider {
        public static Object[] provide() {
            return $(
                    $("fileSystemTest", ".txt"),
                    $("file System Test", ".txt")
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
            assertThat(IOUtils.toString(inputStream, StandardCharsets.UTF_8)).isEqualTo("content");
        } finally {
            temp.delete();
        }
    }

    @Test
    @Parameters(source = FileToCreateProvider.class)
    public final void createdFilesAsInputStreamShouldBeAvailableWhenAccessedWithTwoSlashes(String name, String extension) throws Exception {
        File temp = createTempFile(name, extension);
        try (InputStream inputStream = fileSystem.getResource("file://" + temp.getAbsolutePath())) {
            assertThat(IOUtils.toString(inputStream, StandardCharsets.UTF_8)).isEqualTo("content");
        } finally {
            temp.delete();
        }
    }

    private File createTempFile(String name, String extension) throws IOException {
        File temp = File.createTempFile(name, extension);
        FileUtils.write(temp, "content", StandardCharsets.UTF_8);
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
    
}
