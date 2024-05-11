# ffx-fire-infra
Contains code for creating and maintaining the AWS cloud infrastructure for a Fairfax Country Fire and Rescue operations management application.

## Requirements
Java 17,<br/> 
Maven 3.9,<br/> 
AWS CLI 2.15,<br/> 
AWS CDK 2.134<br/?

## Setup
1. Configure AWS CDK programmatic access
2. Bootstrap the AWS environment 

## Stacks
### FFX Fire Network Stack
Defines VPC network resources

### FFX Fire Data Store Stack
Defines RDS resources

### FFX Fire App Stack
Defines service applcaiotn resources
