# room-booking

This GraphQL API allows for people to book rooms.


# Solution overview

This GraphQL API is implemented using AWS AppSync, with AWS Lambda as data sources used by AppSync.  All data is stored in DynamoDB tables.  All AWS resources are configured to "scale to zero" so no cost is incurred when the API is not being called. 

# Project structure

The directory `/api` contains the GraphQL schema for this API.

The directory `/impl` contains a Maven java project that provides Java Lambda data sources used to implement the API.

The directory `/deploy` contains a bash script `deploy.sh` that is used to deploy this API into AWS.

The directory `/deploy/terraform` contains the terraform files needed to create the AWS resources that implement this API.