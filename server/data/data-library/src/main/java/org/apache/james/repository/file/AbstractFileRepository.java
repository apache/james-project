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

package org.apache.james.repository.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.Configurable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This an abstract class implementing functionality for creating a file-store.
 */
public abstract class AbstractFileRepository implements Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFileRepository.class);

    protected static final boolean DEBUG = false;

    protected static final int BYTE_MASK = 0x0f;

    protected static final char[] HEX_DIGITS = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    protected String extension;

    protected String name;

    protected FilenameFilter filter;

    protected File baseDirectory;

    private FileSystem fileSystem;

    private String destination;

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        destination = configuration.getString("[@destinationURL]");
    }

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    protected abstract String getExtensionDecorator();

    @PostConstruct
    public void init() throws Exception {
        LOGGER.info("Init {} Store", getClass().getName());
        setDestination(destination);

        File directory;

        try {
            directory = baseDirectory.getCanonicalFile();
        } catch (IOException ioe) {
            throw new ConfigurationException("Unable to form canonical representation of " + baseDirectory);
        }

        name = "Repository";
        String postfix = getExtensionDecorator();
        extension = "." + name + postfix;
        filter = new ExtensionFileFilter(extension);
        // m_filter = new NumberedRepositoryFileFilter(getExtensionDecorator());

        FileUtils.forceMkdir(directory);

        LOGGER.info("{} opened in {}", getClass().getName(), baseDirectory);

        // We will look for all numbered repository files in this
        // directory and rename them to non-numbered repositories,
        // logging all the way.

        FilenameFilter numFilter = new NumberedRepositoryFileFilter(getExtensionDecorator());
        final String[] names = directory.list(numFilter);

        for (String origFilename : names) {
            // This needs to handle (skip over) the possible repository
            // numbers
            int pos = origFilename.length() - postfix.length();
            while (pos >= 1 && Character.isDigit(origFilename.charAt(pos - 1))) {
                pos--;
            }
            pos -= ".".length() + name.length();
            String newFilename = origFilename.substring(0, pos) + extension;

            File origFile = new File(directory, origFilename);
            File newFile = new File(directory, newFilename);

            if (origFile.renameTo(newFile)) {
                LOGGER.info("Renamed {} to {}", origFile, newFile);
            } else {
                LOGGER.info("Unable to rename {} to {}", origFile, newFile);
            }
        }

    }

    /**
     * Set the destination for the repository
     * 
     * @param destination
     *            the destination under which the repository get stored
     * @throws ConfigurationException
     * @throws ConfigurationException
     *             get thrown on invalid destintion syntax
     */
    protected void setDestination(String destination) throws ConfigurationException {

        if (!destination.startsWith(FileSystem.FILE_PROTOCOL)) {
            throw new ConfigurationException("cannot handle destination " + destination);
        }

        try {
            baseDirectory = fileSystem.getFileWithinBaseDir(destination);
        } catch (IOException e) {
            throw new ConfigurationException("Unable to acces destination " + destination, e);
        }

    }

    /**
     * Return the File Object which belongs to the given key
     * 
     * @param key
     *            the key for which the File get returned
     * @return file the File associted with the given Key
     * @throws IOException
     *             get thrown on IO error
     */
    protected File getFile(String key) throws IOException {
        return new File(encode(key));
    }

    /**
     * Return the InputStream which belongs to the given key
     * 
     * @param key
     *            the key for which the InputStream get returned
     * @return in the InputStram associted with the given key
     * @throws IOException
     *             get thrown on IO error
     */
    protected InputStream getInputStream(String key) throws IOException {
        // This was changed to SharedFileInputStream but reverted to
        // fix JAMES-559. Usign SharedFileInputStream should be a good
        // performance improvement, but more checks have to be done
        // on the repository side to avoid concurrency in reading and
        // writing the same file.
        return new FileInputStream(encode(key));
    }

    /**
     * Return the OutputStream which belongs to the given key
     * 
     * @param key
     *            the key for which the OutputStream get returned
     * @return out the OutputStream
     * @throws IOException
     *             get thrown on IO error
     */
    protected OutputStream getOutputStream(String key) throws IOException {
        return new FileOutputStream(getFile(key));
    }

    /**
     * Remove the object associated to the given key.
     * 
     * @param key
     *            the key to remove
     * @throws IOException 
     */
    
    public synchronized boolean remove(String key) {
        try {
            FileUtils.forceDelete(getFile(key));
            return true;
        } catch (FileNotFoundException e) { 
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Exception caught while removing" + " an object", e);
        }
    }
    
    /**
     * 
     * Indicates if the given key is associated to a contained object
     * 
     * @param key
     *            the key which checked for
     * @return true if the repository contains the key
     */
    public synchronized boolean containsKey(String key) {
        try {
            final File file = getFile(key);
            if (DEBUG) {
                LOGGER.debug("checking key {}", key);
            }
            return file.exists();
        } catch (Exception e) {
            throw new RuntimeException("Exception caught while searching " + "an object", e);
        }
    }

    /**
     * Returns the list of used keys.
     */
    public Stream<String> list() {
        final File storeDir = new File(baseDirectory.getAbsolutePath());
        final String[] names = storeDir.list(filter);

        return Arrays.stream(names).map(this::decode);
    }

    /**
     * Returns a String that uniquely identifies the object. <b>Note:</b> since
     * this method uses the Object.toString() method, it's up to the caller to
     * make sure that this method doesn't change between different JVM
     * executions (like it may normally happen). For this reason, it's highly
     * recommended (even if not mandated) that Strings be used as keys.
     * 
     * @param key
     *            the key for which the Object should be searched
     * @return result a unique String represent the Object which belongs to the
     *         key
     */
    protected String encode(String key) {
        final byte[] bytes = key.getBytes();
        final char[] buffer = new char[bytes.length << 1];

        for (int i = 0, j = 0; i < bytes.length; i++) {
            final int k = bytes[i];
            buffer[j++] = HEX_DIGITS[(k >>> 4) & BYTE_MASK];
            buffer[j++] = HEX_DIGITS[k & BYTE_MASK];
        }

        StringBuilder result = new StringBuilder();
        result.append(baseDirectory.getAbsolutePath());
        result.append(File.separator);
        result.append(buffer);
        result.append(extension);
        return result.toString();
    }

    /**
     * Inverse of encode exept it do not use path. So decode(encode(s) - m_path)
     * = s. In other words it returns a String that can be used as key to
     * retrieve the record contained in the 'filename' file.
     * 
     * @param filename
     *            the filename for which the key should generated
     * @return key a String which can be used to retrieve the filename
     */
    protected String decode(String filename) {
        filename = filename.substring(0, filename.length() - extension.length());
        final int size = filename.length();
        final byte[] bytes = new byte[size >>> 1];

        for (int i = 0, j = 0; i < size; j++) {
            bytes[j] = Byte.parseByte(filename.substring(i, i + 2), 16);
            i += 2;
        }

        return new String(bytes);
    }
}
