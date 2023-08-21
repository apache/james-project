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


package org.apache.james.clamav;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;


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
 * <LI>sends a "<CODE>INSTREAM</CODE>" request;</LI>
 * <LI>sends the mime message to CLAMD;</LI>
 * <LI>gets the "<CODE>OK</CODE>" or "<CODE>... FOUND</CODE>" answer from the connection;</LI>
 * <LI>closes the connection;</LI>
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
 * <P>Here follows an example of mailetcontainer.xml which handles the infected messages bases on a CLAMD deployed on localhost with port 3316:</P>
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
    private static final Logger LOGGER = LoggerFactory.getLogger(ClamAVScan.class);

    private static final int DEFAULT_PORT = 3310;

    private static final int DEFAULT_MAX_PINGS = 2;

    private static final int DEFAULT_PING_INTERVAL_MILLI = 10000;

    private static final int DEFAULT_STREAM_BUFFER_SIZE = 8192;

    private static final String FOUND_STRING = "FOUND";

    protected static final AttributeName INFECTED_MAIL_ATTRIBUTE_NAME = AttributeName.of("org.apache.james.infected");

    protected static final String INFECTED_HEADER_NAME = "X-MessageIsInfected";

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

    @Override
    public String getMailetInfo() {
        return "Antivirus Check using ClamAV (CLAMD)";
    }

    /**
     * Gets the expected init parameters.
     */
    protected Set<String> getAllowedInitParameters() {
        return ImmutableSet.of(
                //            "static",
                "debug",
                "host",
                "port",
                "maxPings",
                "pingIntervalMilli",
                "streamBufferSize"
        );
    }

    /**
     * Initializer for property debug.
     */
    protected void initDebug() {
        String debugParam = getInitParameter("debug");
        this.debug = Boolean.parseBoolean(debugParam);
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
     * Initializer for property host.
     *
     * @throws UnknownHostException if unable to resolve the host name, or if invalid
     */
    protected void initHost() throws UnknownHostException {
        setHost(getInitParameter("host"));
        if (isDebug()) {
            LOGGER.debug("host: {}", getHost());
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
    private void setHost(String host) throws UnknownHostException {
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
            LOGGER.debug("port: {}", getPort());
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
            LOGGER.debug("maxPings: {}", getMaxPings());
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
            LOGGER.debug("pingIntervalMilli: {}", getPingIntervalMilli());
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
            LOGGER.debug("streamBufferSize: {}", getStreamBufferSize());
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
     * @throws ConnectException if no CLAMD in the round-robin address list has accepted the connection
     */
    protected Socket getClamdSocket() throws ConnectException {

        InetAddress address;

        Set<InetAddress> usedAddresses = new HashSet<>(getAddressesCount());
        for (; ; ) {
            // this do-while loop is needed because other threads could in the meantime
            // calling getNextAddress(), and because of that the current thread may skip
            // some working address
            do {
                if (usedAddresses.size() >= getAddressesCount()) {
                    String logText = "Unable to connect to CLAMD. All addresses failed.";
                    LOGGER.debug("{} Giving up.", logText);
                    throw new ConnectException(logText);
                }
                address = getNextAddress();
            } while (!usedAddresses.add(address));
            try {
                // get the socket
                return new Socket(address, getPort());
            } catch (IOException ioe) {
                LOGGER.error("Exception caught acquiring main socket to CLAMD on {} on port {}: {}", address, getPort(), ioe.getMessage());
                getNextAddress();
                // retry
            }
        }
    }

    @Override
    public void init() throws MessagingException {

        // check that all init parameters have been declared in allowedInitParameters
        checkInitParameters(getAllowedInitParameters());

        try {
            initDebug();
            if (isDebug()) {
                LOGGER.debug("Initializing");
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

        } catch (ConnectException ce) {
            LOGGER.error("ConnectException caught {}", ce.getMessage());
        } catch (Exception e) {
            LOGGER.error("Exception thrown", e);
        }
    }

    /**
     * Scans the mail.
     *
     * @param mail the mail to scan
     * @throws MessagingException if a problem arises
     */
    @Override
    public void service(Mail mail) throws MessagingException {

        // if already checked no action
        if (mail.getAttribute(INFECTED_MAIL_ATTRIBUTE_NAME).isPresent()) {
            return;
        }

        MimeMessage mimeMessage = mail.getMessage();

        if (mimeMessage == null) {
            LOGGER.debug("Null MimeMessage. Will send to ghost");
            mail.setState(Mail.GHOST);
            return;
        }

        try {
            if (hasVirus(new MimeMessageInputStream(mimeMessage))) {
                LOGGER.info("Detected mail {} containing virus, adding infected header/attribute.", mail);

                // mark the mail with a mail attribute to check later on by other matchers/mailets
                mail.setAttribute(makeInfectedAttribute(true));

                // mark the message with a header string
                mimeMessage.setHeader(INFECTED_HEADER_NAME, "true");
            } else {
                mail.setAttribute(makeInfectedAttribute(false));
                // mark the message with a header string
                mimeMessage.setHeader(INFECTED_HEADER_NAME, "false");
            }
            saveChanges(mimeMessage);
        } catch (IOException ex) {
            LOGGER.error("Exception caught calling CLAMD: {}", ex.getMessage());
        }
    }

    private Attribute makeInfectedAttribute(boolean value) {
        return new Attribute(INFECTED_MAIL_ATTRIBUTE_NAME, AttributeValue.of(value));
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
                LOGGER.debug("Trial #{}/{} - creating socket connected to {} on port {}", ping, getMaxPings(), address, getPort());
            }
            try {
                socket = new Socket(address, getPort());
                break;
            } catch (ConnectException ce) {
                LOGGER.debug("Trial #{}/{} - exception caught while creating socket connected to {} on port {}", ping, getMaxPings(), address, getPort(), ce);
                ping++;
                if (ping <= getMaxPings()) {
                    LOGGER.debug("Waiting {} milliseconds before retrying ...", getPingIntervalMilli());
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
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

            LOGGER.debug("Sending: \"PING\" to {} ...", address);
            writer.println("PING");
            writer.flush();

            boolean pongReceived = false;
            for (; ; ) {
                String answer = reader.readLine();
                if (answer != null) {
                    answer = answer.trim();
                    LOGGER.debug("Received: \"{}\"", answer);
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

    public boolean hasVirus(InputStream mimeMessage) throws IOException {
        try (Socket socket = getClamdSocket();
        OutputStream clamAVOutputStream = new BufferedOutputStream(socket.getOutputStream(), getStreamBufferSize());
        InputStream clamAvInputStream = socket.getInputStream()) {
            socket.setSoTimeout(2000);
            clamAVOutputStream.write("zINSTREAM\0".getBytes());
            clamAVOutputStream.flush();

            byte[] buffer = new byte[getStreamBufferSize()];
            int read = mimeMessage.read(buffer);
            while (read >= 0) {
                byte[] chunkSize = ByteBuffer.allocate(4).putInt(read).array();
                clamAVOutputStream.write(chunkSize);
                clamAVOutputStream.write(buffer, 0, read);
                if (clamAvInputStream.available() > 0) {
                    byte[] reply = clamAvInputStream.readAllBytes();
                    throw new IOException("Reply from server: " + new String(reply));
                }
                read = mimeMessage.read(buffer);
            }
            clamAVOutputStream.write(new byte[]{0, 0, 0, 0});
            clamAVOutputStream.flush();
            return replyContainsFound(new String(clamAvInputStream.readAllBytes()));
        }
    }

    private boolean replyContainsFound(String reply) {
        return reply.contains(FOUND_STRING);
    }
}

