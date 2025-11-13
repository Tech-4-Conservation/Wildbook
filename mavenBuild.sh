#!/bin/bash

mvn clean
mvn install -DskipTests -Dmaven.javadoc.skip=true
