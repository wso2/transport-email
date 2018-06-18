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

package org.wso2.transport.email.contract.message;

/**
 * This class represent the Email message which can contain both text and attachments.
 */
public class EmailMessage extends EmailBaseMessage {
    private final String text;
    private String[] attachments;

    public EmailMessage(String text, String[] attachments) {
        this.text = text;
        this.attachments = attachments;
    }

    /**
     * Method to get text content of the email message
     *
     * @return text content to be included in the email message
     */
    public String getText() {
        return this.text;
    }

    /**
     * Method to get attachments list.
     *
     * @return array of file paths that needs to be attached to the email.
     */
    public String[] getAttachments() {
        return this.attachments;
    }
}
