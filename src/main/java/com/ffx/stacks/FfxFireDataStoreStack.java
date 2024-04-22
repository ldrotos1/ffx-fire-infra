package com.ffx.stacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import software.constructs.Construct;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;

public class FfxFireDataStoreStack extends Stack {

  public FfxFireDataStoreStack(final Construct scope, final String id, final Map<String, Object> config, 
    FfxFireNetworkStack network) {
    this(scope, id, null, null, network);
  }

  public FfxFireDataStoreStack(final Construct scope, final String id, final StackProps props, 
    final Map<String, Object> config, FfxFireNetworkStack network) {
    super(scope, id, props);

    final String projectName = config.get("project_name").toString();
    final String adminUser = config.get("db_admin_user").toString();
    final String adminPassword = config.get("db_admin_password").toString();

    List<SecurityGroup> securityGroups = new ArrayList<SecurityGroup>();
    securityGroups.add(network.getSecurityGroup());

    DatabaseInstance.Builder.create(this, projectName.concat("-db"))
      .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder().version(PostgresEngineVersion.VER_16_1).build()))
      .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.MICRO))
      .credentials(Credentials.fromPassword(adminUser, SecretValue.unsafePlainText(adminPassword)))
      .vpc(network.getVpc())
      .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
      .securityGroups(securityGroups)
      .build();
  }
}