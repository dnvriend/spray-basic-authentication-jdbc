# spray-basic-authentication-jdbc
We will secure our spray service with Basic Authentication. For this example however, we will only secure the /secure
context, and not the /api context. 

# Command Query Responsibility Segregation (CQRS)
CQRS is the principle that uses separate Query and Command objects to retrieve and modify data. It can be used with 
Event Sourcing to store the events and replay them back to recover the state of the Actor aka. Aggregate. Gregg Young
and Eric Evans do a very good job at explaining the concepts so I invite you to search for lectures from them about
these topics.

This solution uses Akka Actors as domain aggregates, companion objects for the bounded contexts, to put the case classes
into, the domain commmands and events, Akka-persistence for event sourcing, views and aggregate separation for CQRS, 
and basic authentication to authenticate access to our precious resource. 

# Docker
This example can be run using [Docker](http://docker.io) and I would strongly advice using Docker and just take a few 
hours to work through the guide on how to use this great piece of software.

## Run the image
When you have Docker installed, you can launch a [containerized version](https://registry.hub.docker.com/u/dnvriend/spray-basic-authentication-jdbc/). 
However, this example uses two containers, one that will run the Postgres database and one that will run the example application. Let's first run the
Docker training/postgresql container:

    $ sudo docker run -d --name db training/postgres 

The Docker training/postgresql container runs the database we will use for storing the events from our example. The credentials are:
 
    username: docker
    password: docker
    database-name: docker

Next we will create the necessary schema's:

    $ sudo docker run -ti --name ubuntu --link db:db ubuntu:14.04 /bin/bash
    $ sudo apt-get install -qqy postgresql-client nano
    $ nano schema.sql
    
Then copy the database schema, by selecting the text below and pasting it in nano, when finished press CTRL+O and CTRL+X     

    CREATE TABLE IF NOT EXISTS public.journal (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_number BIGINT NOT NULL,
      marker VARCHAR(255) NOT NULL,
      message TEXT NOT NULL,
      created TIMESTAMP NOT NULL,
      PRIMARY KEY(persistence_id, sequence_number)
    );
    
    CREATE TABLE IF NOT EXISTS public.snapshot (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_nr BIGINT NOT NULL,
      snapshot TEXT NOT NULL,
      created BIGINT NOT NULL,
      PRIMARY KEY (persistence_id, sequence_nr)
    );
    
Then type the following commands:
    
    $ psql --dbname=docker --host=$DB_PORT_5432_TCP_ADDR --username=docker < schema.sql  
    $ exit

Finally we can launch the example container:

    $ sudo docker run -d -P --link db:db --name spray-basic-authentication-jdbc dnvriend/spray-basic-authentication-jdbc

Check which local port has been mapped to the Vagrant VM:
    
    $ sudo docker ps spray-basic-authentication-jdbc
    
And note the entries in the PORTS column eg:

    CONTAINER ID        IMAGE                                             COMMAND                CREATED             STATUS              PORTS                     NAMES
    592cbd800b0f        dnvriend/spray-basic-authentication-jdbc:latest   /bin/sh -c java -jar   2 seconds ago       Up 1 seconds        0.0.0.0:49156->8080/tcp   spray-basic-authentication-jdbc
    28a8c28a2726        training/postgres:latest                          su postgres -c /usr/   About an hour ago   Up 37 minutes       5432/tcp                  db,spray-basic-authentication-jdbc/db,ubuntu/db,web/db

In this example, the local port of my Vagrant VM has been mapped to port 49156 to the port of the example application, and that is 8080. 
Point the browser to the following url (change the port to your mapped port):

    http://192.168.99.99:49156/web/index.html    
    
To view the log output:
    
    $ sudo docker logs -f spray-basic-authentication-jdbc

To stop the container:

    $ sudo docker stop spray-basic-authentication-jdbc
    
To remove the image from your computer:
    
    $ sudo docker rm -f spray-basic-authentication-jdbc
    $ sudo docker rm -f tutorial/postgres
    $ sudo docker rm -f ubuntu
    
# Httpie
We will use the *great* tool [httpie](https://github.com/jakubroztocil/httpie), so please install it:

# REST API
## Getting a list of users
        
    $ http http://localhost:8080/api/users
    
## Get a user by name
The username has been encoded in the path
    
    $ http http://localhost:8080/api/users/foo
    
## Adding or updating users

    $ http PUT http://localhost:8080/api/users username="foo" password="bar"
    
## Deleting a user
The username has been encoded in the path

    $ http DELETE http://localhost:8080/api/users/foo
    
# The secured resource
The resource that has been secured for us is in the /secure context

## No authentication

    $ http http://localhost:8080/secure

## Basic Authentication
    
    $ http -a foo:bar http://localhost:8080/secure
    
# User Credentials CRUD Interface
The application should automatically launch your browser and show the CRUD screen, but if it doesn't:

    http://localhost:8080/web/index.html
        
Have fun!