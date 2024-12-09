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
 
package org.apache.james.transport.matchers;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.mail.MessagingException;
import jakarta.mail.Part;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.field.ContentDispositionFieldLenientImpl;
import org.apache.james.mime4j.field.ContentTypeFieldLenientImpl;
import org.apache.james.mime4j.stream.RawFieldParser;
import org.apache.james.mime4j.util.ContentUtil;
import org.apache.james.mime4j.util.MimeParameterMapping;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.james.transport.matchers.utils.MimeWalk;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

/**
 * <P>Checks if at least one attachment has a file name which matches any
 * element of a comma-separated or space-separated list of file name masks.</P>
 * <P>Syntax: <CODE>match="AttachmentFileNameIs=[-d] [-z] <I>masks</I>"</CODE></P>
 * <P>The match is case insensitive.</P>
 * <P>File name masks may start with a wildcard '*'.</P>
 * <P>Multiple file name masks can be specified, e.g.: '*.scr,*.bat'.</P>
 * <P>If '<CODE>-d</CODE>' is coded, some debug info will be logged.</P>
 * <P>If '<CODE>-z</CODE>' is coded, the check will be non-recursively applied
 * to the contents of any attached '*.zip' file.</P>
 *
 * @version CVS $Revision$ $Date$
 * @since 2.2.0
 */
public class AttachmentFileNameIs extends GenericMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentFileNameIs.class);

    /**
     * Transforms <I>fileName<I> in a trimmed lowercase string usable for matching agains the masks.
     * Also decode encoded words.
     */
    public static String cleanFileName(String fileName) {
        return DecoderUtil.decodeEncodedWords(fileName.toLowerCase(Locale.US).trim(), DecodeMonitor.SILENT);
    }
    
    /** Match string for zip files. */
    protected static final String ZIP_SUFFIX = ".zip";
    
    /**
     * represents a single parsed file name mask.
     */
    
    /**
     * Controls certain log messages.
     */
    @VisibleForTesting
    MimeWalk.Configuration configuration = MimeWalk.Configuration.DEFAULT;
    

    @Override
    public void init() throws MessagingException {
        configuration = MimeWalk.Configuration.parse(getCondition());
    }

    /** 
     * Either every recipient is matching or neither of them.
     * @throws MessagingException if no matching attachment is found and at least one exception was thrown
     */
    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        return new MimeWalk(configuration, this::partMatch)
            .matchMail(mail);
    }

    private boolean partMatch(Part part) throws MessagingException {
        Optional<ContentDispositionField> contentDispositionField = Optional.ofNullable(part.getHeader("Content-Disposition"))
            .map(headers -> headers[0])
            .map(value -> "Content-Disposition: " + value)
            .map(ContentUtil::encode)
            .map(Throwing.function(RawFieldParser.DEFAULT::parseField))
            .map(raw -> ContentDispositionFieldLenientImpl.PARSER.parse(raw, DecodeMonitor.SILENT));
        Optional<ContentTypeField> contentTypeField = Optional.ofNullable(part.getHeader("Content-Type"))
            .map(headers -> headers[0])
            .map(value -> "Content-Type: " + value)
            .map(ContentUtil::encode)
            .map(Throwing.function(RawFieldParser.DEFAULT::parseField))
            .map(raw -> ContentTypeFieldLenientImpl.PARSER.parse(raw, DecodeMonitor.SILENT));

        return extractFilename(contentTypeField, contentDispositionField)
            .map(AttachmentFileNameIs::cleanFileName)
            .map(Throwing.function(fileName -> {
                if (matchFound(fileName)) {
                    if (configuration.isDebug()) {
                        LOGGER.debug("matched {}", fileName);
                    }
                    return true;
                }
                if (configuration.unzipIsRequested() && fileName.endsWith(ZIP_SUFFIX) && matchFoundInZip(part)) {
                    return true;
                }
                return false;
            }))
            .orElse(false);
    }

    private Optional<String> extractFilename(Optional<ContentTypeField> contentTypeField, Optional<ContentDispositionField> contentDispositionField) {
        Comparator<Map.Entry<String, String>> comparingName = (e1, e2) -> extractParameterName(e1.getKey()).compareTo(extractParameterName(e2.getKey()));
        Comparator<Map.Entry<String, String>> comparingPartNumbers = (e1, e2) -> Integer.compare(extractPartNumber(e1.getKey()),
            extractPartNumber(e2.getKey()));

        return contentTypeField
            .flatMap(field -> {
                MimeParameterMapping mimeParameterMapping = new MimeParameterMapping();
                field.getParameters().entrySet().stream()
                    .sorted(comparingName.thenComparing(comparingPartNumbers))
                    .forEach(e -> mimeParameterMapping.addParameter(e.getKey(), e.getValue()));
                return Optional.ofNullable(mimeParameterMapping.get("name"));
            })
            .or(() -> contentDispositionField.map(ContentDispositionField::getFilename))
            .map(MimeUtil::unscrambleHeaderValue);
    }

    String extractParameterName(String name) {
        int separatorPosition = name.indexOf('*');
        if (separatorPosition > 0) {
            return name.substring(0, separatorPosition);
        }
        return name;
    }

    String removeTrailingSeparator(String name) {
        int separatorPosition = name.indexOf('*');
        if (separatorPosition > 0) {
            return name.substring(0, separatorPosition);
        }
        return name;
    }

    int extractPartNumber(String name) {
        int separatorPosition = name.indexOf('*');
        if (separatorPosition > 0) {
            try {
                return Integer.parseInt(removeTrailingSeparator(name.substring(separatorPosition + 1)));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Checks if <I>fileName</I> matches with at least one of the <CODE>masks</CODE>.
     * 
     * @param fileName
     */
    protected boolean matchFound(String fileName) {
        for (MimeWalk.Mask mask1 : configuration.masks()) {
            if (mask1.match(fileName)) {
                return true; // matching file found
            }
        }
        return false;
    }

    /**
     * Checks if <I>part</I> is a zip containing a file that matches with at least one of the <CODE>masks</CODE>.
     *
     *@param part
     */
    protected boolean matchFoundInZip(Part part) throws MessagingException, IOException {
        try (ZipInputStream zis = new ZipInputStream(part.getInputStream())) {
            while (true) {
                ZipEntry zipEntry = zis.getNextEntry();
                if (zipEntry == null) {
                    break;
                }
                String fileName = zipEntry.getName();
                if (matchFound(fileName)) {
                    if (configuration.unzipIsRequested()) {
                        LOGGER.debug("matched {}({})", part.getFileName(), fileName);
                    }
                    return true;
                }
            }
            return false;
        }
    }
}

