## Backend of a community web application for a sports department
Technology used: Java Spring framework.

Main features:
* user management: login, registration, email/account verification, password/username reset
* membership management: keeping records of active members of the sports department, keeping
track of/managing monthly membership fees, banning users, sending monthly reminder emails to
pay the membership fees, etc.
* payment system: Stripe API integration for credit card payment, saved payment methods; PayPal API
integration. After successful payments, custom invoices are generated from the transactions in PDF
format, which can be later queried for each user
* news publishing/management: option for admins to publish, edit, delete articles related to the
community of the department. Also implemented a custom search feature using Apache Lucene
* newsletter system: clients are able to subscribe to, and unsubscribe from the newsletter. Subscribed
clients get the most recent, top-viewed published articles weekly
* support system: option to submit tickets, where clients can describe arising problems/questions
(optionally upload attachments too). Admins manage the tickets: option to submit answers to
opened tickets, close/reopen tickets
