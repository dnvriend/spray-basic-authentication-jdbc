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

# akka-persistence-jdbc
This example stores events into the 'journal' table of a Postgresql database, so please first create the following schema:

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
    
Then change the credentials in the launch.sh file:
    
    #!/bin/bash
    ./activator -Djdbc-connection.username=admin -Djdbc-connection.password=admin -Djdbc-connection.url=jdbc:postgresql://localhost:5432/mydb 'run-main com.example.Main'

# Launching
Launch the application with the following script

    $ ./launch.sh

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