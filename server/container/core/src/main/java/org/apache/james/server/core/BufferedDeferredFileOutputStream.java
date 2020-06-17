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

package org.apache.james.server.core;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.commons.io.output.ThresholdingOutputStream;

/**
 * An almost copy of {@link DeferredFileOutputStream} with buffered file stream.
  * <p>
  * This copy is done because 1. the original class is not written in the way that
  * we can decorate file output stream. 2. when switched to FileOutputStream the
  * original class does not buffer file output stream which could cause up to 50%
  * performance loss compare to buffered stream (see JIRA JAMES-2343 for simple
  * benchmark).
  * <p>
  * The only difference is in the {@link #thresholdReached()} where when updating
  * {@link #currentOutputStream}, instead of directly assign FileOutputStream to
  * it, here we wrap the file output stream with BufferedOutputStream.
  *
  * @link https://issues.apache.org/jira/browse/JAMES-2343
 */
public class BufferedDeferredFileOutputStream extends ThresholdingOutputStream {

    /**
     * The output stream to which data will be written prior to the theshold
     * being reached.
     */
    private ByteArrayOutputStream memoryOutputStream;


    /**
     * The output stream to which data will be written at any given time. This
     * will always be one of <code>memoryOutputStream</code> or
     * <code>diskOutputStream</code>.
     */
    private OutputStream currentOutputStream;


    /**
     * The file to which output will be directed if the threshold is exceeded.
     */
    private File outputFile;

    /**
     * The temporary file prefix.
     */
    private final String prefix;

    /**
     * The temporary file suffix.
     */
    private final String suffix;

    /**
     * The directory to use for temporary files.
     */
    private final File directory;


    /**
     * True when close() has been called successfully.
     */
    private boolean closed = false;

    /**
     * Constructs an instance of this class which will trigger an event at the
     * specified threshold, and save data to a file beyond that point.
     *
     * @param threshold  The number of bytes at which to trigger an event.
     * @param outputFile The file to which data is saved beyond the threshold.
     */
    public BufferedDeferredFileOutputStream(final int threshold, final File outputFile) {
        this(threshold,  outputFile, null, null, null);
    }


    /**
     * Constructs an instance of this class which will trigger an event at the
     * specified threshold, and save data to a temporary file beyond that point.
     *
     * @param threshold  The number of bytes at which to trigger an event.
     * @param prefix Prefix to use for the temporary file.
     * @param suffix Suffix to use for the temporary file.
     * @param directory Temporary file directory.
     */
    public BufferedDeferredFileOutputStream(final int threshold, final String prefix, final String suffix, final File directory) {
        this(threshold, null, prefix, suffix, directory);
        if (prefix == null) {
            throw new IllegalArgumentException("Temporary file prefix is missing");
        }
    }

    /**
     * Constructs an instance of this class which will trigger an event at the
     * specified threshold, and save data either to a file beyond that point.
     *
     * @param threshold  The number of bytes at which to trigger an event.
     * @param outputFile The file to which data is saved beyond the threshold.
     * @param prefix Prefix to use for the temporary file.
     * @param suffix Suffix to use for the temporary file.
     * @param directory Temporary file directory.
     */
    private BufferedDeferredFileOutputStream(final int threshold, final File outputFile, final String prefix,
                                             final String suffix, final File directory) {
        super(threshold);
        this.outputFile = outputFile;

        memoryOutputStream = new ByteArrayOutputStream();
        currentOutputStream = memoryOutputStream;
        this.prefix = prefix;
        this.suffix = suffix;
        this.directory = directory;
    }

    /**
     * Returns the current output stream. This may be memory based or disk
     * based, depending on the current state with respect to the threshold.
     *
     * @return The underlying output stream.
     *
     * @exception IOException if an error occurs.
     */
    @Override
    protected OutputStream getStream() throws IOException {
        return currentOutputStream;
    }

    /**
     * Switches the underlying output stream from a memory based stream to one
     * that is backed by disk. This is the point at which we realise that too
     * much data is being written to keep in memory, so we elect to switch to
     * disk-based storage.
     *
     * @exception IOException if an error occurs.
     */
    @Override
    protected void thresholdReached() throws IOException {
        if (prefix != null) {
            outputFile = File.createTempFile(prefix, suffix, directory);
        }
        final FileOutputStream fos = new FileOutputStream(outputFile);
        try {
            memoryOutputStream.writeTo(fos);
        } catch (IOException e) {
            fos.close();
            throw e;
        }
        currentOutputStream = new BufferedOutputStream(fos, getThreshold());
        memoryOutputStream = null;
    }

    /**
     * Determines whether or not the data for this output stream has been
     * retained in memory.
     *
     * @return {@code true} if the data is available in memory;
     *         {@code false} otherwise.
     */
    public boolean isInMemory() {
        return !isThresholdExceeded();
    }


    /**
     * Returns the data for this output stream as an array of bytes, assuming
     * that the data has been retained in memory. If the data was written to
     * disk, this method returns {@code null}.
     *
     * @return The data for this output stream, or {@code null} if no such
     *         data is available.
     */
    public byte[] getData() {
        if (memoryOutputStream != null) {
            return memoryOutputStream.toByteArray();
        }
        return null;
    }


    /**
     * Returns either the output file specified in the constructor or
     * the temporary file created or null.
     * <p>
     * If the constructor specifying the file is used then it returns that
     * same output file, even when threshold has not been reached.
     * <p>
     * If constructor specifying a temporary file prefix/suffix is used
     * then the temporary file created once the threshold is reached is returned
     * If the threshold was not reached then {@code null} is returned.
     *
     * @return The file for this output stream, or {@code null} if no such
     *         file exists.
     */
    public File getFile() {
        return outputFile;
    }


    /**
     * Closes underlying output stream, and mark this as closed
     *
     * @exception IOException if an error occurs.
     */
    @Override
    public void close() throws IOException {
        super.close();
        closed = true;
    }


    /**
     * Writes the data from this output stream to the specified output stream,
     * after it has been closed.
     *
     * @param out output stream to write to.
     * @exception IOException if this stream is not yet closed or an error occurs.
     */
    public void writeTo(final OutputStream out) throws IOException {
        // we may only need to check if this is closed if we are working with a file
        // but we should force the habit of closing wether we are working with
        // a file or memory.
        if (!closed) {
            throw new IOException("Stream not closed");
        }

        if (isInMemory()) {
            memoryOutputStream.writeTo(out);
        } else {
            final FileInputStream fis = new FileInputStream(outputFile);
            try {
                IOUtils.copy(fis, out);
            } finally {
                IOUtils.closeQuietly(fis);
            }
        }
    }
}