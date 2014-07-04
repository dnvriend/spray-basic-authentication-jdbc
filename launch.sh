#!/bin/bash
./activator -Djdbc-connection.username=admin -Djdbc-connection.password=admin -Djdbc-connection.url=jdbc:postgresql://localhost:5432/mydb 'run-main com.example.Main'
