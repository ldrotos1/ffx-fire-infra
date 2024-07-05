package com.ffx.stacks;

import software.constructs.Construct;

import java.util.Map;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.ec2.AmazonLinuxGeneration;
import software.amazon.awscdk.services.ec2.AmazonLinuxImage;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.UserData;

public class FfxFireApiStack extends Stack {

    public FfxFireApiStack(final Construct scope, final String id, final Map<String, Object> config, 
        FfxFireNetworkStack network, FfxFireStagingStack staging) {
        this(scope, id, null, null, network, staging);
    }

    public FfxFireApiStack(final Construct scope, final String id, final StackProps props, 
        final Map<String, Object> config, FfxFireNetworkStack network, FfxFireStagingStack staging) {
        super(scope, id, props);

        final String projectName = config.get("project_name").toString();
        final String keyPairName = config.get("key_pair_name").toString();

        // Creates the user data script for the EC2 instance
        UserData userData = UserData.forLinux();
        userData.addCommands("curl \"https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip\" -o \"awscliv2.zip\"");
        userData.addCommands("unzip awscliv2.zip");
        userData.addCommands("sudo ./aws/install");
        userData.addCommands("sudo yum install java-17-amazon-corretto.x86_64 -y");
        userData.addCommands("sudo aws s3 cp s3://" + staging.getStagingBucketName() + "/ffx-fire-app.service /etc/systemd/system/ffx-fire-app.service");
        userData.addCommands("sudo aws s3 cp s3://" + staging.getStagingBucketName() + "/fire-services.jar /opt/fire-services.jar");
        userData.addCommands("sudo aws s3 cp s3://" + staging.getStagingBucketName() + "/application.properties /opt/application.properties");
        userData.addCommands("sudo systemctl daemon-reload");
        userData.addCommands("sudo systemctl enable ffx-fire-app.service");
        userData.addCommands("sudo systemctl start ffx-fire-app");

        // Creates the services ASG
        AutoScalingGroup autoScalingGroup = AutoScalingGroup.Builder.create(this, projectName.concat("-service-asg"))
            .autoScalingGroupName(projectName.concat("-service-asg"))    
            .vpc(network.getVpc())
            .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
            .machineImage(AmazonLinuxImage.Builder.create().generation(AmazonLinuxGeneration.AMAZON_LINUX_2023).build())
            .securityGroup(network.getApiInstanceSecurityGroup())
            .keyName(keyPairName)
            .role(staging.getAssetsAccessRole())
            .minCapacity(0)
            .maxCapacity(1)
            .desiredCapacity(0)
            .userData(userData)
            .build();
        autoScalingGroup.attachToApplicationTargetGroup(network.getServiceTargetGroup());
    }
}