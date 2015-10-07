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
package org.apache.james.mailbox.maildir;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Flags;

public class MaildirMessageName {

    // the flags in Maildir message names
    public static final String FLAG_DRAFT = "D";
    public static final String FLAG_FLAGGED = "F";
    public static final String FLAG_ANSWERD = "R";
    public static final String FLAG_SEEN = "S";
    public static final String FLAG_DELETED = "T";

    // patterns
    public static final String PATTERN_STRING_MESSAGE_NAME = "\\d+\\.\\w+\\..+?";
    public static final String PATTERN_STRING_FLAGS = ":2,[DFRST]*";
    public static final String PATTERN_STRING_SIZE = ",S=\\d+";
    public static final Pattern PATTERN_MESSAGE =
        Pattern.compile(PATTERN_STRING_MESSAGE_NAME + optional(PATTERN_STRING_SIZE) + optional(PATTERN_STRING_FLAGS));
    
    public static final Pattern PATTERN_UNSEEN_MESSAGES =
        Pattern.compile(PATTERN_STRING_MESSAGE_NAME + PATTERN_STRING_SIZE + optional(":2,[^S]*"));
    
    public static final FilenameFilter FILTER_UNSEEN_MESSAGES = createRegexFilter(PATTERN_UNSEEN_MESSAGES);
    
    public static final Pattern PATTERN_DELETED_MESSAGES =
        Pattern.compile(PATTERN_STRING_MESSAGE_NAME + PATTERN_STRING_SIZE + ":2,.*" + FLAG_DELETED);
    
    public static final FilenameFilter FILTER_DELETED_MESSAGES = createRegexFilter(PATTERN_DELETED_MESSAGES);
    
    /**
     * The number of deliveries done by the server since its last start
     */
    private static AtomicInteger deliveries = new AtomicInteger(0);
    
    /**
     * A random generator for the random part in the unique message names
     */
    private static Random random = new Random();

    /**
     * The process id of the server process
     */
    private static String processName = ManagementFactory.getRuntimeMXBean().getName();
    static {
        String[] parts = processName.split("@");
        if (parts.length > 1)
            processName = parts[0];
    }
    
    /**
     * The host name of the machine the server is running on
     */
    private static String currentHostname;
    static {
        try {
            currentHostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            currentHostname = "localhost";
        }
    }

    private String fullName;
    private File file;
    private MaildirFolder parentFolder;
    private String timestamp;
    private String uniqueString;
    private String hostnameAndMeta; // tim-erwin.de,S=1000:2,RS
    private String hostname;
    private String sizeString;
    private String flagsString;
    private boolean isSplit;
    private Date internalDate;
    private Long size;
    private Flags flags;
    private boolean messageNameStrictParse = false;
    
    public MaildirMessageName(MaildirFolder parentFolder, String fullName) {
        this.parentFolder = parentFolder;
        setFullName(fullName);
    }

    public boolean isMessageNameStrictParse() {
        return messageNameStrictParse;
    }

    public void setMessageNameStrictParse(boolean messageNameStrictParse) {
        this.messageNameStrictParse = messageNameStrictParse;
    }

    /**
     * Tests whether the file or directory belonging to this {@link MaildirFolder} exists.
     * If the file exists, its absolute path is written to absPath.
     * TODO: If the flags have changed or the file doesn't exist any more, the uidlist should be updated
     * @return true if the file or directory belonging to this {@link MaildirFolder} exists ; false otherwise 
     */
    public boolean exists() {
        if (file != null && file.isFile())
            return true;
        File assumedFile1 = new File(parentFolder.getCurFolder(), fullName);
        if (assumedFile1.isFile()) {
            file = assumedFile1;
            return true;
        }
        File assumedFile2 = new File(parentFolder.getNewFolder(), fullName);
        if (assumedFile2.isFile()) {
            file = assumedFile2;
            return true;
        }
        // check if maybe the flags have changed which means
        // list the files in the cur and new folder and check if the message is there
        FilenameFilter filter = getFilenameFilter();
        File[] matchingFiles1 = parentFolder.getCurFolder().listFiles(filter);
        if (matchingFiles1.length == 1) {
            setFullName(matchingFiles1[0].getName());
            file = matchingFiles1[0];
            return true;
        }
        File[] matchingFiles2 = parentFolder.getNewFolder().listFiles(filter);
        if (matchingFiles2.length == 1) {
            setFullName(matchingFiles2[0].getName());
            file = matchingFiles2[0];
            return true;
        }
        return false;
    }
    
    /**
     * Sets the fullName of this {@link MaildirMessageName} if different from the current one.
     * As this invalidates the parsed elements, they are being reset.
     * @param fullName A name of a message file in the correct Maildir format
     */
    public void setFullName(String fullName) {
        if (this.fullName == null || !this.fullName.equals(fullName)) {
            this.fullName = fullName;
            this.file = null;
            this.isSplit = false;
            this.internalDate = null;
            this.size = null;
            this.flags = null;
        }
    }
    
    /**
     * Returns the full name of this message including size and flags if available.
     * @return the full name of this message
     */
    public String getFullName() {
        if (this.fullName == null) {
            StringBuilder fullBuffer = new StringBuilder();
            fullBuffer.append(timestamp);
            fullBuffer.append(".");
            fullBuffer.append(uniqueString);
            fullBuffer.append(".");
            fullBuffer.append(hostname);
            if (sizeString != null)
                fullBuffer.append(sizeString);
            if (flagsString != null)
                fullBuffer.append(flagsString);
            fullName = fullBuffer.toString();
        }
        return fullName;
    }
    
    /**
     * Returns a {@link File} object of the message denoted by this {@link MaildirMessageName}.
     * Also checks for different flags if it cannot be found directly.
     * @return A {@link File} object
     * @throws FileNotFoundException If there is no file for the given name
     */
    public File getFile() throws FileNotFoundException {
        if (exists())
            return file;
        else
            throw new FileNotFoundException("There is no file for message name " + fullName
                    + " in mailbox " + parentFolder.getRootFile().getAbsolutePath());
    }
    
    /**
     * Creates a filter which matches the message file even if the flags have changed 
     * @return filter for this message
     */
    public FilenameFilter getFilenameFilter() {
        split();
        StringBuilder pattern = new StringBuilder();
        pattern.append(timestamp);
        pattern.append("\\.");
        pattern.append(uniqueString);
        pattern.append("\\.");
        pattern.append(hostname);
        pattern.append(".*");
        return createRegexFilter(Pattern.compile(pattern.toString()));
    }
    
    /**
     * Splits up the full file name if necessary.
     */
    private void split() {
        if (!isSplit) {
            splitFullName();
            splitHostNameAndMeta();
            isSplit = true;
        }
    }
    
    /**
     * Splits up the full file name into its main components timestamp,
     * uniqueString and hostNameAndMeta and fills the respective variables.
     */
    private void splitFullName() {
        int firstEnd = fullName.indexOf('.');
        int secondEnd = fullName.indexOf('.', firstEnd + 1);
        timestamp = fullName.substring(0, firstEnd);
        uniqueString = fullName.substring(firstEnd + 1, secondEnd);
        hostnameAndMeta = fullName.substring(secondEnd + 1, fullName.length());
    }
    
    /**
     * Splits up the third part of the file name (e.g. tim-erwin.de,S=1000:2,RS)
     * into its components hostname, size and flags and fills the respective variables.
     */
    private void splitHostNameAndMeta() {
        String[] hostnamemetaFlags = hostnameAndMeta.split(":", 2);
        if (hostnamemetaFlags.length >= 1) {
          this.hostnameAndMeta = hostnamemetaFlags[0];
          int firstEnd = hostnameAndMeta.indexOf(',');

          // read size field if existent
          if (firstEnd > 0) {
            hostname = hostnameAndMeta.substring(0, firstEnd);
            String attrStr = hostnameAndMeta.substring(firstEnd);
            String[] fields = attrStr.split(",");
            for (String field : fields) {
              if (field.startsWith("S=")) {
                  sizeString = "," + field;
              }
            }
          } else {
            sizeString = null;
            hostname = this.hostnameAndMeta;
          }
        }

        if (hostnamemetaFlags.length >= 2) {
            this.flagsString = ":" + hostnamemetaFlags[1];
        }
        if (isMessageNameStrictParse()) {
            if (sizeString == null) {
                throw new IllegalArgumentException("No message size found in message name: "+ fullName);
            }
            if (flagsString == null) {
                throw new IllegalArgumentException("No flags found in message name: "+ fullName);
            }
        }
    }
    
    /**
     * Sets new flags for this message name.
     * @param flags
     */
    public void setFlags(Flags flags) {
        if (this.flags != flags) {
            split(); // save all parts
            this.flags = flags;
            this.flagsString = encodeFlags(flags);
            this.fullName = null; // invalidate the fullName
        }
    }
    
    /**
     * Decodes the flags part of the file name if necessary and returns the appropriate Flags object.
     * @return The {@link Flags} of this message
     */
    public Flags getFlags() {
        if (flags == null) {
            split();
            if (flagsString == null)
                return null;
            if (flagsString.length() >= 3)
                flags = decodeFlags(flagsString.substring(3)); // skip the ":2," part
        }
        return flags;
    }
    
    /**
     * Decodes the size part of the file name if necessary and returns the appropriate Long.
     * @return The size of this message as a {@link Long}
     */
    public Long getSize() {
        if (size == null) {
            split();
            if (sizeString == null)
                return null;
            if (!sizeString.startsWith(",S="))
                return null;
            size = Long.valueOf(sizeString.substring(3)); // skip the ",S=" part
        }
        return size;
    }
    
    /**
     * Decodes the time part of the file name if necessary and returns the appropriate Date.
     * @return The time of this message as a {@link Date}
     */
    public Date getInternalDate() {
        if (internalDate == null) {
            split();
            if (timestamp == null)
                return null;
            internalDate = new Date(Long.valueOf(timestamp) * 1000);
        }
        return internalDate;
    }
    
    /**
     * Composes the base name consisting of timestamp, unique string and host name
     * witout the size and flags.
     * @return The base name
     */
    public String getBaseName() {
        split();
        StringBuilder baseName = new StringBuilder();
        baseName.append(timestamp);
        baseName.append(".");
        baseName.append(uniqueString);
        baseName.append(".");
        baseName.append(hostname);
        return baseName.toString();
    }
    
    /**
     * Creates a String that represents the provided Flags  
     * @param flags The flags to encode
     * @return A String valid for Maildir
     */
    public String encodeFlags(Flags flags) {
        StringBuilder localFlagsString = new StringBuilder(":2,");
        if (flags.contains(Flags.Flag.DRAFT))
            localFlagsString.append(FLAG_DRAFT);
        if (flags.contains(Flags.Flag.FLAGGED))
            localFlagsString.append(FLAG_FLAGGED);
        if (flags.contains(Flags.Flag.ANSWERED))
            localFlagsString.append(FLAG_ANSWERD);
        if (flags.contains(Flags.Flag.SEEN))
            localFlagsString.append(FLAG_SEEN);
        if (flags.contains(Flags.Flag.DELETED))
            localFlagsString.append(FLAG_DELETED);
        return localFlagsString.toString();
    }
    
    /**
     * Takes a String which is "Maildir encoded" and translates it
     * into a {@link Flags} object.
     * @param flagsString The String with the flags
     * @return A Flags object containing the flags read form the String
     */
    public Flags decodeFlags(String flagsString) {
        Flags localFlags = new Flags();
        if (flagsString.contains(FLAG_DRAFT))
            localFlags.add(Flags.Flag.DRAFT);
        if (flagsString.contains(FLAG_FLAGGED))
            localFlags.add(Flags.Flag.FLAGGED);
        if (flagsString.contains(FLAG_ANSWERD))
            localFlags.add(Flags.Flag.ANSWERED);
        if (flagsString.contains(FLAG_SEEN))
            localFlags.add(Flags.Flag.SEEN);
        if (flagsString.contains(FLAG_DELETED))
            localFlags.add(Flags.Flag.DELETED);
        return localFlags;
    }
    
    /**
     * Returns and increases a global counter for the number
     * of deliveries done since the server has been started.
     * This is used for the creation of message names.
     * @return The number of (attempted) deliveries until now
     */
    static private long getNextDeliveryNumber() {
        return deliveries.getAndIncrement();
    }
    
    /**
     * Create a name for a message according to <a href="http://cr.yp.to/proto/maildir.html" /><br/>
     * The following elements are used:
     * <br><br/>
     * "A unique name has three pieces, separated by dots. On the left is the result of time()
     * or the second counter from gettimeofday(). On the right is the result of gethostname().
     * (To deal with invalid host names, replace / with \057 and : with \072.)
     * In the middle is a delivery identifier, discussed below.
     * <br/><br/>
     * Modern delivery identifiers are created by concatenating enough of the following strings
     * to guarantee uniqueness:
     * <br/><br/>
     * [...]<br/>
     * * Rn, where n is (in hexadecimal) the output of the operating system's unix_cryptorandomnumber() system call, or an equivalent source such as /dev/urandom. Unfortunately, some operating systems don't include cryptographic random number generators.<br/>
     * [...]<br/>
     * * Mn, where n is (in decimal) the microsecond counter from the same gettimeofday() used for the left part of the unique name.<br/>
     * * Pn, where n is (in decimal) the process ID.<br/>
     * * Qn, where n is (in decimal) the number of deliveries made by this process.<br/>
     * <br/>
     * [...]"
     * 
     * @return unique message name
     */
    public static MaildirMessageName createUniqueName(MaildirFolder parentFolder, long size) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        timestamp = timestamp.substring(0, timestamp.length()-3); // time in seconds
        StringBuilder uniquePart = new StringBuilder();
        uniquePart.append(Integer.toHexString(random.nextInt())); // random number as hex
        uniquePart.append(timestamp.substring(timestamp.length()-3)); // milliseconds
        uniquePart.append(processName); // process name
        uniquePart.append(getNextDeliveryNumber()); // delivery number
        String sizeString = ",S=" + String.valueOf(size);
        String fullName = timestamp + "." + uniquePart.toString() + "." + currentHostname + sizeString;
        MaildirMessageName uniqueName = new MaildirMessageName(parentFolder, fullName);
        uniqueName.timestamp = timestamp;
        uniqueName.uniqueString = uniquePart.toString();
        uniqueName.hostname = currentHostname;
        uniqueName.sizeString = sizeString;
        uniqueName.isSplit = true;
        uniqueName.size = size;
        return uniqueName;
    }
    
    public static FilenameFilter createRegexFilter(final Pattern pattern) {
        return new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                Matcher matcher = pattern.matcher(name);
                return matcher.matches();
            }
        };
    }
    
    public static String optional(String pattern) {
        return "(" + pattern + ")?";
    }
    
    @Override
    public String toString() {
        return getFullName();
    }
}
