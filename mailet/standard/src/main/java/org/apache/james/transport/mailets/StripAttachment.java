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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.james.javax.MultipartUtil;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * <p>
 * Remove attachments from a Message. Supports simple removal, storing to file,
 * or storing to mail attributes.
 * </p>
 * <p>
 * Configuration:
 * </p>
 * <p>
 * 
 * <pre>
 *   &lt;mailet match=&quot;All&quot; class=&quot;StripAttachment&quot; &gt;
 *     &lt;pattern &gt;.*\.xls &lt;/pattern&gt;  &lt;!-- The regular expression that must be matched -- &gt;
 *     &lt;!-- notpattern &gt;.*\.xls &lt;/notpattern--&gt;  &lt;!-- The regular expression that must be matched -- &gt;
 *     &lt;mimeType&gt;text/calendar&lt;/mimeType&gt;  &lt;!-- The matching mimeType -- &gt;
 *     &lt;directory &gt;c:\temp\james_attach &lt;/directory&gt;   &lt;!-- The directory to save to -- &gt;
 *     &lt;remove &gt;all &lt;/remove&gt;   &lt;!-- either &quot;no&quot;, &quot;matched&quot;, &quot;all&quot; -- &gt;
 *     &lt;!-- attribute&gt;my.attribute.name&lt;/attribute --&gt;
 *   &lt;/mailet &gt;
 *   
 *   At least one of pattern, notpattern and mimeType is required.
 * </pre>
 * 
 * </p>
 */
public class StripAttachment extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(StripAttachment.class);
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, AttributeValue<byte[]>>> MAP_STRING_BYTES_CLASS = (Class<Map<String, AttributeValue<byte[]>>>) (Object) Map.class;

    @SuppressWarnings("unchecked")
    private static final Class<List<AttributeValue<String>>> LIST_OF_STRINGS = (Class<List<AttributeValue<String>>>)(Object) List.class;
    private static final String MULTIPART_MIME_TYPE = "multipart/*";
    public static final String PATTERN_PARAMETER_NAME = "pattern";
    public static final String NOTPATTERN_PARAMETER_NAME = "notpattern";
    public static final String MIMETYPE_PARAMETER_NAME = "mimeType";
    public static final String ATTRIBUTE_PARAMETER_NAME = "attribute";
    public static final String DIRECTORY_PARAMETER_NAME = "directory";
    // Either "no", "matched", "all"
    public static final String REMOVE_ATTACHMENT_PARAMETER_NAME = "remove"; 
    // Either "true", "false"
    public static final String DECODE_FILENAME_PARAMETER_NAME = "decodeFilename";
    public static final String REPLACE_FILENAME_PATTERN_PARAMETER_NAME = "replaceFilenamePattern";
    public static final String REMOVE_NONE = "no";
    public static final String REMOVE_ALL = "all";
    public static final String REMOVE_MATCHED = "matched";
    public static final AttributeName REMOVED_ATTACHMENTS = AttributeName.of("org.apache.james.mailet.standard.mailets.StripAttachment.removed");
    public static final AttributeName SAVED_ATTACHMENTS = AttributeName.of("org.apache.james.mailet.standard.mailets.StripAttachment.saved");

    public static final boolean DECODE_FILENAME_DEFAULT_VALUE = false;

    @VisibleForTesting String removeAttachments;
    private String directoryName;
    private Optional<AttributeName> attributeName;
    private Optional<Pattern> regExPattern;
    private Optional<Pattern> notRegExPattern;
    private String mimeType;
    private boolean decodeFilename;

    private List<ReplacingPattern> filenameReplacingPatterns;

    /**
     * Checks if the mandatory parameters are present, creates the directory to
     * save the files in (if not present).
     * 
     * @throws MailetException
     */
    @Override
    public void init() throws MailetException {
        regExPattern = regExFromParameter(PATTERN_PARAMETER_NAME);
        notRegExPattern = regExFromParameter(NOTPATTERN_PARAMETER_NAME);
        mimeType = getInitParameter(MIMETYPE_PARAMETER_NAME);
        if (regExPattern.isEmpty() && notRegExPattern.isEmpty() && Strings.isNullOrEmpty(mimeType)) {
            throw new MailetException("At least one of '" + PATTERN_PARAMETER_NAME + "', '" + NOTPATTERN_PARAMETER_NAME + "' or '" + MIMETYPE_PARAMETER_NAME + 
                    "' parameter should be provided.");
        }

        directoryName = getInitParameter(DIRECTORY_PARAMETER_NAME);
        attributeName = getInitParameterAsOptional(ATTRIBUTE_PARAMETER_NAME).map(AttributeName::of);

        removeAttachments = getInitParameter(REMOVE_ATTACHMENT_PARAMETER_NAME, REMOVE_NONE).toLowerCase(Locale.US);
        if (!removeAttachments.equals(REMOVE_MATCHED) && !removeAttachments.equals(REMOVE_ALL) && !removeAttachments.equals(REMOVE_NONE)) {
            throw new MailetException(String.format("Unknown remove parameter value '%s' waiting for '%s', '%s' or '%s'.", 
                    removeAttachments, REMOVE_MATCHED, REMOVE_ALL, REMOVE_NONE));
        }

        if (directoryName != null) {
            createDirectory();
        }

        decodeFilename = getBooleanParameter(getInitParameter(DECODE_FILENAME_PARAMETER_NAME), DECODE_FILENAME_DEFAULT_VALUE);
        String replaceFilenamePattern = getInitParameter(REPLACE_FILENAME_PATTERN_PARAMETER_NAME);
        if (replaceFilenamePattern != null) {
            filenameReplacingPatterns = new PatternExtractor().getPatternsFromString(replaceFilenamePattern);
        } else {
            filenameReplacingPatterns = ImmutableList.of();
        }

        logConfiguration();
    }

    private Optional<Pattern> regExFromParameter(String patternParameterName) throws MailetException {
        String patternString = getInitParameter(patternParameterName);
        try {
            return Optional.ofNullable(patternString)
                .map(Pattern::compile);
        } catch (Exception e) {
            throw new MailetException("Could not compile regex [" + patternString + "].");
        }
    }

    private void createDirectory() throws MailetException {
        try {
            FileUtils.forceMkdir(new File(directoryName));
        } catch (Exception e) {
            throw new MailetException("Could not create directory [" + directoryName + "].", e);
        }
    }

    private void logConfiguration() {
        if (LOGGER.isDebugEnabled()) {
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("StripAttachment is initialised with regex pattern [");
            regExPattern.ifPresent(pattern -> logMessage.append(pattern.pattern()));
            logMessage.append(" / ");
            notRegExPattern.ifPresent(pattern -> logMessage.append(pattern.pattern()));
            logMessage.append(']');

            if (directoryName != null) {
                logMessage.append(" and will save to directory [");
                logMessage.append(directoryName);
                logMessage.append(']');
            }
            attributeName.ifPresent(attributeName -> {
                logMessage.append(" and will store attachments to attribute [");
                logMessage.append(attributeName.asString());
                logMessage.append(']');
            });
            LOGGER.debug(logMessage.toString());
        }
    }
    
    /**
     * Service the mail: scan it for attachments matching the pattern.
     * If a filename matches the pattern: 
     * - the file is stored (using its name) in the given directory (if directoryName is given in the mailet configuration)
     *    and the filename is stored in 'saved' mail list attribute
     * - the part is removed (when removeAttachments mailet configuration property is 'all' or 'matched') 
     *    and the filename is stored in 'removed' mail list attribute
     * - the part filename and its content is stored in mail map attribute (if attributeName is given in the mailet configuration)
     * 
     * @param mail
     *            The mail to service
     * @throws MailetException
     *             Thrown when an error situation is encountered.
     */
    @Override
    public void service(Mail mail) throws MailetException {
        MimeMessage message = getMessageFromMail(mail);
        if (isMultipart(message)) {
            processMultipartPartMessage(message, mail);
        }
    }

    private boolean isMultipart(Part part) throws MailetException {
        try {
            return part.isMimeType(MULTIPART_MIME_TYPE);
        } catch (MessagingException e) {
            throw new MailetException("Could not retrieve contenttype of MimePart.", e);
        }
    }

    private MimeMessage getMessageFromMail(Mail mail) throws MailetException {
        try {
            return mail.getMessage();
        } catch (MessagingException e) {
            throw new MailetException("Could not retrieve message from Mail object", e);
        }
    }

    @Override
    public String getMailetInfo() {
        return "StripAttachment";
    }

    /**
     * Checks every part in this part (if it is a Multipart) for having a
     * filename that matches the pattern. 
     * If the name matches: 
     * - the file is stored (using its name) in the given directory (if directoryName is given in the mailet configuration)
     *    and the filename is stored in 'saved' mail list attribute
     * - the part is removed (when removeAttachments mailet configuration property is 'all' or 'matched') 
     *    and the filename is stored in 'removed' mail list attribute
     * - the part filename and its content is stored in mail map attribute (if attributeName is given in the mailet configuration)
     * 
     * Note: this method is recursive.
     * 
     * @param part
     *            The part to analyse.
     * @param mail
     * @return True if one of the subpart was removed
     * @throws Exception
     */
    @VisibleForTesting boolean processMultipartPartMessage(Part part, Mail mail) throws MailetException {
        if (!isMultipart(part)) {
            return false;
        }

        try {
            Multipart multipart = (Multipart) part.getContent();
            boolean atLeastOneRemoved = false;
            boolean subpartHasBeenChanged = false;
            List<BodyPart> bodyParts = MultipartUtil.retrieveBodyParts(multipart);
            for (BodyPart bodyPart: bodyParts) {
                if (isMultipart(bodyPart)) {
                    if (processMultipartPartMessage(bodyPart, mail)) {
                        subpartHasBeenChanged = true;
                    }
                } else {
                    if (shouldBeRemoved(bodyPart, mail)) {
                        multipart.removeBodyPart(bodyPart);
                        atLeastOneRemoved = true;
                    }
                }
            }
            if (atLeastOneRemoved || subpartHasBeenChanged) {
                updateBodyPart(part, multipart);
            }
            return atLeastOneRemoved || subpartHasBeenChanged;
        } catch (Exception e) {
            LOGGER.error("Failing while analysing part for attachments (StripAttachment mailet).", e);
            return false;
        }
    }

    private void updateBodyPart(Part part, Multipart newPartContent) throws MessagingException {
        part.setContent(newPartContent);
        if (part instanceof Message) {
            ((Message) part).saveChanges();
        }
    }

    private boolean shouldBeRemoved(BodyPart bodyPart, Mail mail) throws MessagingException, Exception {
        String fileName = getFilename(bodyPart);

        boolean shouldRemove = removeAttachments.equals(REMOVE_ALL);
        String decodedName = DecoderUtil.decodeEncodedWords(fileName, DecodeMonitor.SILENT);
        if (isMatching(bodyPart, decodedName)) {
            storeBodyPartAsFile(bodyPart, mail, decodedName);
            storeBodyPartAsMailAttribute(bodyPart, mail, decodedName);
            if (removeAttachments.equals(REMOVE_MATCHED)) {
                shouldRemove = true;
            }
        }
        storeFileNameAsAttribute(mail, AttributeValue.of(fileName), shouldRemove);
        return shouldRemove;
    }

    private boolean isMatching(BodyPart bodyPart, String fileName) throws MessagingException {
        return fileNameMatches(fileName) || bodyPart.isMimeType(mimeType);
    }

    private void storeBodyPartAsFile(BodyPart bodyPart, Mail mail, String fileName) throws Exception {
        if (directoryName != null) {
            saveAttachmentToFile(bodyPart, Optional.of(fileName)).ifPresent(filename ->
                    addFilenameToAttribute(mail, AttributeValue.of(filename), SAVED_ATTACHMENTS)
            );
        }
    }

    private void addFilenameToAttribute(Mail mail, AttributeValue<String> attributeValue, AttributeName attributeName) {
        Function<List<AttributeValue<String>>, List<AttributeValue<?>>> typeWeakner = values ->
                values.stream().map(value -> (AttributeValue<?>) value).collect(Collectors.toList());

        ImmutableList.Builder<AttributeValue<?>> attributeValues = ImmutableList.<AttributeValue<?>>builder()
                .addAll(AttributeUtils
                    .getValueAndCastFromMail(mail, attributeName, LIST_OF_STRINGS)
                    .map(typeWeakner)
                    .orElse(new ArrayList<>()));
        attributeValues.add(attributeValue);
        mail.setAttribute(new Attribute(attributeName, AttributeValue.of(attributeValues.build())));
    }

    private void storeBodyPartAsMailAttribute(BodyPart bodyPart, Mail mail, String fileName) throws IOException, MessagingException {
        attributeName.ifPresent(Throwing.<AttributeName>consumer(attributeName ->
            addPartContent(bodyPart, mail, fileName, attributeName)).sneakyThrow());
    }

    private void addPartContent(BodyPart bodyPart, Mail mail, String fileName, AttributeName attributeName) throws IOException, MessagingException {
        ImmutableMap.Builder<String, AttributeValue<?>> fileNamesToPartContent = AttributeUtils
            .getValueAndCastFromMail(mail, attributeName, MAP_STRING_BYTES_CLASS)
            .map(ImmutableMap.<String, AttributeValue<?>>builder()::putAll)
            .orElse(ImmutableMap.builder());

        UnsynchronizedByteArrayOutputStream byteArrayOutputStream = new UnsynchronizedByteArrayOutputStream();
        bodyPart.writeTo(byteArrayOutputStream);
        fileNamesToPartContent.put(fileName, AttributeValue.of(byteArrayOutputStream.toByteArray()));

        Map<String, AttributeValue<?>> build = fileNamesToPartContent.build();
        mail.setAttribute(new Attribute(attributeName, AttributeValue.of(build)));
    }

    private void storeFileNameAsAttribute(Mail mail, AttributeValue<String> fileName, boolean hasToBeStored) {
        if (hasToBeStored) {
            addFilenameToAttribute(mail, fileName, REMOVED_ATTACHMENTS);
        }
    }

    @VisibleForTesting String getFilename(BodyPart bodyPart) {
        try {
            String fileName = bodyPart.getFileName();
            if (fileName != null) {
                return renameWithConfigurationPattern(decodeFilename(fileName));
            }
        } catch (Exception e) {
            LOGGER.warn("Unparsable filename, using a random filename instead.", e);
        }
        return randomFilename();
    }

    private String randomFilename() {
        return UUID.randomUUID().toString();
    }

    private String renameWithConfigurationPattern(String fileName) {
        if (filenameReplacingPatterns != null) {
            boolean debug = false;
            return new ContentReplacer(debug).applyPatterns(filenameReplacingPatterns, fileName);
        }
        return fileName;
    }

    private String decodeFilename(String fileName) throws UnsupportedEncodingException {
        if (decodeFilename) {
            return MimeUtility.decodeText(fileName);
        }
        return fileName;
    }

    /**
     * Checks if the given name matches the pattern.
     * 
     * @param name
     *            The name to check for a match.
     * @return True if a match is found, false otherwise.
     */
    @VisibleForTesting boolean fileNameMatches(String name) {
        if (patternsAreEquals()) {
            return false;
        }
        boolean result = isMatchingPattern(name, regExPattern).orElse(false)
                || !isMatchingPattern(name, notRegExPattern).orElse(true);

        LOGGER.debug("attachment {} {}", name, result ? "matches" : "does not match");
        return result;
    }

    private boolean patternsAreEquals() {
        return regExPattern.map(Pattern::pattern).equals(notRegExPattern.map(Pattern::pattern));
    }

    private Optional<Boolean> isMatchingPattern(String name, Optional<Pattern> pattern) {
        return pattern.map(p -> p.matcher(name).matches());
    }

    /**
     * Saves the content of the part to a file in the given directory, using the
     * name of the part. Created files have unique names.
     * 
     * @param part
     *            The MIME part to save.
     * @return
     * @throws Exception
     */
    @VisibleForTesting Optional<String> saveAttachmentToFile(Part part, Optional<String> fileName) throws Exception {
        try {
            File outputFile = outputFile(part, fileName);

            LOGGER.debug("saving content of {}...", outputFile.getName());
            try (InputStream inputStream = part.getInputStream();
                 OutputStream outputStream = new FileOutputStream(outputFile)) {
                IOUtils.copy(inputStream, outputStream);
            }

            return Optional.of(outputFile.getName());
        } catch (Exception e) {
            LOGGER.error("Error while saving contents of", e);
            return Optional.empty();
        }
    }

    private File outputFile(Part part, Optional<String> fileName) throws MessagingException, IOException {
        Optional<String> maybePartFileName = Optional.ofNullable(part.getFileName());
        return createTempFile(fileName.orElse(maybePartFileName.orElse(null)));
    }

    private File createTempFile(String originalFileName) throws IOException {
        OutputFileName outputFileName = OutputFileName.from(originalFileName);
        return Files.createTempFile(new File(directoryName).toPath(), outputFileName.getPrefix(), outputFileName.getSuffix()).toFile();
    }

    @VisibleForTesting static class OutputFileName {

        private static final char PAD_CHAR = '_';
        private static final int MIN_LENGTH = 3;
        private static final String DEFAULT_SUFFIX = ".bin";

        public static OutputFileName from(String originalFileName) {
            if (!originalFileName.contains(".")) {
                return new OutputFileName(prependedPrefix(originalFileName), DEFAULT_SUFFIX);
            }

            int lastDotPosition = originalFileName.lastIndexOf(".");
            return new OutputFileName(prependedPrefix(originalFileName.substring(0, lastDotPosition)), 
                    originalFileName.substring(lastDotPosition));
        }

        @VisibleForTesting static String prependedPrefix(String prefix) {
            return Strings.padStart(prefix, MIN_LENGTH, PAD_CHAR);
        }

        private final String prefix;
        private final String suffix;

        private OutputFileName(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getSuffix() {
            return suffix;
        }
    }
}

