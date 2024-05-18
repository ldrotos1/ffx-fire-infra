package com.ffx.stacks;

import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.DefaultInstanceTenancy;
import software.amazon.awscdk.services.ec2.IpAddresses;
import software.amazon.awscdk.services.ec2.IpProtocol;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;

public class FfxFireNetworkStack extends Stack {

    private final Vpc vpc;
    private final SecurityGroup rdsAccessSecurityGroup;
    private final SecurityGroup serviceInstanceSecurityGroup;

    public FfxFireNetworkStack(final Construct scope, final String id, final Map<String, Object> config) {
        this(scope, id, null, null);
    }

    public FfxFireNetworkStack(final Construct scope, final String id, final StackProps props, final Map<String, Object> config) {
        super(scope, id, props);

        final String projectName = config.get("project_name").toString();
        final String vpcCidrRange = config.get("vpc_cidr_range").toString();
        final String devMachineIp = config.get("dev_machine_ip").toString();

        // Creates the VPC with two public subnets
        this.vpc = Vpc.Builder.create(this, projectName.concat("-vpc"))
            .vpcName(projectName.concat("-vpc"))
            .createInternetGateway(true)
            .defaultInstanceTenancy(DefaultInstanceTenancy.DEFAULT)
            .enableDnsHostnames(true)
            .enableDnsSupport(true)
            .ipAddresses(IpAddresses.cidr(vpcCidrRange))
            .ipProtocol(IpProtocol.IPV4_ONLY)
            .maxAzs(2)
            .subnetConfiguration(List.of(
                SubnetConfiguration.builder()
                    .name(projectName.concat("-public-subnet"))
                    .subnetType(SubnetType.PUBLIC)
                    .build()))
            .build();

        // Creates the security group for proving dev macine access to the RDS instance
        this.rdsAccessSecurityGroup = SecurityGroup.Builder.create(this, projectName.concat("rds-access-sg"))
            .vpc(vpc)
            .securityGroupName(projectName.concat("rds-access-sg"))
            .description("Allow dev machine access")
            .allowAllOutbound(true)
            .build();
        this.rdsAccessSecurityGroup.addIngressRule(Peer.ipv4(devMachineIp), Port.tcp(5432), "Allow dev machine access");

        // Creates the service instance security group
        this.serviceInstanceSecurityGroup = SecurityGroup.Builder.create(this, projectName.concat("services-sg"))
            .vpc(vpc)
            .securityGroupName(projectName.concat("services-sg"))
            .description("Allow http and ssh access to services instances")
            .allowAllOutbound(true)
            .build();
        this.serviceInstanceSecurityGroup.addIngressRule(Peer.ipv4("0.0.0.0/0"), Port.tcp(8080), "Allow http access");
        this.serviceInstanceSecurityGroup.addIngressRule(Peer.ipv4(devMachineIp), Port.tcp(22), "Allow ssh access");
    }

    public Vpc getVpc() {
        return this.vpc;
    }

    public SecurityGroup getRdsAccessSecurityGroup() {
        return this.rdsAccessSecurityGroup;
    }

    public SecurityGroup getServiceInstanceSecurityGroup() {
        return this.serviceInstanceSecurityGroup;
    }
}