package com.ffx.stacks;

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
import software.constructs.Construct;

public class FfxFireUiStack extends Stack {
   
    public FfxFireUiStack(final Construct scope, final String id, final Map<String, Object> config, 
        FfxFireNetworkStack network, FfxFireStagingStack staging) {
            this(scope, id, null, null, network, staging);
    }

    public FfxFireUiStack(final Construct scope, final String id, final StackProps props, 
        final Map<String, Object> config, FfxFireNetworkStack network, FfxFireStagingStack staging) {
        super(scope, id, props);

        final String projectName = config.get("project_name").toString();
        final String keyPairName = config.get("key_pair_name").toString();

        // Creates the user data script for the EC2 instance
        UserData userData = UserData.forLinux();
        userData.addCommands("sudo yum update -y");
        userData.addCommands("curl \"https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip\" -o \"awscliv2.zip\"");
        userData.addCommands("unzip awscliv2.zip");
        userData.addCommands("sudo ./aws/install");   
        userData.addCommands("sudo yum install httpd -y");
        userData.addCommands("sudo systemctl start httpd");
        userData.addCommands("sudo systemctl enable httpd");
        userData.addCommands("sudo chmod 777 /var/www/html");
        userData.addCommands("sudo aws s3 cp s3://ffx-fire-staging/ffx-fire-ui.zip ffx-fire-ui.zip");
        userData.addCommands("sudo unzip ffx-fire-ui.zip");
        userData.addCommands("sudo cp -a dist/spa/. /var/www/html/");

        // Creates the services ASG
        AutoScalingGroup autoScalingGroup = AutoScalingGroup.Builder.create(this, projectName.concat("-ui-asg"))
            .autoScalingGroupName(projectName.concat("-ui-asg"))    
            .vpc(network.getVpc())
            .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
            .machineImage(AmazonLinuxImage.Builder.create().generation(AmazonLinuxGeneration.AMAZON_LINUX_2023).build())
            .securityGroup(network.getUiInstanceSecurityGroup())
            .keyName(keyPairName)
            .role(staging.getAssetsAccessRole())
            .minCapacity(0)
            .maxCapacity(1)
            .desiredCapacity(0)
            .userData(userData)
            .build();
        autoScalingGroup.attachToApplicationTargetGroup(network.getUiTargetGroup());        
    }
}
