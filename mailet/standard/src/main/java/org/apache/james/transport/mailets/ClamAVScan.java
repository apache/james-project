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

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


/**
 * <P>Does an antivirus scan check using a ClamAV daemon (CLAMD)</P>
 * <p/>
 * <P> Interacts directly with the daemon using the "stream" method,
 * which should have the lowest possible overhead.</P>
 * <P>The CLAMD daemon will typically reside on <I>localhost</I>, but could reside on a
 * different host.
 * It may also consist on a set of multiple daemons, each residing on a different
 * server and on different IP number.
 * In such case a DNS host name with multiple IP addresses (round-robin load sharing)
 * is supported by the mailet (but on the same port number).</P>
 * <p/>
 * <P>Handles the following init parameters:</P>
 * <UL>
 * <LI><CODE>&lt;debug&gt;</CODE>.</LI>
 * <LI><CODE>&lt;host&gt;</CODE>: the host name of the server where CLAMD runs. It can either be
 * a machine name, such as
 * "<code>java.sun.com</code>", or a textual representation of its
 * IP address. If a literal IP address is supplied, only the
 * validity of the address format is checked.
 * If the machine name resolves to multiple IP addresses, <I>round-robin load sharing</I> will
 * be used.
 * The default is <CODE>localhost</CODE>.</LI>
 * <LI><CODE>&lt;port&gt;</CODE>: the port on which CLAMD listens. The default is <I>3310</I>.</LI>
 * <LI><CODE>&lt;maxPings&gt;</CODE>: the maximum number of connection retries during startup.
 * If the value is <I>0</I> no startup test will be done.
 * The default is <I>6</I>.</LI>
 * <LI><CODE>&lt;pingIntervalMilli&gt;</CODE>: the interval (in milliseconds)
 * between each connection retry during startup.
 * The default is <I>30000</I> (30 seconds).</LI>
 * <LI><CODE>&lt;streamBufferSize&gt;</CODE>: the BufferedOutputStream buffer size to use
 * writing to the <I>stream connection</I>. The default is <I>8192</I>.</LI>
 * </UL>
 * <p/>
 * <P>The actions performed are as follows:</P>
 * <UL>
 * <LI>During initialization:</LI>
 * <OL>
 * <LI>Gets all <CODE>config.xml</CODE> parameters, handling the defaults;</LI>
 * <LI>resolves the <CODE>&lt;host&gt;</CODE> parameter, creating the round-robin IP list;</LI>
 * <LI>connects to CLAMD at the first IP in the round-robin list, on
 * the specified <CODE>&lt;port&gt;</CODE>;</LI>
 * <LI>if unsuccessful, retries every <CODE>&lt;pingIntervalMilli&gt;</CODE> milliseconds up to
 * <CODE>&lt;maxPings&gt;</CODE> times;</LI>
 * <LI>sends a <CODE>PING</CODE> request;</LI>
 * <LI>waits for a <CODE>PONG</CODE> answer;</LI>
 * <LI>repeats steps 3-6 for every other IP resolved.
 * </OL>
 * <LI>For every mail</LI>
 * <OL>
 * <LI>connects to CLAMD at the "next" IP in the round-robin list, on
 * the specified <CODE>&lt;port&gt;</CODE>, and increments the "next" index;
 * if the connection request is not accepted tries with the next one
 * in the list unless all of them have failed;</LI>
 * <LI>sends a "<CODE>STREAM</CODE>" request;</LI>
 * <LI>parses the "<CODE>PORT <I>streamPort</I></CODE>" answer obtaining the port number;</LI>
 * <LI>makes a second connection (the <I>stream connection</I>) to CLAMD at the same host (or IP)
 * on the <I>streamPort</I> just obtained;</LI>
 * <LI>sends the mime message to CLAMD (using {@link MimeMessage#writeTo(java.io.OutputStream)})
 * through the <I>stream connection</I>;</LI>
 * <LI>closes the <I>stream connection</I>;</LI>
 * <LI>gets the "<CODE>OK</CODE>" or "<CODE>... FOUND</CODE>" answer from the main connection;</LI>
 * <LI>closes the main connection;</LI>
 * <LI>sets the "<CODE>org.apache.james.infected</CODE>" <I>mail attribute</I> to either
 * "<CODE>true</CODE>" or "<CODE>false</CODE>";</LI>
 * <LI>adds the "<CODE>X-MessageIsInfected</CODE>" <I>header</I> to either
 * "<CODE>true</CODE>" or "<CODE>false</CODE>";</LI>
 * </OL>
 * </UL>
 * <p/>
 * <P>Some notes regarding <a href="http://www.clamav.net/">clamav.conf</a>:</p>
 * <UL>
 * <LI><CODE>LocalSocket</CODE> must be commented out</LI>
 * <LI><CODE>TCPSocket</CODE> must be set to a port# (typically 3310)</LI>
 * <LI><CODE>StreamMaxLength</CODE> must be &gt;= the James config.xml parameter
 * &lt;<CODE>maxmessagesize</CODE>&gt; in SMTP &lt;<CODE>handler</CODE>&gt;</LI>
 * <LI><CODE>MaxThreads</CODE> should? be &gt;= the James config.xml parameter
 * &lt;<CODE>threads</CODE>&gt; in &lt;<CODE>spoolmanager</CODE>&gt;</LI>
 * <LI><CODE>ScanMail</CODE> must be uncommented</LI>
 * </UL>
 * <p/>
 * <P>Here follows an example of config.xml definitions deploying CLAMD on localhost,
 * and handling the infected messages:</P>
 * <PRE><CODE>
 * <p/>
 * ...
 * <p/>
 * &lt;!-- Do an antivirus scan --&gt;
 * &lt;mailet match="All" class="ClamAVScan" onMailetException="ignore"/&gt;
 * <p/>
 * &lt;!-- If infected go to virus processor --&gt;
 * &lt;mailet match="HasMailAttributeWithValue=org.apache.james.infected, true" class="ToProcessor"&gt;
 * &lt;processor&gt; virus &lt;/processor&gt;
 * &lt;/mailet&gt;
 * <p/>
 * &lt;!-- Check attachment extensions for possible viruses --&gt;
 * &lt;mailet match="AttachmentFileNameIs=-d -z *.exe *.com *.bat *.cmd *.pif *.scr *.vbs *.avi *.mp3 *.mpeg *.shs" class="ToProcessor"&gt;
 * &lt;processor&gt; bad-extensions &lt;/processor&gt;
 * &lt;/mailet&gt;
 * <p/>
 * ...
 * <p/>
 * &lt;!-- Messages containing viruses --&gt;
 * &lt;processor name="virus"&gt;
 * <p/>
 * &lt;!-- To avoid a loop while bouncing --&gt;
 * &lt;mailet match="All" class="SetMailAttribute"&gt;
 * &lt;org.apache.james.infected&gt;true, bouncing&lt;/org.apache.james.infected&gt;
 * &lt;/mailet&gt;
 * <p/>
 * &lt;mailet match="SMTPAuthSuccessful" class="Bounce"&gt;
 * &lt;sender&gt;bounce-admin@xxx.com&lt;/sender&gt;
 * &lt;inline&gt;heads&lt;/inline&gt;
 * &lt;attachment&gt;none&lt;/attachment&gt;
 * &lt;notice&gt; Warning: We were unable to deliver the message below because it was found infected by virus(es). &lt;/notice&gt;
 * &lt;/mailet&gt;
 * <p/>
 * &lt;!--
 * &lt;mailet match="All" class="ToRepository"&gt;
 * &lt;repositoryPath&gt;file://var/mail/infected/&lt;/repositoryPath&gt;
 * &lt;/mailet&gt;
 * --&gt;
 * <p/>
 * &lt;mailet match="All" class="Null" /&gt;
 * &lt;/processor&gt;
 * </CODE></PRE>
 *
 * @version 2.2.1
 * @see <a href="http://www.clamav.net/">ClamAV Home Page</a>
 * @see <a href="http://www.sosdg.org/clamav-win32/">ClamAV For Windows</a>
 * @since 2.2.1
 */
public class ClamAVScan extends GenericMailet {

    private static final int DEFAULT_PORT = 3310;

    private static final int DEFAULT_MAX_PINGS = 6;

    private static final int DEFAULT_PING_INTERVAL_MILLI = 30000;

    private static final int DEFAULT_STREAM_BUFFER_SIZE = 8192;

    //private static final int DEFAULT_CONNECTION_TIMEOUT = 20000;

    private static final String STREAM_PORT_STRING = "PORT ";

    private static final String FOUND_STRING = "FOUND";

    private static final String MAIL_ATTRIBUTE_NAME = "org.apache.james.infected";

    private static final String HEADER_NAME = "X-MessageIsInfected";

    /**
     * Holds value of property debug.
     */
    private boolean debug;

    /**
     * Holds value of property host.
     */
    private String host;

    /**
     * Holds value of property port.
     */
    private int port;

    /**
     * Holds value of property maxPings.
     */
    private int maxPings;

    /**
     * Holds value of property pingIntervalMilli.
     */
    private int pingIntervalMilli;

    /**
     * Holds value of property streamBufferSize.
     */
    private int streamBufferSize;

    /**
     * Holds value of property addresses.
     */
    private InetAddress[] addresses;

    /**
     * Holds the index of the next address to connect to
     */
    private int nextAddressIndex;

    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "Antivirus Check using ClamAV (CLAMD)";
    }

    /**
     * Gets the expected init parameters.
     */
    protected String[] getAllowedInitParameters() {
        return new String[]{
                //            "static",
                "debug",
                "host",
                "port",
                "maxPings",
                "pingIntervalMilli",
                "streamBufferSize"
        };
    }

    /**
     * Initializer for property debug.
     */
    protected void initDebug() {
        String debugParam = getInitParameter("debug");
        setDebug((debugParam == null) ? false : Boolean.valueOf(debugParam));
    }

    /**
     * Getter for property debug.
     *
     * @return Value of property debug.
     */
    public boolean isDebug() {
        return this.debug;
    }

    /**
     * Setter for property debug.
     *
     * @param debug New value of property debug.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Initializer for property host.
     *
     * @throws UnknownHostException if unable to resolve the host name, or if invalid
     */
    protected void initHost() throws UnknownHostException {
        setHost(getInitParameter("host"));
        if (isDebug()) {
            log("host: " + getHost());
        }
    }

    /**
     * Getter for property host.
     *
     * @return Value of property host.
     */
    public String getHost() {

        return this.host;
    }

    /**
     * Setter for property host.
     * Resolves also the host name into the corresponding IP addresses, issues
     * a {@link #setAddresses} and resets the <CODE>nextAddressIndex</CODE>
     * variable to <I>0</I> for dealing with <I>round-robin</I>.
     *
     * @param host New value of property host.
     * @throws UnknownHostException if unable to resolve the host name, or if invalid
     */
    public void setHost(String host) throws UnknownHostException {

        this.host = host;

        setAddresses(InetAddress.getAllByName(host));

        nextAddressIndex = 0;
    }

    /**
     * Initializer for property port.
     */
    protected void initPort() {
        String portParam = getInitParameter("port");
        setPort((portParam == null) ? DEFAULT_PORT : Integer.parseInt(portParam));
        if (isDebug()) {
            log("port: " + getPort());
        }
    }

    /**
     * Getter for property port.
     *
     * @return Value of property port.
     */
    public int getPort() {

        return this.port;
    }

    /**
     * Setter for property port.
     *
     * @param port New value of property port.
     */
    public void setPort(int port) {

        this.port = port;
    }

    /**
     * Initializer for property maxPings.
     */
    protected void initMaxPings() {
        String maxPingsParam = getInitParameter("maxPings");
        setMaxPings((maxPingsParam == null) ? DEFAULT_MAX_PINGS : Integer.parseInt(maxPingsParam));
        if (isDebug()) {
            log("maxPings: " + getMaxPings());
        }
    }

    /**
     * Getter for property maxPings.
     *
     * @return Value of property maxPings.
     */
    public int getMaxPings() {

        return this.maxPings;
    }

    /**
     * Setter for property maxPings.
     *
     * @param maxPings New value of property maxPings.
     */
    public void setMaxPings(int maxPings) {

        this.maxPings = maxPings;
    }

    /**
     * Initializer for property pingIntervalMilli.
     */
    protected void initPingIntervalMilli() {
        String pingIntervalMilliParam = getInitParameter("pingIntervalMilli");
        setPingIntervalMilli((pingIntervalMilliParam == null) ? DEFAULT_PING_INTERVAL_MILLI : Integer.parseInt(pingIntervalMilliParam));
        if (isDebug()) {
            log("pingIntervalMilli: " + getPingIntervalMilli());
        }
    }

    /**
     * Getter for property pingIntervalMilli.
     *
     * @return Value of property pingIntervalMilli.
     */
    public int getPingIntervalMilli() {

        return this.pingIntervalMilli;
    }

    /**
     * Setter for property pingIntervalMilli.
     *
     * @param pingIntervalMilli New value of property pingIntervalMilli.
     */
    public void setPingIntervalMilli(int pingIntervalMilli) {

        this.pingIntervalMilli = pingIntervalMilli;
    }

    /**
     * Initializer for property streamBufferSize.
     */
    protected void initStreamBufferSize() {
        String streamBufferSizeParam = getInitParameter("streamBufferSize");
        setStreamBufferSize((streamBufferSizeParam == null) ? DEFAULT_STREAM_BUFFER_SIZE : Integer.parseInt(streamBufferSizeParam));
        if (isDebug()) {
            log("streamBufferSize: " + getStreamBufferSize());
        }
    }

    /**
     * Getter for property streamBufferSize.
     *
     * @return Value of property streamBufferSize.
     */
    public int getStreamBufferSize() {

        return this.streamBufferSize;
    }

    /**
     * Setter for property streamBufferSize.
     *
     * @param streamBufferSize New value of property streamBufferSize.
     */
    public void setStreamBufferSize(int streamBufferSize) {

        this.streamBufferSize = streamBufferSize;
    }

    /**
     * Indexed getter for property addresses.
     *
     * @param index Index of the property.
     * @return Value of the property at <CODE>index</CODE>.
     */
    protected InetAddress getAddresses(int index) {

        return this.addresses[index];
    }

    /**
     * Getter for property addresses.
     *
     * @return Value of property addresses.
     */
    protected InetAddress[] getAddresses() {

        return this.addresses;
    }

    /**
     * Setter for property addresses.
     *
     * @param addresses New value of property addresses.
     */
    protected void setAddresses(InetAddress[] addresses) {

        this.addresses = addresses;
    }

    /**
     * Getter for property nextAddress.
     * <p/>
     * Gets the address of the next CLAMD server to connect to in this round, using round-robin.
     * Increments the nextAddressIndex for the next round.
     *
     * @return Value of property address.
     */
    protected synchronized InetAddress getNextAddress() {

        InetAddress address = getAddresses(nextAddressIndex);

        nextAddressIndex++;
        if (nextAddressIndex >= getAddressesCount()) {
            nextAddressIndex = 0;
        }

        return address;
    }

    /**
     * Getter for property addressesCount.
     *
     * @return Value of property addressesCount.
     */
    public int getAddressesCount() {
        return getAddresses().length;
    }

    /**
     * Gets a Socket connected to CLAMD.
     * <p/>
     * Will loop though the round-robin address list until the first one accepts
     * the connection.
     *
     * @return a socket connected to CLAMD
     * @throws MessagingException if no CLAMD in the round-robin address list has accepted the connection
     */
    protected Socket getClamdSocket() throws MessagingException {

        InetAddress address;

        Set<InetAddress> usedAddresses = new HashSet<InetAddress>(getAddressesCount());
        for (; ; ) {
            // this do-while loop is needed because other threads could in the meantime
            // calling getNextAddress(), and because of that the current thread may skip
            // some working address
            do {
                if (usedAddresses.size() >= getAddressesCount()) {
                    String logText = "Unable to connect to CLAMD. All addresses failed.";
                    log(logText + " Giving up.");
                    throw new MessagingException(logText);
                }
                address = getNextAddress();
            } while (!usedAddresses.add(address));
            try {
                // get the socket
                return new Socket(address, getPort());
            } catch (IOException ioe) {
                log("Exception caught acquiring main socket to CLAMD on "
                        + address + " on port " + getPort() + ": " + ioe.getMessage());
                getNextAddress();
                // retry
            }
        }
    }

    /**
     * Mailet initialization routine.
     */
    public void init() throws MessagingException {

        // check that all init parameters have been declared in allowedInitParameters
        checkInitParameters(getAllowedInitParameters());

        try {
            initDebug();
            if (isDebug()) {
                log("Initializing");
            }

            initHost();
            initPort();
            initMaxPings();
            initPingIntervalMilli();
            initStreamBufferSize();

            // If "maxPings is > ping the CLAMD server to check if it is up
            if (getMaxPings() > 0) {
                ping();
            }

        } catch (Exception e) {
            log("Exception thrown", e);
            throw new MessagingException("Exception thrown", e);
        }
    }

    /**
     * Scans the mail.
     *
     * @param mail the mail to scan
     * @throws MessagingException if a problem arises
     */
    public void service(Mail mail) throws MessagingException {

        // if already checked no action
        if (mail.getAttribute(MAIL_ATTRIBUTE_NAME) != null) {
            return;
        }

        MimeMessage mimeMessage = mail.getMessage();

        if (mimeMessage == null) {
            log("Null MimeMessage. Will send to ghost");
            // write mail info to log
            logMailInfo(mail);
            mail.setState(Mail.GHOST);
            return;
        }

        // get the socket
        Socket socket = getClamdSocket();
        BufferedReader reader = null;
        PrintWriter writer = null;
        Socket streamSocket = null;
        BufferedOutputStream bos = null;

        try {

            // prepare the reader and writer for the commands
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "ASCII"));
            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

            // write a request for a port to use for streaming out the data to scan
            writer.println("STREAM");
            writer.flush();

            // parse and get the "stream" port#
            int streamPort = getStreamPortFromAnswer(reader.readLine());

            // get the "stream" socket and the related (buffered) output stream
            streamSocket = new Socket(socket.getInetAddress(), streamPort);
            bos = new BufferedOutputStream(streamSocket.getOutputStream(), getStreamBufferSize());

            // stream out the message to the scanner
            mimeMessage.writeTo(bos);
            bos.flush();
            bos.close();
            streamSocket.close();

            String answer;
            boolean virusFound = false;
            String logMessage = "";
            for (; ; ) {
                answer = reader.readLine();
                if (answer != null) {
                    answer = answer.trim();

                    // if a virus is found the answer will be '... FOUND'
                    if (answer.substring(answer.length() - FOUND_STRING.length()).equals(FOUND_STRING)) {
                        virusFound = true;
                        logMessage = answer + " (by CLAMD on " + socket.getInetAddress() + ")";
                        log(logMessage);
                    }
                } else {
                    break;
                }
            }

            reader.close();
            writer.close();

            if (virusFound) {
                String errorMessage = mail.getErrorMessage();
                if (errorMessage == null) {
                    errorMessage = "";
                } else {
                    errorMessage += "\r\n";
                }
                StringBuilder sb = new StringBuilder(errorMessage);
                sb.append(logMessage).append("\r\n");

                // write mail and message info to log
                logMailInfo(mail);
                logMessageInfo(mimeMessage);

                // mark the mail with a mail attribute to check later on by other matchers/mailets
                mail.setAttribute(MAIL_ATTRIBUTE_NAME, "true");

                // sets the error message to be shown in any "notifyXxx" message
                mail.setErrorMessage(sb.toString());

                // mark the message with a header string
                mimeMessage.setHeader(HEADER_NAME, "true");

            } else {
                if (isDebug()) {
                    log("OK (by CLAMD on " + socket.getInetAddress() + ")");
                }
                mail.setAttribute(MAIL_ATTRIBUTE_NAME, "false");

                // mark the message with a header string
                mimeMessage.setHeader(HEADER_NAME, "false");

            }

            try {
                saveChanges(mimeMessage);
            } catch (Exception ex) {
                log("Exception caught while saving changes (header) to the MimeMessage. Ignoring ...", ex);
            }

        } catch (Exception ex) {
            log("Exception caught calling CLAMD on " + socket.getInetAddress() + ": " + ex.getMessage(), ex);
            throw new MessagingException("Exception caught", ex);
        } finally {
            shutdownReader(reader);
            shutdownWriter(writer);
            shutdownStream(bos);
            shutdownSocket(streamSocket);
            shutdownSocket(socket);
        }

    }

    /**
     * Unconditionally close an <code>OutputStream</code>.
     * Equivalent to {@link OutputStream#close()}, except any exceptions will be ignored.
     *
     * @param output A (possibly null) OutputStream
     */
    private static void shutdownStream(final OutputStream output) {
        if (null == output) {
            return;
        }

        try {
            output.close();
        } catch (final IOException ignored) {
        }
    }

    /**
     * Unconditionally close an <code>Socket</code>.
     * Equivalent to {@link Socket#close()}, except any exceptions will be ignored.
     *
     * @param socket A (possibly null) Socket
     */
    private static void shutdownSocket(final Socket socket) {
        if (null == socket) {
            return;
        }

        try {
            socket.close();
        } catch (final IOException ioe) {
        }
    }

    /**
     * Unconditionally close an <code>Writer</code>.
     * Equivalent to {@link Writer#close()}, except any exceptions will be ignored.
     *
     * @param output A (possibly null) Writer
     */
    private void shutdownWriter(final Writer output) {
        if (null == output) {
            return;
        }

        try {
            output.close();
        } catch (final IOException ioe) {
        }
    }


    /**
     * Unconditionally close an <code>Reader</code>.
     * Equivalent to {@link Reader#close()}, except any exceptions will be ignored.
     *
     * @param input A (possibly null) Reader
     */
    private void shutdownReader(final Reader input) {
        if (null == input) {
            return;
        }

        try {
            input.close();
        } catch (final IOException ioe) {
        }
    }

    /**
     * Tries to "ping" all the CLAMD daemons to
     * check if they are up and accepting requests.
     */

    protected void ping() throws Exception {

        for (int i = 0; i < getAddressesCount(); i++) {
            ping(getAddresses(i));
        }
    }

    /**
     * Tries (and retries as specified up to 'getMaxPings()') to "ping" the specified CLAMD daemon to
     * check if it is up and accepting requests.
     *
     * @param address the address to "ping"
     */
    protected void ping(InetAddress address) throws Exception {
        Socket socket = null;

        int ping = 1;
        for (; ; ) {
            if (isDebug()) {
                log("Trial #" + ping + "/" + getMaxPings() + " - creating socket connected to " + address + " on port " + getPort());
            }
            try {
                socket = new Socket(address, getPort());
                break;
            } catch (ConnectException ce) {
                log("Trial #" + ping + "/" + getMaxPings() + " - exception caught: " + ce.toString() + " while creating socket connected to " + address + " on port " + getPort());
                ping++;
                if (ping <= getMaxPings()) {
                    log("Waiting " + getPingIntervalMilli() + " milliseconds before retrying ...");
                    Thread.sleep(getPingIntervalMilli());
                } else {
                    break;
                }
            }
        }

        // if 'socket' is still null then 'maxPings' has been exceeded
        if (socket == null) {
            throw new ConnectException("maxPings exceeded: " + getMaxPings() + ". Giving up. The clamd daemon seems not to be running");
        }

        try {
            // get the reader and writer to ping and receive pong
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "ASCII"));
            PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

            log("Sending: \"PING\" to " + address + " ...");
            writer.println("PING");
            writer.flush();

            boolean pongReceived = false;
            for (; ; ) {
                String answer = reader.readLine();
                if (answer != null) {
                    answer = answer.trim();
                    log("Received: \"" + answer + "\"");
                    answer = answer.trim();
                    if (answer.equals("PONG")) {
                        pongReceived = true;
                    }

                } else {
                    break;
                }
            }

            reader.close();
            writer.close();

            if (!pongReceived) {
                throw new ConnectException("Bad answer from \"PING\" probe: expecting \"PONG\"");
            }
        } finally {
            socket.close();
        }
    }

    /**
     * Parses the answer from a STREAM request and gets the port number.
     *
     * @param answer the answer from CLAMD containing the port number
     * @return the port number for streaming out the data to scan
     */
    protected final int getStreamPortFromAnswer(String answer) throws ConnectException {
        int port = -1;
        if (answer != null && answer.startsWith(STREAM_PORT_STRING)) {
            try {
                port = Integer.parseInt(answer.substring(STREAM_PORT_STRING.length()));
            } catch (NumberFormatException nfe) {

            }
        }

        if (port <= 0) {
            throw new ConnectException("\"PORT nn\" expected - unable to parse: " + "\"" + answer + "\"");
        }

        return port;
    }

    /**
     * Saves changes resetting the original message id.
     *
     * @param message the message to save
     */
    protected final void saveChanges(MimeMessage message) throws MessagingException {
        String messageId = message.getMessageID();
        message.saveChanges();
        if (messageId != null) {
            message.setHeader(RFC2822Headers.MESSAGE_ID, messageId);
        }
    }

    private void logMailInfo(Mail mail) {

        // writes the error message to the log
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);

        out.print("Mail details:");
        out.print(" MAIL FROM: " + mail.getSender());
        Iterator<MailAddress> rcptTo = mail.getRecipients().iterator();
        out.print(", RCPT TO: " + rcptTo.next());
        while (rcptTo.hasNext()) {
            out.print(", " + rcptTo.next());
        }

        log(sout.toString());
    }

    private void logMessageInfo(MimeMessage mimeMessage) {

        // writes the error message to the log
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);

        out.println("MimeMessage details:");

        try {
            if (mimeMessage.getSubject() != null) {
                out.println("  Subject: " + mimeMessage.getSubject());
            }
            if (mimeMessage.getSentDate() != null) {
                out.println("  Sent date: " + mimeMessage.getSentDate());
            }
            String[] sender;
            sender = mimeMessage.getHeader(RFC2822Headers.FROM);
            if (sender != null) {
                out.print("  From: ");
                for (String aSender : sender) {
                    out.print(aSender + " ");
                }
                out.println();
            }
            String[] rcpts;
            rcpts = mimeMessage.getHeader(RFC2822Headers.TO);
            if (rcpts != null) {
                out.print("  To: ");
                for (String rcpt : rcpts) {
                    out.print(rcpt + " ");
                }
                out.println();
            }
            rcpts = mimeMessage.getHeader(RFC2822Headers.CC);
            if (rcpts != null) {
                out.print("  CC: ");
                for (String rcpt : rcpts) {
                    out.print(rcpt + " ");
                }
                out.println();
            }
            out.print("  Size (in bytes): " + mimeMessage.getSize());
            if (mimeMessage.getLineCount() >= 0) {
                out.print(", Number of lines: " + mimeMessage.getLineCount());
            }
        } catch (MessagingException me) {
            log("Exception caught reporting message details", me);
        }

        log(sout.toString());
    }

}

