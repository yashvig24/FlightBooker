# FlightBooker
Java application built on top of a Azure SQL Server to login a user, maintain a username and password database, search, book, cancel, itineraries, book flights. 

Does so in real time using real time data and implementations of transactions in order to prevent same time booking conflicts, cancelation conflicts etc. 

Most of all, it allows users to create accounts, save searches, and book iteineraries through the saved searches. Once a user is logged in through one machine, it does not let him log in again through another machine. 


MVC Structure

View, Controller : FlightService.java

Model : Query.java
