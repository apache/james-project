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

package org.apache.james.transport.mailets;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.StringUtils;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class PatternExtractor {

    private static final int PATTERN = 0;
    private static final int SUBSTITUTION = 1;
    private static final int OPTIONS = 2;

    public List<ReplacingPattern> getPatternsFromString(String pattern) throws MailetException {
        String trimPattern = pattern.trim();
        assertPatternSurroundedWithSlashes(trimPattern);
        
        ImmutableList.Builder<ReplacingPattern> patternList = ImmutableList.builder();
        for (String aPatternArray : extractPatternParts(trimPattern)) {
            patternList.add(stripSurroundingSlashes(aPatternArray));
        }
        return patternList.build();
    }

    private void assertPatternSurroundedWithSlashes(String pattern) throws MailetException {
        if (pattern.length() < 2 || (!pattern.startsWith("/") && !pattern.endsWith("/"))) {
            throw new MailetException("Invalid expression: " + pattern);
        }
    }

    private String[] extractPatternParts(String trimPattern) {
        String trimSurroundingSlashes = trimPattern.substring(1, trimPattern.length() - 1);
        return StringUtils.split(trimSurroundingSlashes, "/,/");
    }

    private ReplacingPattern stripSurroundingSlashes(String line) throws MailetException {
        String[] parts = StringUtils.split(line, "/");
        if (parts.length < 3) {
            throw new MailetException("Invalid expression: " + line);
        }
        return new ReplacingPattern(Pattern.compile(parts[PATTERN], extractOptions(parts[OPTIONS])), 
                extractRepeat(parts[OPTIONS]), 
                unescapeSubstitutions(parts[SUBSTITUTION]));
    }

    private int extractOptions(String optionsAsString) {
        int options = 0;
        if (optionsAsString.contains("i")) {
            options |= Pattern.CASE_INSENSITIVE;
        }
        if (optionsAsString.contains("m")) {
            options |= Pattern.MULTILINE;
        }
        if (optionsAsString.contains("s")) {
            options |= Pattern.DOTALL;
        }
        return options;
    }

    private boolean extractRepeat(String flagsAsString) {
        return flagsAsString.contains("r");
    }

    private String unescapeSubstitutions(String substitutions) {
        String unescaped = substitutions;
        if (unescaped.contains("\\r")) {
            unescaped = unescaped.replaceAll("\\\\r", "\r");
        }
        if (unescaped.contains("\\n")) {
            unescaped = unescaped.replaceAll("\\\\n", "\n");
        }
        if (unescaped.contains("\\t")) {
            unescaped = unescaped.replaceAll("\\\\t", "\t");
        }
        return unescaped;
    }

    public List<ReplacingPattern> getPatternsFromFileList(String filepar) throws MailetException, IOException {
        ImmutableList.Builder<ReplacingPattern> patternList = ImmutableList.builder();
        for (String file : Splitter.on(",").split(filepar)) {
            FileNameWithCharset fileNameWithCharset = FileNameWithCharset.from(file);
            Optional<? extends InputStream> inputStream = retrieveInputStream(fileNameWithCharset.getFileName());
            if (inputStream.isPresent()) {
                patternList.addAll(getPatternsFromStream(inputStream.get(), fileNameWithCharset.getCharset()));
            }
        }
        return patternList.build();
    }

    private static class FileNameWithCharset {

        public static FileNameWithCharset from(String fileName) {
            Optional<Integer> charsetOffset = charsetIndex(fileName);
            if (charsetOffset.isPresent()) {
                return new FileNameWithCharset(fileName.substring(0, charsetOffset.get()), 
                        charset(fileName, charsetOffset.get()));
            }
            return new FileNameWithCharset(fileName, null);
        }

        private static Optional<Integer> charsetIndex(String fileName) {
            int charsetOffset = fileName.lastIndexOf('?');
            if (charsetOffset >= 0) {
                return Optional.of(charsetOffset);
            }
            return Optional.empty();
        }

        private static Charset charset(String fileName, int charsetOffset) {
            return Charset.forName(fileName.substring(charsetOffset + 1));
        }

        private final String fileName;
        private final Charset charset;

        private FileNameWithCharset(String fileName, Charset charset) {
            this.fileName = fileName;
            this.charset = charset;
        }

        public String getFileName() {
            return fileName;
        }

        public Charset getCharset() {
            return charset;
        }
    }

    private List<ReplacingPattern> getPatternsFromStream(InputStream stream, Charset charset) throws MailetException, IOException {
        ImmutableList.Builder<ReplacingPattern> patternList = ImmutableList.builder();
        for (String line: IOUtils.readLines(stream, charset)) {
            line = line.trim();
            if (!isComment(line)) {
                assertPatternSurroundedWithSlashes(line);
                patternList.add(stripSurroundingSlashes(line.substring(1, line.length() - 1)));
            }
        }
        return patternList.build();
    }

    private boolean isComment(String line) {
        return Strings.isNullOrEmpty(line) || line.startsWith("#");
    }

    private Optional<? extends InputStream> retrieveInputStream(String fileAsString) throws FileNotFoundException {
        if (fileAsString.startsWith("#")) {
            return Optional.of(getClass().getResourceAsStream(fileAsString.substring(1)));
        }
        File file = new File(fileAsString);
        if (file.isFile()) {
            return Optional.of(new FileInputStream(file));
        }
        return Optional.empty();
    }
}
