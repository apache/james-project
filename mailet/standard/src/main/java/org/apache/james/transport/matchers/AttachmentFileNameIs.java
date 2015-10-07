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

import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.io.UnsupportedEncodingException;


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
    
    /** Unzip request parameter. */
    protected static final String UNZIP_REQUEST_PARAMETER = "-z";
    
    /** Debug request parameter. */
    protected static final String DEBUG_REQUEST_PARAMETER = "-d";
    
    /** Match string for zip files. */
    protected static final String ZIP_SUFFIX = ".zip";
    
    /**
     * represents a single parsed file name mask.
     */
    private static class Mask {
        /** true if the mask starts with a wildcard asterisk */
        public boolean suffixMatch;
        
        /** file name mask not including the wildcard asterisk */
        public String matchString;
    }
    
    /**
     * Controls certain log messages.
     */
    protected boolean isDebug = false;

    /** contains ParsedMask instances, setup by init */
    private Mask[] masks = null;
    
    /** True if unzip is requested. */
    protected boolean unzipIsRequested;
    

    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericMatcher#init()
     */
    public void init() throws MessagingException {
        /* sets up fileNameMasks variable by parsing the condition */
        
        StringTokenizer st = new StringTokenizer(getCondition(), ", ", false);
        ArrayList<Mask> theMasks = new ArrayList<Mask>(20);
        while (st.hasMoreTokens()) {
            String fileName = st.nextToken();
            
            // check possible parameters at the beginning of the condition
            if (theMasks.size() == 0 && fileName.equalsIgnoreCase(UNZIP_REQUEST_PARAMETER)) {
                unzipIsRequested = true;
                log("zip file analysis requested");
                continue;
            }
            if (theMasks.size() == 0 && fileName.equalsIgnoreCase(DEBUG_REQUEST_PARAMETER)) {
                isDebug = true;
                log("debug requested");
                continue;
            }
            Mask mask = new Mask(); 
            if (fileName.startsWith("*")) {
                mask.suffixMatch = true;
                mask.matchString = fileName.substring(1);
            } else {
                mask.suffixMatch = false;
                mask.matchString = fileName;
            }
            mask.matchString = cleanFileName(mask.matchString);
            theMasks.add(mask);
        }
        masks = theMasks.toArray(new Mask[theMasks.size()]);
    }

    /** 
     * Either every recipient is matching or neither of them.
     * @param mail
     * @throws MessagingException if no matching attachment is found and at least one exception was thrown
     */
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        
        try {
            MimeMessage message = mail.getMessage();
            
            if (matchFound(message)) {
                return mail.getRecipients(); // matching file found
            } else {
                return null; // no matching attachment found
            }
            
        } catch (Exception e) {
            if (isDebug) {
                log("Malformed message", e);
            }
            throw new MessagingException("Malformed message", e);
        }
    }
    
    /**
     * Checks if <I>part</I> matches with at least one of the <CODE>masks</CODE>.
     * 
     * @param part
     */
    protected boolean matchFound(Part part) throws Exception {
        
        /*
         * if there is an attachment and no inline text,
         * the content type can be anything
         */
        
        if (part.getContentType() == null ||
            part.getContentType().startsWith("multipart/alternative")) {
            return false;
        }
        
        Object content;
        
        try {
            content = part.getContent();
        } catch (UnsupportedEncodingException uee) {
            // in this case it is not an attachment, so ignore it
            return false;
        }
        
        Exception anException = null;
        
        if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            for (int i = 0; i < multipart.getCount(); i++) {
                try {
                    Part bodyPart = multipart.getBodyPart(i);
                    if (matchFound(bodyPart)) {
                        return true; // matching file found
                    }
                } catch (MessagingException e) {
                    anException = e;
                } // remember any messaging exception and process next bodypart
            }
        } else {
            String fileName = part.getFileName();
            if (fileName != null) {
                fileName = cleanFileName(fileName);
                // check the file name
                if (matchFound(fileName)) {
                    if (isDebug) {
                        log("matched " + fileName);
                    }
                    return true;
                }
                if (unzipIsRequested && fileName.endsWith(ZIP_SUFFIX) && matchFoundInZip(part)){
                    return true;
                }
            }
        }
        
        // if no matching attachment was found and at least one exception was catched rethrow it up
        if (anException != null) {
            throw anException;
        }
        
        return false;
    }

    /**
     * Checks if <I>fileName</I> matches with at least one of the <CODE>masks</CODE>.
     * 
     * @param fileName
     */
    protected boolean matchFound(String fileName) {
        for (Mask mask1 : masks) {
            boolean fMatch;

            //XXX: file names in mail may contain directory - theoretically
            if (mask1.suffixMatch) {
                fMatch = fileName.endsWith(mask1.matchString);
            } else {
                fMatch = fileName.equals(mask1.matchString);
            }
            if (fMatch) {
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
        ZipInputStream zis = new ZipInputStream(part.getInputStream());
        
        try {
            while (true) {
                ZipEntry zipEntry = zis.getNextEntry();
                if (zipEntry == null) {
                    break;
                }
                String fileName = zipEntry.getName();
                if (matchFound(fileName)) {
                    if (isDebug) {
                        log("matched " + part.getFileName() + "(" + fileName + ")");
                    }
                    return true;
                }
            }
            return false;
        } finally {
            zis.close();
        }
    }

    /**
     * Transforms <I>fileName<I> in a trimmed lowercase string usable for matching agains the masks.
     *
     * @param fileName
     */
    protected String cleanFileName(String fileName) {
        return fileName.toLowerCase(Locale.US).trim();
    }
}

