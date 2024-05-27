package com.ffx.stacks;

import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.ec2.AmazonLinuxGeneration;
import software.amazon.awscdk.services.ec2.AmazonLinuxImage;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.UserData;
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
        final String keyPairName = config.get("key_pair_name").toString();

        // Creates the staging bucket for storing artifacts that will be deployed
        Bucket bucket = Bucket.Builder.create(this, projectName.concat("-staging"))
            .bucketName(projectName.concat("-staging"))
            .removalPolicy(RemovalPolicy.DESTROY)
            .autoDeleteObjects(true)
            .build(); 
        BucketDeployment.Builder.create(this, "deploy-service-assets")
            .sources(List.of(Source.asset("./assets")))
            .destinationBucket(bucket)
            .build();

        // Creates the IAM role that will give read access to the staging bucket 
        Role role = Role.Builder.create(this, projectName.concat("-services-role"))
            .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
            .description("Provides access to staging bucket")
            .roleName(projectName.concat("-services-role"))
            .build();
        role.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AmazonS3ReadOnlyAccess"));

        // Creates the user data script for the EC2 instance
        UserData userData = UserData.forLinux();
        userData.addCommands("curl \"https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip\" -o \"awscliv2.zip\"");
        userData.addCommands("unzip awscliv2.zip");
        userData.addCommands("sudo ./aws/install");
        userData.addCommands("sudo yum install java-17-amazon-corretto.x86_64 -y");
        userData.addCommands("sudo aws s3 cp s3://" + bucket.getBucketName() + "/ffx-fire-app.service /etc/systemd/system/ffx-fire-app.service");
        userData.addCommands("sudo aws s3 cp s3://" + bucket.getBucketName() + "/fire-services.jar /opt/fire-services.jar");
        userData.addCommands("sudo aws s3 cp s3://" + bucket.getBucketName() + "/application.properties /opt/application.properties");
        userData.addCommands("sudo systemctl daemon-reload");
        userData.addCommands("sudo systemctl enable ffx-fire-app.service");
        userData.addCommands("sudo systemctl start ffx-fire-app");

        // Creates the services ASG
        AutoScalingGroup.Builder.create(this, projectName.concat("-service-asg"))
            .autoScalingGroupName(projectName.concat("-service-asg"))    
            .vpc(network.getVpc())
            .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
            .machineImage(AmazonLinuxImage.Builder.create().generation(AmazonLinuxGeneration.AMAZON_LINUX_2023).build())
            .securityGroup(network.getServiceInstanceSecurityGroup())
            .keyName(keyPairName)
            .role(role)
            .minCapacity(0)
            .maxCapacity(1)
            .desiredCapacity(0)
            .userData(userData)
            .build();
    }
}