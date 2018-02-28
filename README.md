# Welcome to WSO2 Email Transports 
This repository holds email protocol implementations for C5 based products. 

# Introduction
This repository holds email protocol implementation using javax.mail api for wso2 products. Currently this is used in wso2 Stream Processor 4.0.0. 

# Features
This implementation has mainly two parts,
 1. Email Server Connector: The Server connector has ability to poll the email account and search for the new mails which satisfy the conditions given in the email searchTerm. Email server connector supports receiving email through 'imap' or 'pop3' server. If mail receiving server is pop3, then it supports only 'delete' for the processed mails. For the imap server, it supports different actions for processed mails like setting flags, deleting or moving to another folder. 
  Further it support to search mail using different search terms like `to` address, `from` address, `subject` and etc.
   
 2. Email Client Connector: Client connector send the email message to backend. Currently it only support only html and plain text 

# Features
- Provides the implementation for handling transport layer complexity of the message interaction
- Handles the concurrent connections from the source systems and connect with multiple target systems
- Formulates a common message context (Carbon Message) and emit that to the message processing layer
