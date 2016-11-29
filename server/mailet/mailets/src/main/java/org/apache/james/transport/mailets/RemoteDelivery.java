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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;
import javax.mail.internet.ParseException;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.james.dnsservice.library.MXHostAddressIterator;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.transport.mailets.remoteDelivery.HeloNameProvider;
import org.apache.james.transport.mailets.remoteDelivery.RemoteDeliverySocketFactory;
import org.apache.james.transport.util.Patterns;
import org.apache.james.util.TimeConverter;
import org.apache.mailet.HostAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;

import com.sun.mail.smtp.SMTPTransport;

/**
 * <p>The RemoteDelivery mailet delivers messages to a remote SMTP server able to deliver or forward messages to their final
 * destination.
 * <p/>
 * <p>The remote SMTP server through which each mail is delivered is resolved using MX lookup for each message destination
 * unless the <code>&lt;gateway/&gt</code> parameter is set. The <code>&lt;gateway/&gt</code> parameter enables the
 * definition of one or more gateway servers through which all messages are sent.
 * <p/>
 * <p>If an attempt to deliver a message fails, a redelivery attempt is scheduled according to the scheme defined
 * by the <code>&lt;delayTime/&gt</code> parameter, retrying up to the limit defined
 * by the <code>&lt;maxRetries/&gt</code> parameter. When the retry limit is exceeded, delivery failure is processed
 * according to the setting of the <code>&lt;bounceProcessor/&gt</code> parameter.
 * <p/>
 * <p>These are the parameters that control the operation of the RemoteDelivery mailet:
 * <p/>
 * <ul>
 * <li><b>deliveryThreads</b> (required) - an Integer for the number of threads this mailet will use to deliver mail.</li>
 * <li><b>outgoing</b> (required) - a String containing the name of the queue that will hold messages being processed by this mailet.</li>
 * <li><b>bind</b> (optional) - a String describing the local IP address to which the mailet should be bound while delivering
 * emails. This tag is useful for multihomed machines. Default is to bind to the default local address of the machine.<br>
 * Note: The same IP address must be used for all of those RemoteDelivery instances where you explicitly supply a bind address.
 * <li><b>delayTime</b> (optional> a String containing a comma separated list of patterns defining the number of and delays between delivery
 * attempts. The pattern is <code>[attempts\*]delay [unit]</code> where:
 * <ul>
 * <li><i>attempts</i> (optional) - an Integer for the number of delivery attempts. Default is 1.</li>
 * <li><i>delay</i> (required) - a Long for the delay between attempts.</li>
 * <li><i>unit</i> (optional) - a String with the value of one of 'msec', 'sec', 'minute', 'hour', or 'day'. Default is msec.</li>
 * </ul>
 * Default is one attempt after 6 hours, which if explicitly declared would be written as <code>&lt;delayTime&gt;1 * 6 hour&lt;/delayTime&gt;</code></li>
 * <li><b>maxRetries</b> (optional) an Integer for the number of times an attempt is made to deliver a particular mail.
 * Default is the greater of five and the sum of the attempts for each <code>&lt;delayTime/&gt;</code> specified.
 * <li><b>maxDnsProblemRetries</b> (optional) - an Integer for the number of times to retry if DNS problems for a domain occur.
 * Default is 0.
 * <li><b>timeout</b> (optional) - an Integer for the Socket I/O timeout in milliseconds. Default is 180000</li>
 * <li><b>connectionTimeout</b> (optional) - an Integer for the Socket connection timeout in milliseconds. Default is 60000</li>
 * <li><b>bounceProcessor</b> (optional) - a String containing the name of the mailet processor to pass messages that cannot
 * be delivered to for DSN bounce processing. Default is to send a traditional message containing the bounce details.</li>
 * <li><b>startTLS</b> (optional) - a Boolean (true/false) indicating whether the STARTTLS command (if supported by the server)
 * to switch the connection to a TLS-protected connection before issuing any login commands. Default is false.</li>
 * <li><b>sslEnable</b> (optional) - a Boolean (true/false) indicating whether to use SSL to connect and use the SSL port unless
 * explicitly overridden. Default is false.</li>
 * <li><b>gateway</b> (optional) - a String containing a comma separated list of patterns defining the gateway servers to be used to
 * deliver mail regardless of the recipient address. If multiple gateway servers are defined, each will be tried in definition order
 * until delivery is successful. If none are successful, the mail is bounced. The pattern is <code>host[:port]</code> where:
 * <ul>
 * <li><i>host</i> (required) - the FQN of the gateway server.</li>
 * <li><i>port</i> (optional) - the port of the gateway server. Default is the value defined in the <code>&lt;gatewayPort/&gt;</code>
 * parameter if set, else the default port for the specified connection type.</li>
 * </ul>
 * Default is to resolve the destination SMTP server for each mail using MX lookup.
 * </li>
 * <li><b>gatewayPort</b> (optional) - an Integer for the gateway port to be used for each defined gateway server for which a
 * port is not explicitly defined in the <code>&lt;gateway/&gt;</code> parameter. Default is the default port for the specified connection type.</li>
 * <li><b>gatewayUsername</b> (optional) - a String containing the user name to be used to authenticate the user using the
 * AUTH command. Default is not to issue the AUTH command.
 * <li><b>gatewayPassword</b> (required if <code>gatewayUsername</code>) is set - a String representing the password to be used
 * to authenticate the user using the AUTH command.
 * <li><b>heloName</b> (optional) - a String containing the name used in the SMTP HELO and EHLO commands. Default is the default domain,
 * which is typically <code>localhost</code>.</li>
 * <li><b>mail.*</b> (optional) - Any property beginning with <code>mail.</code> described in the Javadoc for package
 * <a href="http://java.sun.com/products/javamail/javadocs/com/sun/mail/smtp/package-summary.html"><code>com.sun.mail.smtp</code></a>
 * can be set with a parameter of the corresponding name. For example the parameter
 * <code>&lt;mail.smtp.ssl.enable&gt;true&lt;/mail.smtp.ssl.enable&gt;</code> is equivalent to the Java code
 * <code>props.put("mail.smtp.ssl.enable", "true");</code>. Properties set by this facility override settings made
 * within the mailet code.<br>
 * Note: This facility should be used with extreme care by expert users with a thorough knowledge of the relevant RFCs and
 * the ability to perform their own problem resolutions.</li>
 * <li><b>debug</b> (optional) - a Boolean (true/false) indicating whether debugging is on. Default is false.</li>
 * </ul>
 */
@SuppressWarnings("deprecation")
public class RemoteDelivery extends GenericMailet implements Runnable {

    /**
     * Default Delay Time (Default is 6*60*60*1000 Milliseconds (6 hours)).
     */
    private static final long DEFAULT_DELAY_TIME = 21600000;

    /**
     * Pattern to match [attempts*]delay[units].
     */
    private static final String PATTERN_STRING = "\\s*([0-9]*\\s*[\\*])?\\s*([0-9]+)\\s*([a-z,A-Z]*)\\s*";

    private static final Pattern PATTERN = Patterns.compilePatternUncheckedException(PATTERN_STRING);
    private static final String OUTGOING_MAILS = "outgoingMails";

    private final DNSService dnsServer;
    private final DomainList domainList;
    private final MailQueueFactory queueFactory;
    private final Metric outgoingMailsMetric;
    private final Properties defprops; // Default properties for the JavaMail Session
    private final Collection<Thread> workersThreads;

    /**
     * Flag to define verbose logging messages.
     */
    private boolean isDebug = false;

    /**
     * List of Delay Times. Controls frequency of retry attempts.
     */
    private long[] delayTimes;

    /**
     * Maximum no. of retries (Defaults to 5).
     */
    private int maxRetries = 5;

    /**
     * Default number of ms to timeout on smtp delivery
     */
    private long smtpTimeout = 180000;

    /**
     * If false then ANY address errors will cause the transmission to fail
     */
    private boolean sendPartial = false;

    /**
     * The amount of time JavaMail will wait before giving up on a socket
     * connect()
     */
    private int connectionTimeout = 60000;

    /**
     * No. of threads used to process messages that should be retried.
     */
    private int workersThreadCount = 1;

    /**
     * The server(s) to send all email to
     */
    private Collection<String> gatewayServer = null;

    /**
     * Auth for gateway server
     */
    private String authUser = null;

    /**
     * Password for gateway server
     */
    private String authPass = null;

    /**
     * True, if the bind configuration parameter is supplied,
     * RemoteDeliverySocketFactory will be used in this case.
     */
    private boolean isBindUsed = false;

    /**
     * Flag used by 'run' method to end itself.
     */
    private volatile boolean destroyed = false;

    /**
     * the processor for creating Bounces
     */
    private String bounceProcessor = null;


    /**
     * The retry count dnsProblemErrors
     */
    private int dnsProblemRetry = 0;

    private MailQueue queue;

    private Logger logger;

    private boolean usePriority;

    private boolean startTLS = false;

    private boolean isSSLEnable = false;

    private HeloNameProvider heloNameProvider;

    @Inject
    public RemoteDelivery(DNSService dnsServer, DomainList domainList, MailQueueFactory queueFactory, MetricFactory metricFactory) {
        this.dnsServer = dnsServer;
        this.domainList = domainList;
        this.queueFactory = queueFactory;
        this.outgoingMailsMetric = metricFactory.generate(OUTGOING_MAILS);
        this.defprops = new Properties();
        this.workersThreads = new Vector<Thread>();
    }

    /**
     * Initializes all arguments based on configuration values specified in the
     * James configuration file.
     *
     * @throws MessagingException on failure to initialize attributes.
     */
    public void init() throws MessagingException {
        // Set isDebug flag.
        isDebug = (getInitParameter("debug") == null) ? false : Boolean.valueOf(getInitParameter("debug"));

        logger = getMailetContext().getLogger();

        // Create list of Delay Times.
        ArrayList<Delay> delayTimesList = new ArrayList<Delay>();
        try {
            if (getInitParameter("delayTime") != null) {

                // parses delayTimes specified in config file.
                String delayTimesParm = getInitParameter("delayTime");

                // Split on commas
                StringTokenizer st = new StringTokenizer(delayTimesParm, ",");
                while (st.hasMoreTokens()) {
                    String delayTime = st.nextToken();
                    delayTimesList.add(new Delay(delayTime));
                }
            } else {
                // Use default delayTime.
                delayTimesList.add(new Delay());
            }
        } catch (Exception e) {
            log("Invalid delayTime setting: " + getInitParameter("delayTime"));
        }

        try {
            // Get No. of Max Retries.
            if (getInitParameter("maxRetries") != null) {
                maxRetries = Integer.parseInt(getInitParameter("maxRetries"));
            }

            // Check consistency of 'maxRetries' with delayTimesList attempts.
            int totalAttempts = calcTotalAttempts(delayTimesList);

            // If inconsistency found, fix it.
            if (totalAttempts > maxRetries) {
                log("Total number of delayTime attempts exceeds maxRetries specified. " + " Increasing maxRetries from " + maxRetries + " to " + totalAttempts);
                maxRetries = totalAttempts;
            } else {
                int extra = maxRetries - totalAttempts;
                if (extra != 0) {
                    log("maxRetries is larger than total number of attempts specified.  " + "Increasing last delayTime with " + extra + " attempts ");

                    // Add extra attempts to the last delayTime.
                    if (delayTimesList.size() != 0) {
                        // Get the last delayTime.
                        Delay delay = delayTimesList.get(delayTimesList.size() - 1);

                        // Increase no. of attempts.
                        delay.setAttempts(delay.getAttempts() + extra);
                        log("Delay of " + delay.getDelayTime() + " msecs is now attempted: " + delay.getAttempts() + " times");
                    } else {
                        throw new MessagingException("No delaytimes, cannot continue");
                    }
                }
            }
            delayTimes = expandDelays(delayTimesList);

        } catch (Exception e) {
            log("Invalid maxRetries setting: " + getInitParameter("maxRetries"));
        }
        // Get the path for the 'Outgoing' repository. This is the place on the
        // file system where Mail objects will be saved during the 'delivery'
        // processing. This can be changed to a repository on a database (e.g.
        // db://maildb/spool/retry).
        String outgoing = getInitParameter("outgoing");
        if (outgoing == null) {
            outgoing = "outgoing";
        }
        queue = queueFactory.getQueue(outgoing);

        try {
            if (getInitParameter("timeout") != null) {
                smtpTimeout = Integer.parseInt(getInitParameter("timeout"));
            }
        } catch (Exception e) {
            log("Invalid timeout setting: " + getInitParameter("timeout"));
        }

        try {
            if (getInitParameter("connectiontimeout") != null) {
                connectionTimeout = Integer.parseInt(getInitParameter("connectiontimeout"));
            }
        } catch (Exception e) {
            log("Invalid timeout setting: " + getInitParameter("timeout"));
        }

        sendPartial = (getInitParameter("sendpartial") == null) ? false : Boolean.valueOf(getInitParameter("sendpartial"));

        bounceProcessor = getInitParameter("bounceProcessor");

        String sTLS = getInitParameter("startTLS");
        if (sTLS != null) {
            startTLS = Boolean.valueOf(sTLS);
        }

        isSSLEnable = (getInitParameter("sslEnable") == null) ? false : Boolean.valueOf(getInitParameter("sslEnable"));

        String gateway = getInitParameter("gateway");
        String gatewayPort = getInitParameter("gatewayPort");

        if (gateway != null) {
            gatewayServer = new ArrayList<String>();
            StringTokenizer st = new StringTokenizer(gateway, ",");
            while (st.hasMoreTokens()) {
                String server = st.nextToken().trim();
                if (server.indexOf(':') < 0 && gatewayPort != null) {
                    server += ":";
                    server += gatewayPort;
                }

                if (isDebug)
                    log("Adding SMTP gateway: " + server);
                gatewayServer.add(server);
            }
            authUser = getInitParameter("gatewayUsername");
            // backward compatibility with 2.3.x
            if (authUser == null) {
                authUser = getInitParameter("gatewayusername");
            }
            authPass = getInitParameter("gatewayPassword");
        }

        /*
      JavaMail delivery socket binds to this local address. If null the
      JavaMail default will be used.
     */
        String bindAddress = getInitParameter("bind");
        isBindUsed = bindAddress != null;
        try {
            if (isBindUsed)
                RemoteDeliverySocketFactory.setBindAdress(bindAddress);
        } catch (UnknownHostException e) {
            log("Invalid bind setting (" + bindAddress + "): " + e.toString());
        }


        // deal with <mail.*> attributes, passing them to javamail
        Iterator<String> i = getInitParameterNames();
        while (i.hasNext()) {
            String name = i.next();
            if (name.startsWith("mail.")) {
                defprops.put(name, getInitParameter(name));
            }

        }

        String dnsRetry = getInitParameter("maxDnsProblemRetries");
        if (dnsRetry != null && !dnsRetry.equals("")) {
            dnsProblemRetry = Integer.parseInt(dnsRetry);
        }

        heloNameProvider = new HeloNameProvider(getInitParameter("heloName"), domainList);

        String prio = getInitParameter("usePriority");
        if (prio != null) {
            usePriority = Boolean.valueOf(prio);
        }

        // Start Workers Threads.
        workersThreadCount = Integer.parseInt(getInitParameter("deliveryThreads"));
        initDeliveryThreads();
    }

    private void initDeliveryThreads() {
        for (int a = 0; a < workersThreadCount; a++) {
            String threadName = "Remote delivery thread (" + a + ")";
            Thread t = new Thread(this, threadName);
            t.start();
            workersThreads.add(t);
        }
    }

    /**
     * Calculates Total no. of attempts for the specified delayList.
     *
     * @param delayList list of 'Delay' objects
     * @return total no. of retry attempts
     */
    private int calcTotalAttempts(ArrayList<Delay> delayList) {
        int sum = 0;
        for (Delay delay : delayList) {
            sum += delay.getAttempts();
        }
        return sum;
    }

    /**
     * <p>
     * This method expands an ArrayList containing Delay objects into an array
     * holding the only delaytime in the order.
     * </p>
     * <p/>
     * So if the list has 2 Delay objects the first having attempts=2 and
     * delaytime 4000 the second having attempts=1 and delaytime=300000 will be
     * expanded into this array:
     * <p/>
     * <pre>
     * long[0] = 4000
     * long[1] = 4000
     * long[2] = 300000
     * </pre>
     *
     * @param list the list to expand
     * @return the expanded list
     */
    private long[] expandDelays(ArrayList<Delay> list) {
        long[] delays = new long[calcTotalAttempts(list)];
        Iterator<Delay> i = list.iterator();
        int idx = 0;
        while (i.hasNext()) {
            Delay delay = i.next();
            for (int j = 0; j < delay.getAttempts(); j++) {
                delays[idx++] = delay.getDelayTime();
            }
        }
        return delays;
    }

    /**
     * This method returns, given a retry-count, the next delay time to use.
     *
     * @param retry_count the current retry_count.
     * @return the next delay time to use, given the retry count
     */
    private long getNextDelay(int retry_count) {
        if (retry_count > delayTimes.length) {
            return DEFAULT_DELAY_TIME;
        }
        return delayTimes[retry_count - 1];
    }

    /**
     * This class is used to hold a delay time and its corresponding number of
     * retries.
     */
    private final static class Delay {
        private int attempts = 1;

        private long delayTime = DEFAULT_DELAY_TIME;

        /**
         * <p>
         * This constructor expects Strings of the form
         * "[attempt\*]delaytime[unit]".
         * </p>
         * <p>
         * The optional attempt is the number of tries this delay should be used
         * (default = 1). The unit, if present, must be one of
         * (msec,sec,minute,hour,day). The default value of unit is 'msec'.
         * </p>
         * <p>
         * The constructor multiplies the delaytime by the relevant multiplier
         * for the unit, so the delayTime instance variable is always in msec.
         * </p>
         *
         * @param initString the string to initialize this Delay object from
         */
        public Delay(String initString) throws MessagingException {
            // Default unit value to 'msec'.
            String unit = "msec";

            Matcher res = PATTERN.matcher(initString);
            if (res.matches()) {
                // The capturing groups will now hold:
                // at 1: attempts * (if present)
                // at 2: delaytime
                // at 3: unit (if present)
                if (res.group(1) != null && !res.group(1).equals("")) {
                    // We have an attempt *
                    String attemptMatch = res.group(1);

                    // Strip the * and whitespace.
                    attemptMatch = attemptMatch.substring(0, attemptMatch.length() - 1).trim();
                    attempts = Integer.parseInt(attemptMatch);
                }

                delayTime = Long.parseLong(res.group(2));

                if (!res.group(3).equals("")) {
                    // We have a value for 'unit'.
                    unit = res.group(3).toLowerCase(Locale.US);
                }
            } else {
                throw new MessagingException(initString + " does not match " + PATTERN_STRING);
            }

            // calculate delayTime.
            try {
                delayTime = TimeConverter.getMilliSeconds(delayTime, unit);
            } catch (NumberFormatException e) {
                throw new MessagingException(e.getMessage());
            }
        }

        /**
         * This constructor makes a default Delay object with attempts = 1 and
         * delayTime = DEFAULT_DELAY_TIME.
         */
        public Delay() {
        }

        /**
         * @return the delayTime for this Delay
         */
        public long getDelayTime() {
            return delayTime;
        }

        /**
         * @return the number attempts this Delay should be used.
         */
        public int getAttempts() {
            return attempts;
        }

        /**
         * Set the number attempts this Delay should be used.
         */
        public void setAttempts(int value) {
            attempts = value;
        }

        /**
         * Pretty prints this Delay
         */
        @Override
        public String toString() {
            return getAttempts() + "*" + getDelayTime() + "msecs";
        }
    }

    @Override
    public String getMailetInfo() {
        return "RemoteDelivery Mailet";
    }

    /**
     * For this message, we take the list of recipients, organize these into
     * distinct servers, and duplicate the message for each of these servers,
     * and then call the deliver (messagecontainer) method for each
     * server-specific messagecontainer ... that will handle storing it in the
     * outgoing queue if needed.
     *
     * @param mail org.apache.mailet.Mail
     */
    @Override
    public void service(Mail mail) throws MessagingException {
        // Do I want to give the internal key, or the message's Message ID
        if (isDebug) {
            log("Remotely delivering mail " + mail.getName());
        }
        Collection<MailAddress> recipients = mail.getRecipients();

        if (usePriority) {

            // Use highest prio for new emails. See JAMES-1311
            mail.setAttribute(MailPrioritySupport.MAIL_PRIORITY, MailPrioritySupport.HIGH_PRIORITY);
        }
        if (gatewayServer == null) {
            // Must first organize the recipients into distinct servers (name
            // made case insensitive)
            Hashtable<String, Collection<MailAddress>> targets = new Hashtable<String, Collection<MailAddress>>();
            for (MailAddress target : recipients) {
                String targetServer = target.getDomain().toLowerCase(Locale.US);
                Collection<MailAddress> temp = targets.get(targetServer);
                if (temp == null) {
                    temp = new ArrayList<MailAddress>();
                    targets.put(targetServer, temp);
                }
                temp.add(target);
            }

            // We have the recipients organized into distinct servers... put
            // them into the
            // delivery store organized like this... this is ultra inefficient I
            // think...

            // Store the new message containers, organized by server, in the
            // outgoing mail repository
            String name = mail.getName();
            for (Map.Entry<String, Collection<MailAddress>> entry : targets.entrySet()) {
                if (isDebug) {
                    String logMessageBuffer = "Sending mail to " + entry.getValue() + " on host " + entry.getKey();
                    log(logMessageBuffer);
                }
                mail.setRecipients(entry.getValue());
                String nameBuffer = name + "-to-" + entry.getKey();
                mail.setName(nameBuffer);
                try {
                    queue.enQueue(mail);
                } catch (MailQueueException e) {
                    log("Unable to queue mail " + mail.getName() + " for recipients + " + mail.getRecipients().toString(), e);
                }
            }
        } else {
            // Store the mail unaltered for processing by the gateway server(s)
            if (isDebug) {
                String logMessageBuffer = "Sending mail to " + mail.getRecipients() + " via " + gatewayServer;
                log(logMessageBuffer);
            }

            // Set it to try to deliver (in a separate thread) immediately
            // (triggered by storage)
            try {
                queue.enQueue(mail);
            } catch (MailQueueException e) {
                log("Unable to queue mail " + mail.getName() + " for recipients + " + mail.getRecipients().toString(), e);
            }
        }
        mail.setState(Mail.GHOST);
    }

    /**
     * Stops all the worker threads that are waiting for messages. This method
     * is called by the Mailet container before taking this Mailet out of
     * service.
     */
    @Override
    public synchronized void destroy() {
        // Mark flag so threads from this Mailet stop themselves
        destroyed = true;

        // Wake up all threads from waiting for an accept
        for (Thread t : workersThreads) {
            t.interrupt();
        }
        notifyAll();
    }

    /**
     * Handles checking the outgoing spool for new mail and delivering them if
     * there are any
     */
    @Override
    public void run() {

        // Checks the pool and delivers a mail message
        Properties props = new Properties();
        // Not needed for production environment
        props.put("mail.debug", "false");
        // Reactivated: javamail 1.3.2 should no more have problems with
        // "250 OK"
        // messages (WAS "false": Prevents problems encountered with 250 OK
        // Messages)
        props.put("mail.smtp.ehlo", "true");
        // By setting this property to true the transport is allowed to
        // send 8 bit data to the server (if it supports the 8bitmime
        // extension).
        // 2006/03/01 reverted to false because of a javamail bug converting to
        // 8bit
        // messages created by an inputstream.
        props.setProperty("mail.smtp.allow8bitmime", "true");
        // Sets timeout on going connections
        props.put("mail.smtp.timeout", smtpTimeout + "");

        props.put("mail.smtp.connectiontimeout", connectionTimeout + "");
        props.put("mail.smtp.sendpartial", String.valueOf(sendPartial));

        props.put("mail.smtp.localhost", heloNameProvider.getHeloName());

        // handle starttls
        props.put("mail.smtp.starttls.enable", String.valueOf(startTLS));

        // handle SSLEnable
        props.put("mail.smtp.ssl.enable", String.valueOf(isSSLEnable));

        if (isBindUsed) {
            // undocumented JavaMail 1.2 feature, smtp transport will use
            // our socket factory, which will also set the local address
            props.put("mail.smtp.socketFactory.class", RemoteDeliverySocketFactory.class.getClass());
            // Don't fallback to the standard socket factory on error, do throw
            // an exception
            props.put("mail.smtp.socketFactory.fallback", "false");
        }

        if (authUser != null) {
            props.put("mail.smtp.auth", "true");
        }

        props.putAll(defprops);

        final Session session = obtainSession(props);
        try {
            while (!Thread.interrupted() && !destroyed) {
                try {
                    // Get the 'mail' object that is ready for deliverying. If
                    // no
                    // message is
                    // ready, the 'accept' will block until message is ready.
                    // The amount
                    // of time to block is determined by the 'getWaitTime'
                    // method of the
                    // MultipleDelayFilter.
                    MailQueueItem queueItem = queue.deQueue();
                    Mail mail = queueItem.getMail();

                    String key = mail.getName();

                    try {
                        if (isDebug) {
                            String message = Thread.currentThread().getName() + " will process mail " + key;
                            log(message);
                        }

                        // Deliver message
                        if (deliver(mail, session)) {
                            // Message was successfully delivered/fully
                            // failed...
                            // delete it
                            LifecycleUtil.dispose(mail);
                            // workRepository.remove(key);
                        } else {
                            // Something happened that will delay delivery.
                            // Store it back in the retry repository.
                            // workRepository.store(mail);
                            int retries = 0;
                            try {
                                retries = Integer.parseInt(mail.getErrorMessage());
                            } catch (NumberFormatException e) {
                                // Something strange was happen with the
                                // errorMessage..
                            }

                            long delay = getNextDelay(retries);

                            if (usePriority) {
                                // Use lowest priority for retries. See JAMES-1311
                                mail.setAttribute(MailPrioritySupport.MAIL_PRIORITY, MailPrioritySupport.LOW_PRIORITY);
                            }
                            queue.enQueue(mail, delay, TimeUnit.MILLISECONDS);
                            LifecycleUtil.dispose(mail);

                            // This is an update, so we have to unlock and
                            // notify or this mail is kept locked by this
                            // thread.
                            // workRepository.unlock(key);

                            // Note: We do not notify because we updated an
                            // already existing mail and we are now free to
                            // handle
                            // more mails.
                            // Furthermore this mail should not be processed now
                            // because we have a retry time scheduling.
                        }

                        // Clear the object handle to make sure it recycles
                        // this object.
                        mail = null;
                        queueItem.done(true);
                    } catch (Exception e) {
                        // Prevent unexpected exceptions from causing looping by
                        // removing message from outgoing.
                        // DO NOT CHANGE THIS to catch Error! For example, if
                        // there were an OutOfMemory condition caused because
                        // something else in the server was abusing memory, we
                        // would
                        // not want to start purging the retrying spool!
                        log("Exception caught in RemoteDelivery.run()", e);
                        LifecycleUtil.dispose(mail);
                        // workRepository.remove(key);
                        queueItem.done(false);
                        throw new MailQueueException("Unable to perform dequeue", e);
                    }

                } catch (Throwable e) {
                    if (!destroyed) {
                        log("Exception caught in RemoteDelivery.run()", e);
                    }
                }
            }
        } finally {
            // Restore the thread state to non-interrupted.
            Thread.interrupted();
        }
    }

    /**
     * We can assume that the recipients of this message are all going to the
     * same mail server. We will now rely on the DNS server to do DNS MX record
     * lookup and try to deliver to the multiple mail servers. If it fails, it
     * should throw an exception.
     *
     * @param mail    org.apache.james.core.MailImpl
     * @param session javax.mail.Session
     * @return boolean Whether the delivery was successful and the message can
     *         be deleted
     */
    private boolean deliver(Mail mail, Session session) {
        try {
            if (isDebug) {
                log("Attempting to deliver " + mail.getName());
            }
            MimeMessage message = mail.getMessage();

            // Create an array of the recipients as InternetAddress objects
            Collection<MailAddress> recipients = mail.getRecipients();
            InternetAddress addr[] = new InternetAddress[recipients.size()];
            int j = 0;
            for (Iterator<MailAddress> i = recipients.iterator(); i.hasNext(); j++) {
                MailAddress rcpt = i.next();
                addr[j] = rcpt.toInternetAddress();
            }

            if (addr.length <= 0) {
                log("No recipients specified... not sure how this could have happened.");
                return true;
            }

            // Figure out which servers to try to send to. This collection
            // will hold all the possible target servers
            Iterator<HostAddress> targetServers;
            if (gatewayServer == null) {
                MailAddress rcpt = recipients.iterator().next();
                String host = rcpt.getDomain();

                // Lookup the possible targets
                try {
                    targetServers = new MXHostAddressIterator(dnsServer.findMXRecords(host).iterator(), dnsServer, false, logger);
                } catch (TemporaryResolutionException e) {
                    log("Temporary problem looking up mail server for host: " + host);
                    String exceptionBuffer = "Temporary problem looking up mail server for host: " + host + ".  I cannot determine where to send this message.";

                    // temporary problems
                    return failMessage(mail, new MessagingException(exceptionBuffer), false);
                }
                if (!targetServers.hasNext()) {
                    log("No mail server found for: " + host);
                    String exceptionBuffer = "There are no DNS entries for the hostname " + host + ".  I cannot determine where to send this message.";

                    int retry = 0;
                    try {
                        retry = Integer.parseInt(mail.getErrorMessage());
                    } catch (NumberFormatException e) {
                        // Unable to parse retryCount
                    }
                    if (retry == 0 || retry > dnsProblemRetry) {
                        // The domain has no dns entry.. Return a permanent
                        // error
                        return failMessage(mail, new MessagingException(exceptionBuffer), true);
                    } else {
                        return failMessage(mail, new MessagingException(exceptionBuffer), false);
                    }
                }
            } else {
                targetServers = getGatewaySMTPHostAddresses(gatewayServer);
            }

            MessagingException lastError = null;

            while (targetServers.hasNext()) {
                try {

                    Properties props = session.getProperties();
                    if (mail.getSender() == null) {
                        props.put("mail.smtp.from", "<>");
                    } else {
                        String sender = mail.getSender().toString();
                        props.put("mail.smtp.from", sender);
                    }

                    HostAddress outgoingMailServer = targetServers.next();
                    StringBuilder logMessageBuffer = new StringBuilder(256).append("Attempting delivery of ").append(mail.getName()).append(" to host ").append(outgoingMailServer.getHostName()).append(" at ").append(outgoingMailServer.getHost()).append(" from ").append(props.get("mail.smtp.from"))
                            .append(" for addresses ").append(Arrays.asList(addr));
                    log(logMessageBuffer.toString());

                    // Many of these properties are only in later JavaMail
                    // versions
                    // "mail.smtp.ehlo" //default true
                    // "mail.smtp.auth" //default false
                    // "mail.smtp.dsn.ret" //default to nothing... appended as
                    // RET= after MAIL FROM line.
                    // "mail.smtp.dsn.notify" //default to nothing...appended as
                    // NOTIFY= after RCPT TO line.

                    SMTPTransport transport = null;
                    try {
                        transport =  (SMTPTransport) session.getTransport(outgoingMailServer);
                        transport.setLocalHost( props.getProperty("mail.smtp.localhost", heloNameProvider.getHeloName()) );
                        try {
                            if (authUser != null) {
                                transport.connect(outgoingMailServer.getHostName(), authUser, authPass);
                            } else {
                                transport.connect();
                            }
                        } catch (MessagingException me) {
                            // Any error on connect should cause the mailet to
                            // attempt
                            // to connect to the next SMTP server associated
                            // with this
                            // MX record. Just log the exception. We'll worry
                            // about
                            // failing the message at the end of the loop.

                            // Also include the stacktrace if debug is enabled. See JAMES-1257
                            if (isDebug) {
                                log(me.getMessage(), me.getCause());
                            } else {
                                log(me.getMessage());
                            }
                            continue;
                        }
                        // if the transport is a SMTPTransport (from sun) some
                        // performance enhancement can be done.
                        if (transport.getClass().getName().endsWith(".SMTPTransport")) {
                            boolean supports8bitmime = false;
                            try {
                                Method supportsExtension = transport.getClass().getMethod("supportsExtension", new Class[]{String.class});
                                supports8bitmime = (Boolean) supportsExtension.invoke(transport, "8BITMIME");
                            } catch (NoSuchMethodException nsme) {
                                // An SMTPAddressFailedException with no
                                // getAddress method.
                            } catch (IllegalAccessException iae) {
                            } catch (IllegalArgumentException iae) {
                            } catch (InvocationTargetException ite) {
                                // Other issues with getAddress invokation.
                            }

                            // if the message is alredy 8bit or binary and the
                            // server doesn't support the 8bit extension it has
                            // to be converted to 7bit. Javamail api doesn't
                            // perform
                            // that conversion, but it is required to be a
                            // rfc-compliant smtp server.

                            // Temporarily disabled. See JAMES-638
                            if (!supports8bitmime) {
                                try {
                                    convertTo7Bit(message);
                                } catch (IOException e) {
                                    // An error has occured during the 7bit
                                    // conversion.
                                    // The error is logged and the message is
                                    // sent anyway.

                                    log("Error during the conversion to 7 bit.", e);
                                }
                            }
                        } else {
                            // If the transport is not the one
                            // developed by Sun we are not sure of how it
                            // handles the 8 bit mime stuff,
                            // so I convert the message to 7bit.
                            try {
                                convertTo7Bit(message);
                            } catch (IOException e) {
                                log("Error during the conversion to 7 bit.", e);
                            }
                        }
                        transport.sendMessage(message, addr);
                    } finally {
                        if (transport != null) {
                            try {
                                // James-899: transport.close() sends QUIT to
                                // the server; if that fails
                                // (e.g. because the server has already closed
                                // the connection) the message
                                // should be considered to be delivered because
                                // the error happened outside
                                // of the mail transaction (MAIL, RCPT, DATA).
                                transport.close();
                            } catch (MessagingException e) {
                                log("Warning: could not close the SMTP transport after sending mail (" + mail.getName() + ") to " + outgoingMailServer.getHostName() + " at " + outgoingMailServer.getHost() + " for " + mail.getRecipients() + "; probably the server has already closed the "
                                        + "connection. Message is considered to be delivered. Exception: " + e.getMessage());
                            }
                            transport = null;
                        }
                    }
                    logMessageBuffer = new StringBuilder(256).append("Mail (").append(mail.getName()).append(") sent successfully to ").append(outgoingMailServer.getHostName()).append(" at ").append(outgoingMailServer.getHost()).append(" from ").append(props.get("mail.smtp.from")).append(" for ")
                            .append(mail.getRecipients());
                    log(logMessageBuffer.toString());
                    outgoingMailsMetric.increment();
                    return true;
                } catch (SendFailedException sfe) {
                    logSendFailedException(sfe);

                    if (sfe.getValidSentAddresses() != null) {
                        Address[] validSent = sfe.getValidSentAddresses();
                        if (validSent.length > 0) {
                            String logMessageBuffer = "Mail (" + mail.getName() + ") sent successfully for " + Arrays.asList(validSent);
                            log(logMessageBuffer);
                        }
                    }

                    /*
                     * SMTPSendFailedException introduced in JavaMail 1.3.2, and
                     * provides detailed protocol reply code for the operation
                     */
                    if (sfe.getClass().getName().endsWith(".SMTPSendFailedException")) {
                        try {
                            int returnCode = (Integer) invokeGetter(sfe, "getReturnCode");
                            // if 5xx, terminate this delivery attempt by
                            // re-throwing the exception.
                            if (returnCode >= 500 && returnCode <= 599)
                                throw sfe;
                        } catch (ClassCastException cce) {
                        } catch (IllegalArgumentException iae) {
                        }
                    }

                    if (sfe.getValidUnsentAddresses() != null && sfe.getValidUnsentAddresses().length > 0) {
                        if (isDebug)
                            log("Send failed, " + sfe.getValidUnsentAddresses().length + " valid addresses remain, continuing with any other servers");
                        lastError = sfe;
                    } else {
                        // There are no valid addresses left to send, so rethrow
                        throw sfe;
                    }
                } catch (MessagingException me) {
                    // MessagingException are horribly difficult to figure out
                    // what actually happened.
                    String exceptionBuffer = "Exception delivering message (" + mail.getName() + ") - " + me.getMessage();
                    log(exceptionBuffer);
                    if ((me.getNextException() != null) && (me.getNextException() instanceof java.io.IOException)) {
                        // This is more than likely a temporary failure

                        // If it's an IO exception with no nested exception,
                        // it's probably
                        // some socket or weird I/O related problem.
                        lastError = me;
                        continue;
                    }
                    // This was not a connection or I/O error particular to one
                    // SMTP server of an MX set. Instead, it is almost certainly
                    // a protocol level error. In this case we assume that this
                    // is an error we'd encounter with any of the SMTP servers
                    // associated with this MX record, and we pass the exception
                    // to the code in the outer block that determines its
                    // severity.
                    throw me;
                }
            } // end while
            // If we encountered an exception while looping through,
            // throw the last MessagingException we caught. We only
            // do this if we were unable to send the message to any
            // server. If sending eventually succeeded, we exit
            // deliver() though the return at the end of the try
            // block.
            if (lastError != null) {
                throw lastError;
            }
        } catch (SendFailedException sfe) {
            logSendFailedException(sfe);

            // Copy the recipients as direct modification may not be possible
            Collection<MailAddress> recipients = new ArrayList<MailAddress>(mail.getRecipients());

            boolean deleteMessage = false;

            /*
             * If you send a message that has multiple invalid addresses, you'll
             * get a top-level SendFailedException that that has the valid,
             * valid-unsent, and invalid address lists, with all of the server
             * response messages will be contained within the nested exceptions.
             * [Note: the content of the nested exceptions is implementation
             * dependent.]
             * 
             * sfe.getInvalidAddresses() should be considered permanent.
             * sfe.getValidUnsentAddresses() should be considered temporary.
             * 
             * JavaMail v1.3 properly populates those collections based upon the
             * 4xx and 5xx response codes to RCPT TO. Some servers, such as
             * Yahoo! don't respond to the RCPT TO, and provide a 5xx reply
             * after DATA. In that case, we will pick up the failure from
             * SMTPSendFailedException.
             */

            /*
             * SMTPSendFailedException introduced in JavaMail 1.3.2, and
             * provides detailed protocol reply code for the operation
             */
            try {
                if (sfe.getClass().getName().endsWith(".SMTPSendFailedException")) {
                    int returnCode = (Integer) invokeGetter(sfe, "getReturnCode");
                    // If we got an SMTPSendFailedException, use its RetCode to
                    // determine default permanent/temporary failure
                    deleteMessage = (returnCode >= 500 && returnCode <= 599);
                } else {
                    // Sometimes we'll get a normal SendFailedException with
                    // nested SMTPAddressFailedException, so use the latter
                    // RetCode
                    MessagingException me = sfe;
                    Exception ne;
                    while ((ne = me.getNextException()) != null && ne instanceof MessagingException) {
                        me = (MessagingException) ne;
                        if (me.getClass().getName().endsWith(".SMTPAddressFailedException")) {
                            int returnCode = (Integer) invokeGetter(me, "getReturnCode");
                            deleteMessage = (returnCode >= 500 && returnCode <= 599);
                        }
                    }
                }
            } catch (IllegalStateException ise) {
                // unexpected exception (not a compatible javamail
                // implementation)
            } catch (ClassCastException cce) {
                // unexpected exception (not a compatible javamail
                // implementation)
            }

            // log the original set of intended recipients
            if (isDebug)
                log("Recipients: " + recipients);

            if (sfe.getInvalidAddresses() != null) {
                Address[] address = sfe.getInvalidAddresses();
                if (address.length > 0) {
                    recipients.clear();
                    for (Address addres : address) {
                        try {
                            recipients.add(new MailAddress(addres.toString()));
                        } catch (ParseException pe) {
                            // this should never happen ... we should have
                            // caught malformed addresses long before we
                            // got to this code.
                            log("Can't parse invalid address: " + pe.getMessage());
                        }
                    }
                    // Set the recipients for the mail
                    mail.setRecipients(recipients);

                    if (isDebug)
                        log("Invalid recipients: " + recipients);
                    deleteMessage = failMessage(mail, sfe, true);
                }
            }

            if (sfe.getValidUnsentAddresses() != null) {
                Address[] address = sfe.getValidUnsentAddresses();
                if (address.length > 0) {
                    recipients.clear();
                    for (Address addres : address) {
                        try {
                            recipients.add(new MailAddress(addres.toString()));
                        } catch (ParseException pe) {
                            // this should never happen ... we should have
                            // caught malformed addresses long before we
                            // got to this code.
                            log("Can't parse unsent address: " + pe.getMessage());
                        }
                    }
                    // Set the recipients for the mail
                    mail.setRecipients(recipients);
                    if (isDebug)
                        log("Unsent recipients: " + recipients);
                    if (sfe.getClass().getName().endsWith(".SMTPSendFailedException")) {
                        int returnCode = (Integer) invokeGetter(sfe, "getReturnCode");
                        deleteMessage = failMessage(mail, sfe, returnCode >= 500 && returnCode <= 599);
                    } else {
                        deleteMessage = failMessage(mail, sfe, false);
                    }
                }
            }


            return deleteMessage;
        } catch (MessagingException ex) {
            // We should do a better job checking this... if the failure is a
            // general
            // connect exception, this is less descriptive than more specific
            // SMTP command
            // failure... have to lookup and see what are the various Exception
            // possibilities

            // Unable to deliver message after numerous tries... fail
            // accordingly

            // We check whether this is a 5xx error message, which
            // indicates a permanent failure (like account doesn't exist
            // or mailbox is full or domain is setup wrong).
            // We fail permanently if this was a 5xx error
            return failMessage(mail, ex, ('5' == ex.getMessage().charAt(0)));
        } catch (Exception ex) {
            log("Generic exception = permanent failure: "+ex.getMessage(), ex);
            // Generic exception = permanent failure
            return failMessage(mail, ex, true);
        }

        /*
         * If we get here, we've exhausted the loop of servers without sending
         * the message or throwing an exception. One case where this might
         * happen is if we get a MessagingException on each transport.connect(),
         * e.g., if there is only one server and we get a connect exception.
         */
        return failMessage(mail, new MessagingException("No mail server(s) available at this time."), false);
    }

    /**
     * Try to return a usefull logString created of the Exception which was
     * given. Return null if nothing usefull could be done
     *
     * @param e The MessagingException to use
     * @return logString
     */
    private String exceptionToLogString(Exception e) {
        if (e.getClass().getName().endsWith(".SMTPSendFailedException")) {
            return "RemoteHost said: " + e.getMessage();
        } else if (e instanceof SendFailedException) {
            SendFailedException exception = (SendFailedException) e;

            // No error
            if (exception.getInvalidAddresses().length == 0 && exception.getValidUnsentAddresses().length == 0)
                return null;

            Exception ex;
            StringBuilder sb = new StringBuilder();
            boolean smtpExFound = false;
            sb.append("RemoteHost said:");

            if (e instanceof MessagingException)
                while ((ex = ((MessagingException) e).getNextException()) != null && ex instanceof MessagingException) {
                    e = ex;
                    if (ex.getClass().getName().endsWith(".SMTPAddressFailedException")) {
                        try {
                            InternetAddress ia = (InternetAddress) invokeGetter(ex, "getAddress");
                            sb.append(" ( ").append(ia).append(" - [").append(ex.getMessage().replaceAll("\\n", "")).append("] )");
                            smtpExFound = true;
                        } catch (IllegalStateException ise) {
                            // Error invoking the getAddress method
                        } catch (ClassCastException cce) {
                            // The getAddress method returned something
                            // different than InternetAddress
                        }
                    }
                }
            if (!smtpExFound) {
                boolean invalidAddr = false;
                sb.append(" ( ");

                if (exception.getInvalidAddresses().length > 0) {
                    sb.append(Arrays.toString(exception.getInvalidAddresses()));
                    invalidAddr = true;
                }
                if (exception.getValidUnsentAddresses().length > 0) {
                    if (invalidAddr)
                        sb.append(" ");
                    sb.append(Arrays.toString(exception.getValidUnsentAddresses()));
                }
                sb.append(" - [");
                sb.append(exception.getMessage().replaceAll("\\n", ""));
                sb.append("] )");
            }
            return sb.toString();
        }
        return null;
    }

    /**
     * Utility method used to invoke getters for javamail implementation
     * specific classes.
     *
     * @param target the object whom method will be invoked
     * @param getter the no argument method name
     * @return the result object
     * @throws IllegalStateException on invocation error
     */
    private Object invokeGetter(Object target, String getter) {
        try {
            Method getAddress = target.getClass().getMethod(getter);
            return getAddress.invoke(target);
        } catch (NoSuchMethodException nsme) {
            // An SMTPAddressFailedException with no getAddress method.
        } catch (IllegalAccessException iae) {
        } catch (IllegalArgumentException iae) {
        } catch (InvocationTargetException ite) {
            // Other issues with getAddress invokation.
        }
        return new IllegalStateException("Exception invoking " + getter + " on a " + target.getClass() + " object");
    }

    /*
     * private method to log the extended SendFailedException introduced in
     * JavaMail 1.3.2.
     */
    private void logSendFailedException(SendFailedException sfe) {
        if (isDebug) {
            MessagingException me = sfe;
            if (me.getClass().getName().endsWith(".SMTPSendFailedException")) {
                try {
                    String command = (String) invokeGetter(sfe, "getCommand");
                    Integer returnCode = (Integer) invokeGetter(sfe, "getReturnCode");
                    log("SMTP SEND FAILED:");
                    log(sfe.toString());
                    log("  Command: " + command);
                    log("  RetCode: " + returnCode);
                    log("  Response: " + sfe.getMessage());
                } catch (IllegalStateException ise) {
                    // Error invoking the getAddress method
                    log("Send failed: " + me.toString());
                } catch (ClassCastException cce) {
                    // The getAddress method returned something different than
                    // InternetAddress
                    log("Send failed: " + me.toString());
                }
            } else {
                log("Send failed: " + me.toString());
            }
            Exception ne;
            while ((ne = me.getNextException()) != null && ne instanceof MessagingException) {
                me = (MessagingException) ne;
                if (me.getClass().getName().endsWith(".SMTPAddressFailedException") || me.getClass().getName().endsWith(".SMTPAddressSucceededException")) {
                    try {
                        String action = me.getClass().getName().endsWith(".SMTPAddressFailedException") ? "FAILED" : "SUCCEEDED";
                        InternetAddress address = (InternetAddress) invokeGetter(me, "getAddress");
                        String command = (String) invokeGetter(me, "getCommand");
                        Integer returnCode = (Integer) invokeGetter(me, "getReturnCode");
                        log("ADDRESS " + action + ":");
                        log(me.toString());
                        log("  Address: " + address);
                        log("  Command: " + command);
                        log("  RetCode: " + returnCode);
                        log("  Response: " + me.getMessage());
                    } catch (IllegalStateException ise) {
                        // Error invoking the getAddress method
                    } catch (ClassCastException cce) {
                        // A method returned something different than expected
                    }
                }
            }
        }
    }

    /**
     * Converts a message to 7 bit.
     *
     * @param part
     */
    private void convertTo7Bit(MimePart part) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            MimeMultipart parts = (MimeMultipart) part.getContent();
            int count = parts.getCount();
            for (int i = 0; i < count; i++) {
                convertTo7Bit((MimePart) parts.getBodyPart(i));
            }
        } else if ("8bit".equals(part.getEncoding())) {
            // The content may already be in encoded the form (likely with mail
            // created from a
            // stream). In that case, just changing the encoding to
            // quoted-printable will mangle
            // the result when this is transmitted. We must first convert the
            // content into its
            // native format, set it back, and only THEN set the transfer
            // encoding to force the
            // content to be encoded appropriately.

            // if the part doesn't contain text it will be base64 encoded.
            String contentTransferEncoding = part.isMimeType("text/*") ? "quoted-printable" : "base64";
            part.setContent(part.getContent(), part.getContentType());
            part.setHeader("Content-Transfer-Encoding", contentTransferEncoding);
            part.addHeader("X-MIME-Autoconverted", "from 8bit to " + contentTransferEncoding + " by " + getMailetContext().getServerInfo());
        }
    }

    /**
     * Insert the method's description here.
     *
     * @param mail      org.apache.james.core.MailImpl
     * @param ex        javax.mail.MessagingException
     * @param permanent
     * @return boolean Whether the message failed fully and can be deleted
     */
    private boolean failMessage(Mail mail, Exception ex, boolean permanent) {
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);
        if (permanent) {
            out.print("Permanent");
        } else {
            out.print("Temporary");
        }

        String exceptionLog = exceptionToLogString(ex);

        StringBuilder logBuffer = new StringBuilder(64).append(" exception delivering mail (").append(mail.getName());

        if (exceptionLog != null) {
            logBuffer.append(". ");
            logBuffer.append(exceptionLog);
        }

        logBuffer.append(": ");
        out.print(logBuffer.toString());
        if (isDebug)
            ex.printStackTrace(out);
        log(sout.toString());
        if (!permanent) {
            if (!mail.getState().equals(Mail.ERROR)) {
                mail.setState(Mail.ERROR);
                mail.setErrorMessage("0");
                mail.setLastUpdated(new Date());
            }

            int retries = 0;
            try {
                retries = Integer.parseInt(mail.getErrorMessage());
            } catch (NumberFormatException e) {
                // Something strange was happen with the errorMessage..
            }

            if (retries < maxRetries) {
                logBuffer = new StringBuilder(128).append("Storing message ").append(mail.getName()).append(" into outgoing after ").append(retries).append(" retries");
                log(logBuffer.toString());
                ++retries;
                mail.setErrorMessage(retries + "");
                mail.setLastUpdated(new Date());
                return false;
            } else {
                logBuffer = new StringBuilder(128).append("Bouncing message ").append(mail.getName()).append(" after ").append(retries).append(" retries");
                log(logBuffer.toString());
            }
        }

        if (mail.getSender() == null) {
            log("Null Sender: no bounce will be generated for " + mail.getName());
            return true;
        }

        if (bounceProcessor != null) {
            // do the new DSN bounce
            // setting attributes for DSN mailet
            String cause;
            if (ex instanceof MessagingException) {
                cause = getErrorMsg((MessagingException) ex);
            } else {
                cause = ex.getMessage();
            }
            mail.setAttribute("delivery-error", cause);
            mail.setState(bounceProcessor);
            // re-insert the mail into the spool for getting it passed to the
            // dsn-processor
            MailetContext mc = getMailetContext();
            try {
                mc.sendMail(mail);
            } catch (MessagingException e) {
                // we shouldn't get an exception, because the mail was already
                // processed
                log("Exception re-inserting failed mail: ", e);
            }
        } else {
            // do an old style bounce
            bounce(mail, ex);
        }
        return true;
    }

    /**
     * Utility method for getting the error message from the (nested) exception.
     *
     * @param me MessagingException
     * @return error message
     */
    protected String getErrorMsg(MessagingException me) {
        if (me.getNextException() == null) {
            return me.getMessage().trim();
        } else {
            Exception ex1 = me.getNextException();
            return ex1.getMessage().trim();
        }
    }

    private void bounce(Mail mail, Exception ex) {
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);
        String machine;
        try {
            machine = heloNameProvider.getHeloName();

        } catch (Exception e) {
            machine = "[address unknown]";
        }
        String bounceBuffer = "Hi. This is the James mail server at " + machine + ".";
        out.println(bounceBuffer);
        out.println("I'm afraid I wasn't able to deliver your message to the following addresses.");
        out.println("This is a permanent error; I've given up. Sorry it didn't work out.  Below");
        out.println("I include the list of recipients and the reason why I was unable to deliver");
        out.println("your message.");
        out.println();
        for (MailAddress mailAddress : mail.getRecipients()) {
            out.println(mailAddress);
        }
        if (ex instanceof MessagingException) {
            if (((MessagingException) ex).getNextException() == null) {
                out.println(ex.getMessage().trim());
            } else {
                Exception ex1 = ((MessagingException) ex).getNextException();
                if (ex1 instanceof SendFailedException) {
                    out.println("Remote mail server told me: " + ex1.getMessage().trim());
                } else if (ex1 instanceof UnknownHostException) {
                    out.println("Unknown host: " + ex1.getMessage().trim());
                    out.println("This could be a DNS server error, a typo, or a problem with the recipient's mail server.");
                } else if (ex1 instanceof ConnectException) {
                    // Already formatted as "Connection timed out: connect"
                    out.println(ex1.getMessage().trim());
                } else if (ex1 instanceof SocketException) {
                    out.println("Socket exception: " + ex1.getMessage().trim());
                } else {
                    out.println(ex1.getMessage().trim());
                }
            }
        }
        out.println();

        log("Sending failure message " + mail.getName());
        try {
            getMailetContext().bounce(mail, sout.toString());
        } catch (MessagingException me) {
            log("Encountered unexpected messaging exception while bouncing message: " + me.getMessage());
        } catch (Exception e) {
            log("Encountered unexpected exception while bouncing message: " + e.getMessage());
        }
    }

    /**
     * Returns the javamail Session object.
     *
     * @param props
     * @return the java mail session
     */
    protected Session obtainSession(Properties props) {
        return Session.getInstance(props);
    }

    /**
     * Returns an Iterator over org.apache.mailet.HostAddress, a specialized
     * subclass of javax.mail.URLName, which provides location information for
     * servers that are specified as mail handlers for the given hostname. If no
     * host is found, the Iterator returned will be empty and the first call to
     * hasNext() will return false. The Iterator is a nested iterator: the outer
     * iteration is over each gateway, and the inner iteration is over
     * potentially multiple A records for each gateway.
     *
     * @param gatewayServers - Collection of host[:port] Strings
     * @return an Iterator over HostAddress instances, sorted by priority
     * @since v2.2.0a16-unstable
     */
    private Iterator<HostAddress> getGatewaySMTPHostAddresses(Collection<String> gatewayServers) {
        Iterator<String> gateways = gatewayServers.iterator();

        return new MXHostAddressIterator(gateways, dnsServer, false, logger);
    }

}
