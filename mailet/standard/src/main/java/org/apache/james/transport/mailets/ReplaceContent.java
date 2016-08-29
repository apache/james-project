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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.ParseException;

import org.apache.commons.io.IOUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.StringUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * Replace text contents
 * <p>This mailet allow to specific regular expression to replace text in subject and content.
 * 
 * <p>
 * Each expression is defined as:
 * <code>/REGEX_PATTERN/SUBSTITUTION_PATTERN/FLAGS/</code>
 * </p>
 * 
 * <p>
 * <code>REGEX_PATTERN</code> is a regex used for the match<br>
 * <code>SUBSTITUTION_PATTERN</code> is a substitution pattern<br>
 * <code>FLAGS</code> flags supported for the pattern:<br>
 *   i: case insensitive<br>
 *   m: multi line<br>
 *   x: extended (N/A)<br>
 *   r: repeat - keep matching until a substitution is possible<br>
 * </p>
 * 
 * <p>To identify subject and body pattern we use the tags &lt;subjectPattern&gt; and &lt;bodyPattern&gt;</p>
 *
 * <p>
 * Rules can be specified in external files.
 * Lines must be CRLF terminated and lines starting with # are considered commments.
 * Tags used to include external files are &lt;subjectPatternFile&gt; and 
 * &lt;bodyPatternFile&gt;
 * If file path starts with # then the file is loaded as a reasource.
 * </p>
 * 
 * <p>
 * Use of both files and direct patterns at the same time is allowed.
 * </p>
 * 
 * <p>
 * This mailet allow also to enforce the resulting charset for messages processed.
 * To do that the tag &lt;charset&gt; must be specified.
 * </p>
 * 
 * <p>
 * NOTE:
 * Regexp rules must be escaped by regexp excaping rules and applying this 2 additional rules:<br>
 * - "/" char inside an expression must be prefixed with "\":
 *   e.g: "/\//-//" replaces "/" with "-"<br>
 * - when the rules are specified using &lt;subjectPattern&gt; or &lt;bodyPattern&gt; and
 *   "/,/" has to be used in a pattern string it must be prefixed with a "\".
 *   E.g: "/\/\/,//" replaces "/" with "," (the rule would be "/\//,//" but the "/,/" must
 *   be escaped.<br>
 * </p>
 */
public class ReplaceContent extends GenericMailet {

    private static final String PARAMETER_NAME_SUBJECT_PATTERN = "subjectPattern";
    private static final String PARAMETER_NAME_BODY_PATTERN = "bodyPattern";
    private static final String PARAMETER_NAME_SUBJECT_PATTERNFILE = "subjectPatternFile";
    private static final String PARAMETER_NAME_BODY_PATTERNFILE = "bodyPatternFile";
    private static final String PARAMETER_NAME_CHARSET = "charset";

    public static final int FLAG_REPEAT = 1;

    private Optional<Charset> charset;
    private boolean debug;
    @VisibleForTesting ReplaceConfig replaceConfig;

    @Override
    public String getMailetInfo() {
        return "ReplaceContent";
    }

    @Override
    public void init() throws MailetException {
        charset = initCharset();
        debug = isDebug();
        replaceConfig = initPatterns();
    }

    private Optional<Charset> initCharset() {
        String charsetName = getInitParameter(PARAMETER_NAME_CHARSET);
        if (Strings.isNullOrEmpty(charsetName)) {
            return Optional.absent();
        }
        return Optional.of(Charset.forName(charsetName));
    }

    private boolean isDebug() {
        if (Integer.valueOf(getInitParameter("debug", "0")) == 1) {
            return true;
        }
        return false;
    }

    private ReplaceConfig initPatterns() throws MailetException {
        try {
            ReplaceConfig.Builder builder = ReplaceConfig.builder();
            initSubjectPattern(builder);
            initBodyPattern(builder);
            initSubjectPatternFile(builder);
            initBodyPatternFile(builder);
            return builder.build();
        } catch (FileNotFoundException e) {
            throw new MailetException("Failed initialization", e);
            
        } catch (MailetException e) {
            throw new MailetException("Failed initialization", e);
            
        } catch (IOException e) {
            throw new MailetException("Failed initialization", e);
            
        }
    }

    private void initSubjectPattern(ReplaceConfig.Builder builder) throws MailetException {
        String pattern = getInitParameter(PARAMETER_NAME_SUBJECT_PATTERN);
        if (pattern != null) {
            builder.addAllSubjectReplacingUnits(getPatternsFromString(pattern));
        }
    }

    private void initBodyPattern(ReplaceConfig.Builder builder) throws MailetException {
        String pattern = getInitParameter(PARAMETER_NAME_BODY_PATTERN);
        if (pattern != null) {
            builder.addAllBodyReplacingUnits(getPatternsFromString(pattern));
        }
    }

    private void initSubjectPatternFile(ReplaceConfig.Builder builder) throws MailetException, IOException {
        String filePattern = getInitParameter(PARAMETER_NAME_SUBJECT_PATTERNFILE);
        if (filePattern != null) {
            builder.addAllSubjectReplacingUnits(getPatternsFromFileList(filePattern));
        }
    }

    private void initBodyPatternFile(ReplaceConfig.Builder builder) throws MailetException, IOException {
        String filePattern = getInitParameter(PARAMETER_NAME_BODY_PATTERNFILE);
        if (filePattern != null) {
            builder.addAllBodyReplacingUnits(getPatternsFromFileList(filePattern));
        }
    }

    protected static List<ReplacingPattern> getPatternsFromString(String pattern) throws MailetException {
        String patternProcessing = pattern.trim();
        ensurePatternIsValid(patternProcessing);
        patternProcessing = patternProcessing.substring(1, patternProcessing.length() - 1);
        String[] patternArray = StringUtils.split(patternProcessing, "/,/");
        
        ImmutableList.Builder<ReplacingPattern> patternList= ImmutableList.builder();
        for (String aPatternArray : patternArray) {
            patternList.add(getPattern(aPatternArray));
        }
        return patternList.build();
    }

    protected static List<ReplacingPattern> getPatternsFromStream(InputStream stream, String charset) throws MailetException, IOException {
        ImmutableList.Builder<ReplacingPattern> patternList= ImmutableList.builder();
        for (String line: IOUtils.readLines(stream, charset)) {
            line = line.trim();
            if (!Strings.isNullOrEmpty(line) && !line.startsWith("#")) {
                ensurePatternIsValid(line);
                patternList.add(getPattern(line.substring(1, line.length() - 1)));
            }
        }
        return patternList.build();
    }

    private static void ensurePatternIsValid(String pattern) throws MailetException {
        if (pattern.length() < 2 || (!pattern.startsWith("/") && !pattern.endsWith("/"))) {
            throw new MailetException("Invalid expression: " + pattern);
        }
    }

    private static ReplacingPattern getPattern(String line) throws MailetException {
        String[] pieces = StringUtils.split(line, "/");
        if (pieces.length < 3) {
            throw new MailetException("Invalid expression: " + line);
        }
        return new ReplacingPattern(Pattern.compile(pieces[0], extractOptions(pieces[2])), 
                extractRepeat(pieces[2]), 
                unescapeSubstitutions(pieces[1]));
    }

    private static int extractOptions(String optionsAsString) {
        int options = 0;
        if (optionsAsString.indexOf('i') >= 0) {
            options += Pattern.CASE_INSENSITIVE;
        }
        if (optionsAsString.indexOf('m') >= 0) {
            options += Pattern.MULTILINE;
        }
        if (optionsAsString.indexOf('s') >= 0) {
            options += Pattern.DOTALL;
        }
        return options;
    }

    private static boolean extractRepeat(String flagsAsString) {
        if (flagsAsString.indexOf('r') >= 0) {
            return true;
        }
        return false;
    }

    private static String unescapeSubstitutions(String substitutions) {
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

    /**
     * @param filepar File path list (or resources if the path starts with #) comma separated
     */
    private List<ReplacingPattern> getPatternsFromFileList(String filepar) throws MailetException, IOException {
        ImmutableList.Builder<ReplacingPattern> patternList= ImmutableList.builder();
        String[] files = filepar.split(",");
        for (String file : files) {
            file = file.trim();
            if (debug) {
                log("Loading patterns from: " + file);
            }
            String charset = null;
            int charsetOffset = file.lastIndexOf('?');
            if (charsetOffset >= 0) {
                charset = file.substring(charsetOffset + 1);
                file = file.substring(0, charsetOffset);
            }
            Optional<? extends InputStream> inputStream = retrieveInputStream(file);
            if (inputStream.isPresent()) {
                patternList.addAll(getPatternsFromStream(inputStream.get(), charset));
            }
        }
        return patternList.build();
    }

    private Optional<? extends InputStream> retrieveInputStream(String fileAsString) throws FileNotFoundException {
        if (fileAsString.startsWith("#")) {
            return Optional.of(getClass().getResourceAsStream(fileAsString.substring(1)));
        }
        File file = new File(fileAsString);
        if (file.isFile()) {
            return Optional.of(new FileInputStream(file));
        }
        return Optional.absent();
    }
    
    protected static String applyPatterns(List<ReplacingPattern> patterns, String text, boolean debug, GenericMailet logOwner) {
        for (ReplacingPattern replacingPattern : patterns) {
            boolean changed;
            do {
                changed = false;
                Matcher matcher = replacingPattern.getMatcher().matcher(text);
                if (matcher.find()) {
                    String replaced = matcher.replaceAll(replacingPattern.getSubstitution());
                    if (debug) {
                        logOwner.log("Subject rule match: " + replacingPattern.getMatcher());
                    }
                    text = replaced;
                    changed = true;
                }
            } while (replacingPattern.isRepeat() && changed);
        }
        
        return text;
    }

    @Override
    public void service(Mail mail) throws MailetException {
        try {
            boolean subjectChanged = applySubjectReplacingUnits(mail);
            boolean contentChanged = applyBodyReplacingUnits(mail);

            if (charset.isPresent() && !contentChanged) {
                mail.getMessage().setContent(mail.getMessage().getContent(), getContentType(mail));
            }

            if (subjectChanged || contentChanged) {
                mail.getMessage().saveChanges();
            }
        } catch (MessagingException e) {
            throw new MailetException("Error in replace", e);
            
        } catch (IOException e) {
            throw new MailetException("Error in replace", e);
        }
    }

    private boolean applySubjectReplacingUnits(Mail mail) throws MessagingException {
        if (!replaceConfig.getSubjectReplacingUnits().isEmpty()) {
            String subject = applyPatterns(replaceConfig.getSubjectReplacingUnits(), 
                    Strings.nullToEmpty(mail.getMessage().getSubject()), debug, this);
            if (charset.isPresent()) {
                mail.getMessage().setSubject(subject, charset.get().name());
            }
            else {
                mail.getMessage().setSubject(subject);
            }
            return true;
        }
        return false;
    }

    private boolean applyBodyReplacingUnits(Mail mail) throws IOException, MessagingException, ParseException {
        if (!replaceConfig.getBodyReplacingUnits().isEmpty()) {
            Object bodyObj = mail.getMessage().getContent();
            if (bodyObj instanceof String) {
                String body = applyPatterns(replaceConfig.getBodyReplacingUnits(), 
                        Strings.nullToEmpty((String) bodyObj), debug, this);
                setContent(mail, body);
                return true;
            }
        }
        return false;
    }

    private void setContent(Mail mail, String body) throws MessagingException, ParseException {
        mail.getMessage().setContent(body, getContentType(mail));
    }

    private String getContentType(Mail mail) throws MessagingException, ParseException {
        String contentTypeAsString = mail.getMessage().getContentType();
        if (charset.isPresent()) {
            ContentType contentType = new ContentType(contentTypeAsString);
            contentType.setParameter("charset", charset.get().name());
            return contentType.toString();
        }
        return contentTypeAsString;
    }

}
