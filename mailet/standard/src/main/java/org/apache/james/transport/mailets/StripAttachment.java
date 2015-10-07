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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import org.apache.commons.io.FileUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;

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
 *     &lt;directory &gt;c:\temp\james_attach &lt;/directory&gt;   &lt;!-- The directory to save to -- &gt;
 *     &lt;remove &gt;all &lt;/remove&gt;   &lt;!-- either &quot;no&quot;, &quot;matched&quot;, &quot;all&quot; -- &gt;
 *     &lt;!-- attribute&gt;my.attribute.name&lt;/attribute --&gt;
 *   &lt;/mailet &gt;
 * </pre>
 * 
 * </p>
 */
public class StripAttachment extends GenericMailet {

    public static final String PATTERN_PARAMETER_NAME = "pattern";

    public static final String NOTPATTERN_PARAMETER_NAME = "notpattern";

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

    public static final String REMOVED_ATTACHMENTS_ATTRIBUTE_KEY = "org.apache.james.mailet.standard.mailets.StripAttachment.removed";

    public static final String SAVED_ATTACHMENTS_ATTRIBUTE_KEY = "org.apache.james.mailet.standard.mailets.StripAttachment.saved";

    private String removeAttachments = null;

    private String directoryName = null;

    private String attributeName = null;

    private Pattern regExPattern = null;

    private Pattern notregExPattern = null;

    private boolean decodeFilename = false;

    private Pattern[] replaceFilenamePatterns = null;

    private String[] replaceFilenameSubstitutions = null;

    private Integer[] replaceFilenameFlags = null;

    private static boolean getBooleanParameter(String v, boolean def) {
        return def ? !(v != null && (v.equalsIgnoreCase("false") || v
                .equalsIgnoreCase("no"))) : v != null
                && (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes"));
    }

    /**
     * Checks if the mandatory parameters are present, creates the directory to
     * save the files ni (if not present).
     * 
     * @throws MailetException
     */
    public void init() throws MailetException {
        String patternString = getInitParameter(PATTERN_PARAMETER_NAME);
        String notpatternString = getInitParameter(NOTPATTERN_PARAMETER_NAME);
        if (patternString == null && notpatternString == null) {
            throw new MailetException("No value for " + PATTERN_PARAMETER_NAME
                    + " parameter was provided.");
        }

        directoryName = getInitParameter(DIRECTORY_PARAMETER_NAME);
        attributeName = getInitParameter(ATTRIBUTE_PARAMETER_NAME);

        removeAttachments = getInitParameter(REMOVE_ATTACHMENT_PARAMETER_NAME,
                REMOVE_NONE).toLowerCase();
        if (!REMOVE_MATCHED.equals(removeAttachments)
                && !REMOVE_ALL.equals(removeAttachments)) {
            removeAttachments = REMOVE_NONE;
        }

        try {
            // if (patternString != null) regExPattern = new
            // Perl5Compiler().compile(patternString);
            if (patternString != null)
                regExPattern = Pattern.compile(patternString);
        } catch (Exception e) {
            throw new MailetException("Could not compile regex ["
                    + patternString + "].");
        }
        try {
            // if (notpatternString != null) notregExPattern = new
            // Perl5Compiler().compile(notpatternString);
            if (notpatternString != null)
                notregExPattern = Pattern.compile(notpatternString);
        } catch (Exception e) {
            throw new MailetException("Could not compile regex ["
                    + notpatternString + "].");
        }

        if (directoryName != null) {
            try {
                FileUtils.forceMkdir(new File(directoryName));
            } catch (Exception e) {
                throw new MailetException("Could not create directory ["
                        + directoryName + "].", e);
            }
        }

        decodeFilename = getBooleanParameter(
                getInitParameter(DECODE_FILENAME_PARAMETER_NAME),
                decodeFilename);
        if (getInitParameter(REPLACE_FILENAME_PATTERN_PARAMETER_NAME) != null) {
            PatternList pl = ReplaceContent
                    .getPatternsFromString(getInitParameter(REPLACE_FILENAME_PATTERN_PARAMETER_NAME));
            List<Pattern> patterns = pl.getPatterns();
            replaceFilenamePatterns = patterns.toArray(new Pattern[patterns.size()]);
            List<String> substitutions = pl.getSubstitutions();
            replaceFilenameSubstitutions = substitutions.toArray(new String[substitutions.size()]);
            List<Integer> flags = pl.getFlags();
            replaceFilenameFlags = flags.toArray(new Integer[flags.size()]);
        }

        String toLog = String.format("StripAttachment is initialised with regex pattern [%s / %s]",
                patternString, notpatternString);
        if (directoryName != null) {
            toLog += String.format(" and will save to directory [%s]", directoryName);
        }
        if (attributeName != null) {
            toLog += String.format(" and will store attachments to attribute [%s]", attributeName);
        }
        log(toLog);
    }

    /**
     * Service the mail: scan it for attchemnts matching the pattern, store the
     * content of a matchin attachment in the given directory.
     * 
     * @param mail
     *            The mail to service
     * @throws MailetException
     *             Thrown when an error situation is encountered.
     */
    public void service(Mail mail) throws MailetException {
        MimeMessage message;
        try {
            message = mail.getMessage();
        } catch (MessagingException e) {
            throw new MailetException(
                    "Could not retrieve message from Mail object", e);
        }
        // All MIME messages with an attachment are multipart, so we do nothing
        // if it is not mutlipart
        try {
            if (message.isMimeType("multipart/*")) {
                analyseMultipartPartMessage(message, mail);
            }
        } catch (MessagingException e) {
            throw new MailetException("Could not retrieve contenttype of message.", e);
        } catch (Exception e) {
            throw new MailetException("Could not analyse message.", e);
        }
    }

    /**
     * returns a String describing this mailet.
     * 
     * @return A desciption of this mailet
     */
    public String getMailetInfo() {
        return "StripAttachment";
    }

    /**
     * Checks every part in this part (if it is a Multipart) for having a
     * filename that matches the pattern. If the name matches, the content of
     * the part is stored (using its name) in te given diretcory.
     * 
     * Note: this method is recursive.
     * 
     * @param part
     *            The part to analyse.
     * @param mail
     * @return
     * @throws Exception
     */
    private boolean analyseMultipartPartMessage(Part part, Mail mail)
            throws Exception {
        if (part.isMimeType("multipart/*")) {
            try {
                Multipart multipart = (Multipart) part.getContent();
                boolean atLeastOneRemoved = false;
                int numParts = multipart.getCount();
                for (int i = 0; i < numParts; i++) {
                    Part p = multipart.getBodyPart(i);
                    if (p.isMimeType("multipart/*")) {
                        atLeastOneRemoved |= analyseMultipartPartMessage(p,
                                mail);
                    } else {
                        boolean removed = checkMessageRemoved(p, mail);
                        if (removed) {
                            multipart.removeBodyPart(i);
                            atLeastOneRemoved = true;
                            i--;
                            numParts--;
                        }
                    }
                }
                if (atLeastOneRemoved) {
                    part.setContent(multipart);
                    if (part instanceof Message) {
                        ((Message) part).saveChanges();
                    }
                }
                return atLeastOneRemoved;
            } catch (Exception e) {
                log("Could not analyse part.", e);
            }
        }
        return false;
    }

    private boolean checkMessageRemoved(Part part, Mail mail)
            throws MessagingException, Exception {
        String fileName;
        fileName = part.getFileName();

        // filename or name of part can be null, so we have to be careful
        boolean ret = false;

        if (fileName != null) {
            if (decodeFilename)
                fileName = MimeUtility.decodeText(fileName);

            if (replaceFilenamePatterns != null)
                fileName = ReplaceContent.applyPatterns(
                        replaceFilenamePatterns, replaceFilenameSubstitutions,
                        replaceFilenameFlags, fileName, 0, this);

            if (fileNameMatches(fileName)) {
                if (directoryName != null) {
                    String filename = saveAttachmentToFile(part, fileName);
                    if (filename != null) {
                        @SuppressWarnings("unchecked")
                        Collection<String> c = (Collection<String>) mail
                                .getAttribute(SAVED_ATTACHMENTS_ATTRIBUTE_KEY);
                        if (c == null) {
                            c = new ArrayList<String>();
                            mail.setAttribute(SAVED_ATTACHMENTS_ATTRIBUTE_KEY,
                                    (ArrayList<String>) c);
                        }
                        c.add(filename);
                    }
                }
                if (attributeName != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, byte[]> m = (Map<String, byte[]>) mail.getAttribute(attributeName);
                    if (m == null) {
                        m = new LinkedHashMap<String, byte[]>();
                        mail.setAttribute(attributeName, (LinkedHashMap<String, byte[]>) m);
                    }
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    OutputStream os = new BufferedOutputStream(
                            byteArrayOutputStream);
                    part.writeTo(os);
                    m.put(fileName, byteArrayOutputStream.toByteArray());
                }
                if (removeAttachments.equals(REMOVE_MATCHED)) {
                    ret = true;
                }
            }
            if (!ret) {
                ret = removeAttachments.equals(REMOVE_ALL);
            }
            if (ret) {
                @SuppressWarnings("unchecked")
                Collection<String> c = (Collection<String>) mail
                        .getAttribute(REMOVED_ATTACHMENTS_ATTRIBUTE_KEY);
                if (c == null) {
                    c = new ArrayList<String>();
                    mail.setAttribute(REMOVED_ATTACHMENTS_ATTRIBUTE_KEY,
                            (ArrayList<String>) c);
                }
                c.add(fileName);
            }
        }
        return ret;
    }

    /**
     * Checks if the given name matches the pattern.
     * 
     * @param name
     *            The name to check for a match.
     * @return True if a match is found, false otherwise.
     */
    private boolean fileNameMatches(String name) {
        boolean result = true;
        if (regExPattern != null)
            result = regExPattern.matcher(name).matches();
        if (result && notregExPattern != null)
            result = !notregExPattern.matcher(name).matches();

        String log = "attachment " + name + " ";
        if (!result)
            log += "does not match";
        else
            log += "matches";
        log(log);
        return result;
    }

    /**
     * Saves the content of the part to a file in the given directoy, using the
     * name of the part. If a file with that name already exists, it will
     * 
     * @param part
     *            The MIME part to save.
     * @return
     * @throws Exception
     */
    private String saveAttachmentToFile(Part part, String fileName)
            throws Exception {
        BufferedOutputStream os = null;
        InputStream is = null;
        File f = null;
        try {
            if (fileName == null)
                fileName = part.getFileName();
            int pos = -1;
            if (fileName != null) {
                pos = fileName.lastIndexOf(".");
            }
            String prefix = pos > 0 ? (fileName.substring(0, pos)) : fileName;
            String suffix = pos > 0 ? (fileName.substring(pos)) : ".bin";
            while (prefix.length() < 3)
                prefix += "_";
            if (suffix.length() == 0)
                suffix = ".bin";
            f = File.createTempFile(prefix, suffix, new File(directoryName));
            log("saving content of " + f.getName() + "...");
            os = new BufferedOutputStream(new FileOutputStream(f));
            is = part.getInputStream();
            if (!(is instanceof BufferedInputStream)) {
                is = new BufferedInputStream(is);
            }
            int c;
            while ((c = is.read()) != -1) {
                os.write(c);
            }

            return f.getName();
        } catch (Exception e) {
            log("Error while saving contents of ["
                    + (f != null ? f.getName() : (part != null ? part
                            .getFileName() : "NULL")) + "].", e);
            throw e;
        } finally {
            is.close();
            os.close();
        }
    }

}
