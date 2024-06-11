package com.ffx.stacks;

import software.constructs.Construct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import software.amazon.awscdk.Duration;
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
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetGroupsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.route53.CnameRecord;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;

public class FfxFireNetworkStack extends Stack {

    private final Vpc vpc;
    private final SecurityGroup rdsAccessSecurityGroup;
    private final SecurityGroup serviceInstanceSecurityGroup;
    private final SecurityGroup serviceAlbSecurityGroup;
    private final ApplicationTargetGroup targetGroup;
    private final ApplicationLoadBalancer loadBalancer;
    private final ApplicationListener listener;

    public FfxFireNetworkStack(final Construct scope, final String id, final Map<String, Object> config) {
        this(scope, id, null, null);
    }

    public FfxFireNetworkStack(final Construct scope, final String id, final StackProps props, final Map<String, Object> config) {
        super(scope, id, props);

        final String projectName = config.get("project_name").toString();
        final String vpcCidrRange = config.get("vpc_cidr_range").toString();
        final String devMachineIp = config.get("dev_machine_ip").toString();
        final String domainName = config.get("domain_name").toString();
        final String apiDomain = config.get("api_domain").toString();

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

        // Creates the ALB security group
        this.serviceAlbSecurityGroup = SecurityGroup.Builder.create(this, projectName.concat("-alb-sg"))
            .vpc(vpc)
            .securityGroupName(projectName.concat("-alb-sg"))
            .description("Allow http to the ALB")
            .allowAllOutbound(true)
            .build();
        this.serviceAlbSecurityGroup.addIngressRule(Peer.ipv4("0.0.0.0/0"), Port.tcp(80), "Allow http access");

        // Creates the service instance security group
        this.serviceInstanceSecurityGroup = SecurityGroup.Builder.create(this, projectName.concat("-services-sg"))
            .vpc(vpc)
            .securityGroupName(projectName.concat("-services-sg"))
            .description("Allow http and ssh access to services instances")
            .allowAllOutbound(true)
            .build();
        this.serviceInstanceSecurityGroup.addIngressRule(Peer.ipv4(devMachineIp), Port.tcp(22), "Allow ssh access"); 
        this.serviceInstanceSecurityGroup.addIngressRule(this.serviceAlbSecurityGroup, Port.tcp(80), "Allow traffic from ALB");

        // Creates the security group for proving dev machine and EC2 service instances access to the RDS instance
        this.rdsAccessSecurityGroup = SecurityGroup.Builder.create(this, projectName.concat("-rds-access-sg"))
            .vpc(vpc)
            .securityGroupName(projectName.concat("-rds-access-sg"))
            .description("Allow dev machine access")
            .allowAllOutbound(true)
            .build();
        this.rdsAccessSecurityGroup.addIngressRule(Peer.ipv4(devMachineIp), Port.tcp(5432), "Allow dev machine access");
        this.rdsAccessSecurityGroup.addIngressRule(this.serviceInstanceSecurityGroup, Port.tcp(5432), "Allow EC2 service instance access");

        // Creates the application target group
        targetGroup = ApplicationTargetGroup.Builder.create(this, projectName.concat("-service-tg"))
            .vpc(this.vpc)
            .targetGroupName(projectName.concat("-service-tg"))
            .targetType(TargetType.INSTANCE)
            .protocol(ApplicationProtocol.HTTP)
            .port(80)
            .healthCheck(HealthCheck.builder()
                .path("/actuator/health")
                .port("80")
                .build())
            .build();

        // Creates the application load balancer
        this.loadBalancer = ApplicationLoadBalancer.Builder.create(this, projectName.concat("-alb"))
            .loadBalancerName(projectName.concat("-alb"))    
            .vpc(this.vpc)
            .crossZoneEnabled(true)
            .internetFacing(true)
            .securityGroup(serviceAlbSecurityGroup)
            .build();
        this.listener = this.loadBalancer.addListener(projectName.concat("-listener"), BaseApplicationListenerProps.builder()
            .protocol(ApplicationProtocol.HTTP)
            .port(80)
            .build());

        List<IApplicationTargetGroup> targetGroups = new ArrayList<IApplicationTargetGroup>();
        targetGroups.add(targetGroup);
        this.listener.addTargetGroups(projectName.concat("-tgs"), AddApplicationTargetGroupsProps.builder().targetGroups(targetGroups).build()); 

        // Creates the hosted zone
        HostedZoneProviderProps zoneProps = HostedZoneProviderProps.builder()
            .domainName(domainName)
            .build();
        IHostedZone hostedZone = HostedZone.fromLookup(this, projectName.concat("-hz"), zoneProps);
        CnameRecord.Builder.create(this, projectName.concat("-api-cname"))
            .recordName(apiDomain)
            .domainName(this.loadBalancer.getLoadBalancerDnsName())
            .zone(hostedZone)
            .ttl(Duration.seconds(300))
            .build();
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

    public ApplicationTargetGroup getServiceTargetGroup() {
        return this.targetGroup;
    } 
}