/*
 * Copyright (c) 2017 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.email.server.connector;

import com.sun.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.email.contract.EmailMessageListener;
import org.wso2.transport.email.contract.message.EmailBaseMessage;
import org.wso2.transport.email.exception.EmailConnectorException;
import org.wso2.transport.email.utils.Constants;
import org.wso2.transport.email.utils.EmailUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessageRemovedException;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.UIDFolder;
import javax.mail.internet.MimeMessage;
import javax.mail.search.SearchTerm;

/**
 * Class implemented to search and process emails.
 */
public class EmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailConsumer.class);

    private SearchTerm emailSearchTerm;
    private Map<String, String> emailProperties;
    private EmailMessageListener emailMessageListener;
    private String serviceId;
    private Session session;
    private Folder folder;
    private long startUIDNumberOfNextPollCycle = 1L;
    private long uidOfLastMailFetched = 1L;
    private Store store;
    private String host;
    private String username;
    private String password;
    private String storeType;
    private int maxRetryCount;
    private Long retryInterval;
    private String folderName;
    private String contentType;
    private Constants.ActionAfterProcessed action;
    private Folder moveToFolder = null;
    private boolean isFirstTimeConnect = true;
    private boolean autoAcknowledge = true;
    private boolean isImapFolder = false;

    /**
     * Check the given email configurations in the map and initialise the parameters needed for email server connector.
     *
     * @param id                    The service Id which this consumer belongs to
     * @param properties            Map which contains parameters needed to initialize the email server connector
     * @param emailSearchTerm       The search term which is going to use for fetch emails
     * @throws EmailConnectorException EmailConnectorException when action is failed
     *                                       due to a email layer error.
     */
    public EmailConsumer(String id, Map<String, String> properties, SearchTerm emailSearchTerm) throws
            EmailConnectorException {
        this.serviceId = id;
        this.emailProperties = properties;
        this.emailSearchTerm = emailSearchTerm;

        if (emailProperties.get(Constants.MAIL_RECEIVER_USERNAME) != null) {
            this.username = emailProperties.get(Constants.MAIL_RECEIVER_USERNAME);
        } else {
            throw new EmailConnectorException(
                    "Username (email address) of the email account is" + " a mandatory parameter."
                            + " It is not given in the email property map"
                            + " in the email server connector with service id: " + serviceId + ".");
        }

        if (emailProperties.get(Constants.MAIL_RECEIVER_PASSWORD) != null) {
            this.password = emailProperties.get(Constants.MAIL_RECEIVER_PASSWORD);
        } else {
            throw new EmailConnectorException("Password of the email account is" + " a mandatory parameter."
                    + " It is not given in the email property map" + " in the email server connector with service id: "
                    + serviceId + ".");
        }

        if (emailProperties.get(Constants.MAIL_RECEIVER_HOST_NAME) != null) {
            this.host = emailProperties.get(Constants.MAIL_RECEIVER_HOST_NAME);
        } else {
            throw new EmailConnectorException("HostName of the email account is" + " a mandatory parameter."
                    + " It is not given in the email property map" + " in the email server connector with service id: "
                    + serviceId + ".");
        }

        if (emailProperties.get(Constants.MAIL_RECEIVER_STORE_TYPE) != null) {
            this.storeType = emailProperties.get(Constants.MAIL_RECEIVER_STORE_TYPE);
        } else {
            throw new EmailConnectorException("Store type of the email account is" + " a mandatory parameter."
                    + " It is not given in the email property map" + " in the email server connector with service id: "
                    + serviceId + ".");
        }

        if (emailProperties.get(Constants.MAX_RETRY_COUNT) != null) {
            try {
                this.maxRetryCount = Integer.parseInt(emailProperties.get(Constants.MAX_RETRY_COUNT));
            } catch (NumberFormatException e) {
                throw new EmailConnectorException(
                        "Could not parse parameter '" + emailProperties.get(Constants.MAX_RETRY_COUNT)
                                + "' to numeric type 'Integer'" + " in the email server connector for service id :"
                                + serviceId + ".");
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Max retry count is not given in the email property map." + " Get default max retry count '"
                        + Constants.DEFAULT_RETRY_COUNT + "' by " + "the email server connector for service id:"
                        + serviceId + ".");
            }
            this.maxRetryCount = Constants.DEFAULT_RETRY_COUNT;
        }

        if (emailProperties.get(Constants.RETRY_INTERVAL) != null) {
            try {
                this.retryInterval = Long.parseLong(emailProperties.get(Constants.RETRY_INTERVAL));
            } catch (NumberFormatException e) {
                throw new EmailConnectorException(
                        "Could not parse parameter '" + emailProperties.get(Constants.RETRY_INTERVAL)
                                + " to numeric type 'Long'" + " in the email server connector for service id: "
                                + serviceId + ".");
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Retry interval is not given in the email property map." + " Get default retry interval '"
                        + Constants.DEFAULT_RETRY_INTERVAL + "' by "
                        + "the email server connector for service id:" + serviceId + ".");
            }
            retryInterval = Constants.DEFAULT_RETRY_INTERVAL;
        }

        if (emailProperties.get(Constants.CONTENT_TYPE) != null) {
            if (emailProperties.get(Constants.CONTENT_TYPE)
                    .equalsIgnoreCase(Constants.CONTENT_TYPE_TEXT_HTML)) {
                this.contentType = Constants.CONTENT_TYPE_TEXT_HTML;
            } else if (emailProperties.get(Constants.CONTENT_TYPE)
                    .equalsIgnoreCase(Constants.CONTENT_TYPE_TEXT_PLAIN)) {
                contentType = Constants.CONTENT_TYPE_TEXT_PLAIN;
            } else {
                throw new EmailConnectorException(
                        "Content type '" + emailProperties.get(Constants.CONTENT_TYPE) + "' is not supported by"
                                + " the email server connector for service id: " + serviceId + ".");
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Content type is not given in the email property map. " + "Get default content type '"
                        + Constants.DEFAULT_CONTENT_TYPE + "' by " + "the email server connector for service id:"
                        + serviceId + ".");
            }
            contentType = Constants.DEFAULT_CONTENT_TYPE;
        }

        if (emailProperties.get(Constants.MAIL_RECEIVER_FOLDER_NAME) != null) {
            this.folderName = emailProperties.get(Constants.MAIL_RECEIVER_FOLDER_NAME);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Folder to fetch mails is not given in the email property map." + " Get default folder '"
                        + Constants.DEFAULT_FOLDER_NAME + "' by the email server connector for service id: "
                        + serviceId + ".");
            }
            this.folderName = Constants.DEFAULT_FOLDER_NAME;

        }

        if (emailProperties.get(Constants.AUTO_ACKNOWLEDGE) != null) {
            this.autoAcknowledge = Boolean.parseBoolean(emailProperties.get(Constants.AUTO_ACKNOWLEDGE));
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Auto Acknowledgement property is not given in the email property map."
                        + " Get default value 'true' " + "by the email server connector for service id: " + serviceId
                        + ".");
            }
            this.autoAcknowledge = Constants.DEFAULT_AUTO_ACKNOWLEDGE_VALUE;
        }

        // other parameters relate to the email server start with 'mail.'. Check property map contain such parameters
        // and insert them to the serverProperty map.
        Properties serverProperties = new Properties();
        emailProperties.forEach((k, v) -> {
            if (k.startsWith("mail." + storeType)) {
                serverProperties.put(k, v);
            }
        });

        session = Session.getInstance(serverProperties);

        try {
            store = session.getStore(storeType);
        } catch (NoSuchProviderException e) {
            throw new EmailConnectorException(
                    "Couldn't initialize the store '" + storeType + "' in the email server connector for service id: "
                            + serviceId + "." + e.getMessage(), e);
        }
    }

    /**
     * Method to set the email message listener.
     *
     * @param emailMessageListener Instance of the MessageListener belong to the server connector.
     */
    public void setEmailMessageListener(EmailMessageListener emailMessageListener) {
         this.emailMessageListener = emailMessageListener;
    }

    /**
     * Get the formatted email message and send the message to the frontend using message Listener and wait for
     * the acknowledgement to send next message.
     *
     * @throws EmailConnectorException EmailConnectorException when action is failed
     *                                       due to a email layer error.
     */
    public void consume() throws EmailConnectorException {

        openFolder(folder);
        List<Message> messageList = fetchEmails();

        if (messageList != null) {

            for (Message message : messageList) {
                try {
                    String content = getEmailContent(message);

                    if (!content.isEmpty()) {
                        //create carbon message
                        EmailBaseMessage emailMessage = EmailUtils.createEmailMessage(message,
                                folder, content, serviceId);
                        if (autoAcknowledge) {
                            emailMessageListener.onMessage(emailMessage);
                        } else {
                            emailMessageListener.onMessage(emailMessage);
                            emailMessage.waitTillAck();
                        }

                        //have to update uid after callback is arrived.
                        if (isImapFolder) {
                            startUIDNumberOfNextPollCycle =
                                    Long.parseLong(emailMessage.
                                            getProperty(Constants.MAIL_PROPERTY_UID).toString()) + 1;
                        }

                        ActionForProcessedMail.carryOutAction(message, folder, action, moveToFolder);
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Skip the message #: " + message.getMessageNumber() + " by further processing"
                                    + " in Email Server Connector for service: " + serviceId + ".");
                        }
                    }
                } catch (RuntimeException e) {
                    log.error("Catch a run time exception while processing the message in Email Server"
                            + " Connector for service: " + serviceId + "." + e.getMessage(), e);
                } catch (Exception e) {
                    log.warn("Skip the message #: " + message.getMessageNumber() + " by further processing in Email "
                            + "Server Connector for service: " + serviceId + "." + e.getMessage(), e);
                }
            }

            if (isImapFolder) {
                if (!(autoAcknowledge)) {
                    //since all messages are processed update start UID of the
                    startUIDNumberOfNextPollCycle = uidOfLastMailFetched + 1;
                }
            }
        }
        closeFolder(folder);
        if (moveToFolder != null) {
            closeFolder(moveToFolder);
        }
    }

    /**
     * Connect to the email server(store). If couldn't connect to the store retry number of 'maxRetry counts'.
     * If not, throw email server connector exception.
     *
     * @throws EmailConnectorException EmailConnectorException when action is failed
     *                                       due to a email layer error.
     */

    public void connectToEmailStore() throws EmailConnectorException {
        int retryCount = 0;
        while (!store.isConnected()) {
            try {
                retryCount++;

                if (log.isDebugEnabled()) {
                    log.debug("Attempting to connect to '" + storeType + "' server for : " + emailProperties
                            .get(Constants.MAIL_RECEIVER_USERNAME));
                }
                store.connect(host, username, password);

            } catch (MessagingException e) {
                log.error("Error connecting to mail server for address '" + username
                        + "' in the email server connector with id : " + serviceId + ".", e);
                if (maxRetryCount <= retryCount) {
                    throw new EmailConnectorException(
                            "Error connecting to mail server for the address '" + username
                                    + "' in the email server connector with id: " + serviceId + ".", e);
                }
            }

            if (store.isConnected()) {
                if (log.isDebugEnabled()) {
                    log.debug("Connected to the server: " + store);
                }

                // To keep the single instance of the folder
                if (isFirstTimeConnect) {
                    try {
                        folder = store.getFolder(folderName);
                        isFirstTimeConnect = false;
                        if (folder instanceof IMAPFolder) {
                            isImapFolder = true;
                        }
                    } catch (MessagingException e) {
                        throw new EmailConnectorException(
                                "Error is encountered, while getting the folder '" + folderName
                                        + "' in email server connector with service id: "
                                        + serviceId + "." + e.getMessage());
                    }
                }
            }

            if (!store.isConnected()) {
                try {
                    log.warn("Connection to mail server for account : " + username + " using service '" + serviceId
                            + "' is failed. Retrying in '" + retryInterval / 1000 + "' seconds");
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Thread is interrupted. It is ignored by the email server connector.", e);
                    }
                }
            }
        }
    }

    /**
     * Set the action according to the action after processed parameter in the given property map.
     *
     * @throws EmailConnectorException EmailConnectorException when action is failed
     *                                        due to a email layer error.
     */
    public void setAction() throws EmailConnectorException {
        action = EmailUtils
                .getActionAfterProcessed(emailProperties.get(Constants.ACTION_AFTER_PROCESSED), isImapFolder);

        //If action is 'move' then, have to check name of the folder to move the processed mail is given.
        // If not exception is thrown
        if (action.equals(Constants.ActionAfterProcessed.MOVE)) {
            if (emailProperties.get(Constants.MOVE_TO_FOLDER) != null) {
                try {
                    moveToFolder = store.getFolder(emailProperties.get(Constants.MOVE_TO_FOLDER));
                    if (!moveToFolder.exists()) {
                        moveToFolder.create(Folder.HOLDS_MESSAGES);
                    }
                    openFolder(moveToFolder);
                } catch (MessagingException e) {
                    throw new EmailConnectorException(
                            "Couldn't process the folder '" + moveToFolder + "'which used to move the processed mail"
                                    + " in the email server connector with id: " + serviceId + "."
                                    + e.getMessage(), e);
                }
            } else {
                throw new EmailConnectorException(Constants.MOVE_TO_FOLDER + " is a mandatory parameter, "
                        + "since the action for the processed mail is 'MOVE'"
                        + " in the email server connector with id: " + serviceId + ".");
            }
        }
    }

    /**
     * Open the email folder if the folder is not open.
     *
     * @param folder Instance of the folder which used to fetch the email.
     * @throws EmailConnectorException EmailConnectorException when action is failed
     *                                        due to a email layer error.
     */
    protected void openFolder(Folder folder) throws EmailConnectorException {
        if (store.isConnected()) {
            try {
                if (!folder.isOpen()) {
                    folder.open(Folder.READ_WRITE);
                } else {
                    closeFolder(folder);
                    folder.open(Folder.READ_WRITE);
                }
            } catch (MessagingException e) {
                throw new EmailConnectorException(
                        "Couldn't open the folder '" + folderName + " ' in READ_WRITE mode"
                                + " in the email server connector with id: " + serviceId + "." + e.getMessage(), e);
            }
        } else {
            try {
                connectToEmailStore();
                folder.open(Folder.READ_WRITE);
            } catch (MessagingException e) {
                throw new EmailConnectorException(
                        "Couldn't open the folder '" + folderName + " ' in READ_WRITE mode"
                                + " in the email server connector with id: " + serviceId + "." + e.getMessage(), e);
            }
        }
    }

    /**
     * Close the folder if it is open.
     *
     * @param folder Instance of the folder which is used to fetch emails
     * @throws EmailConnectorException EmailConnectorException
     *                                        due to a email layer error.
     */
    protected void closeFolder(Folder folder) throws EmailConnectorException {
        if (folder.isOpen()) {
            try {
                folder.close(true);
            } catch (MessagingException e) {
                log.warn("Couldn't close the folder '" + folderName + "' by the email server connector"
                        + " with service id: " + serviceId + "." + e.getMessage(), e);
            }
        }
    }

    /**
     * Fetch emails which satisfy the conditions given in the search term. If search term is 'null',
     * then fetch all the emails. If folder is IMAP folder, then fetch emails from the new emails.
     * If the folder is pop3, then fetch all the emails in the folder which satisfy the given conditions.
     *
     * @return List of messages which satisfy the search conditions.
     * @throws EmailConnectorException EmailConnectorException when action is failed
     *                                        due to a email layer error.
     */
    private List<Message> fetchEmails() throws EmailConnectorException {

        List<Message> messageList = null;
        long uid;

        if (log.isDebugEnabled()) {
            log.debug("Start to fetch the emails by email server connector with id: " + serviceId + ".");
        }

        try {
            if (isImapFolder) {

                Message[] messages = ((UIDFolder) folder)
                        .getMessagesByUID(startUIDNumberOfNextPollCycle, UIDFolder.LASTUID);

                //Even new messages are not in the folder, It gets always last message. Therefore,
                // when message length is equal to one, then have to check whether message uid is greater than
                //start uid. If not, have to return null.
                if (messages.length > 0) {
                    if (messages.length == 1) {
                        uid = ((UIDFolder) folder).getUID(messages[messages.length - 1]);
                        if (startUIDNumberOfNextPollCycle > uid) {
                            return messageList;
                        }
                    }
                    if (emailSearchTerm != null) {
                        Message[] filterMessages = folder.search(emailSearchTerm, messages);
                        if (filterMessages.length > 0) {
                            messageList = Arrays.asList(filterMessages);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Conditions(Search Term) is not specified. All the mails in the folder '"
                                    + folderName + "' will be fetched " + "by the email server connector " + "with id: "
                                    + serviceId + ".");
                        }
                        messageList = Arrays.asList(messages);
                    }

                    uidOfLastMailFetched = ((UIDFolder) folder).getUID(messages[messages.length - 1]);

                    if (autoAcknowledge) {
                        //update the startUID number
                        startUIDNumberOfNextPollCycle = uidOfLastMailFetched + 1;
                    }
                }

                // when folder is pop3Folder
            } else {
                if (emailSearchTerm != null) {

                    Message[] messages = folder.search(emailSearchTerm);
                    messageList = Arrays.asList(messages);

                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Conditions(Search Term) is not specified. All the mails in the folder '"
                                + folderName + "' will be fetched" + "by the email server connector "
                                + "with id: " + serviceId + ".");
                    }

                    Message[] messages = folder.getMessages();
                    messageList = Arrays.asList(messages);

                }
            }
        } catch (Exception e) {
            throw new EmailConnectorException(
                    "Error is encountered while fetching emails using " + "search term from the folder '" + folderName
                            + "'" + "by the email server connector with id: " + serviceId + "." + e.getMessage(), e);
        }

        if (log.isDebugEnabled()) {
            if (messageList != null) {
                if (!isImapFolder) {
                    log.debug("Number of email '" + messageList.size() + "' are fetched.");
                } else {
                    log.debug("Number of email '" + messageList.size() + "' are fetched." + " Last UID of the mail is '"
                            + (uidOfLastMailFetched) + "'");
                }
            }
        }

        return messageList;
    }

    /**
     * Get the start uid number.
     *
     * @return instance of Long value of startUIDNumber
     */
    public Long getStartUIDNumber() {
        return startUIDNumberOfNextPollCycle;
    }

    /**
     * Close folder if it is open and close the store if it is connected.
     */
    public void closeAll() throws EmailConnectorException {
        try {
            if (store != null && store.isConnected()) {
                if (folder != null && folder.isOpen()) {
                    folder.close(true);
                }
                store.close();
            }
        } catch (Exception e) {
            throw new EmailConnectorException("Error is encountered while closing the connection for"
                    + " the email server connector with id: " + serviceId + "." + e.getMessage(), e);
        }
    }

    /**
     * Set the start uid number.
     *
     * @param startUIDNumber value of start uid number
     */
    public void setStartUIDNumber(Long startUIDNumber) {
        this.startUIDNumberOfNextPollCycle = startUIDNumber;
    }

    /**
     * Read the content of the message according to the content type.
     *
     * @param message Message to read the content
     * @return a String instance which contains Message content
     * @throws EmailConnectorException EmailConnectorException when action is failed
     *                                       due to a email layer error.
     */
    private String getEmailContent(Message message) throws EmailConnectorException {
        String content = "";

        try {
            if (message instanceof MimeMessage) {
                if (message.isMimeType(Constants.CONTENT_TYPE_TEXT_PLAIN)) {
                    if (contentType.equals(Constants.CONTENT_TYPE_TEXT_PLAIN)) {
                        content = message.getContent().toString();
                    }
                } else if (message.isMimeType(Constants.CONTENT_TYPE_TEXT_HTML)) {
                    if (contentType.equals(Constants.CONTENT_TYPE_TEXT_HTML)) {
                        content = message.getContent().toString();
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Message with subject: " + message.getSubject() + ", is skipped from further"
                                + " processing, since content type '" + message.getContentType()
                                + "' of the email is not supported by the email server connector"
                                + " with id: " + serviceId + ".");
                    }
                }
                return content;
            } else {
                throw new EmailConnectorException("Couldn't read the content of the email by the "
                        + " since message is not a instance of MimeMessage");
            }
        } catch (MessageRemovedException e) {

            if (log.isDebugEnabled()) {
                log.debug("Skipping message # : " + message.getMessageNumber()
                        + " as it has been DELETED by another thread after processing");
            }

            throw new EmailConnectorException(
                    "Couldn't read the content of the message #" + message.getMessageNumber()
                            + "by the email server connector with service id '" + serviceId
                            + "' since it has been DELETED by another thread." + e.getMessage(), e);

        } catch (MessagingException | IOException e) {
            throw new EmailConnectorException("Error is encountered while reading the content of a message"
                    + " by the email server connector with service id '" + serviceId + "'" + e.getMessage(), e);
        }
    }
}
