package com.ffx.stacks;

import software.constructs.Construct;

import java.util.Map;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.ec2.AmazonLinuxGeneration;
import software.amazon.awscdk.services.ec2.AmazonLinuxImage;
import software.amazon.awscdk.services.ec2.Instance;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.KeyPair;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;

public class FfxFireAppStack extends Stack {

    public FfxFireAppStack(final Construct scope, final String id, final Map<String, Object> config, 
        FfxFireNetworkStack network) {
        this(scope, id, null, null, network);
    }

    public FfxFireAppStack(final Construct scope, final String id, final StackProps props, 
        final Map<String, Object> config, FfxFireNetworkStack network) {
        super(scope, id, props);

        final String projectName = config.get("project_name").toString();
        final String keyPairId = config.get("key_pair_id").toString();
        final String keyPairName = config.get("key_pair_name").toString();

        // Creates the staging bucket for storing artifacts that will be deployed
        Bucket bucket = new Bucket(this, projectName.concat("-staging"));

        // Creates the IAM role that will give read access to the staging bucket 
        Role role = Role.Builder.create(this, projectName.concat("-services-role"))
            .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
            .description("Provides access to staging bucket")
            .roleName(projectName.concat("-services-role"))
            .build();
        role.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AmazonS3ReadOnlyAccess"));

        // Creates the services instance
        Instance instance = Instance.Builder.create(this, projectName.concat("-services"))
            .vpc(network.getVpc())
            .instanceName(projectName.concat("-services"))
            .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
            .machineImage(AmazonLinuxImage.Builder.create().generation(AmazonLinuxGeneration.AMAZON_LINUX_2023).build())
            .securityGroup(network.getServiceInstanceSecurityGroup())
            .keyPair(KeyPair.fromKeyPairName(this, keyPairId, keyPairName))
            .build();
        
    }
}