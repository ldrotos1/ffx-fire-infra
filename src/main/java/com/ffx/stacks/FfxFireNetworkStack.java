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
    private final SecurityGroup apiInstanceSecurityGroup;
    private final SecurityGroup uiInstanceSecurityGroup;
    private final SecurityGroup albSecurityGroup;
    private final ApplicationTargetGroup apiTargetGroup;
    private final ApplicationLoadBalancer apiLoadBalancer;
    private final ApplicationListener apiListener;
    private final ApplicationTargetGroup uiTargetGroup;
    private final ApplicationLoadBalancer uiLoadBalancer;
    private final ApplicationListener uiListener;

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
        final String uiDomain = config.get("ui_domain").toString();

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
        this.albSecurityGroup = SecurityGroup.Builder.create(this, projectName.concat("-alb-sg"))
            .vpc(vpc)
            .securityGroupName(projectName.concat("-alb-sg"))
            .description("Allow http to the ALB")
            .allowAllOutbound(true)
            .build();
        this.albSecurityGroup.addIngressRule(Peer.ipv4("0.0.0.0/0"), Port.tcp(80), "Allow http access");

        // Creates the API instance security group
        this.apiInstanceSecurityGroup = SecurityGroup.Builder.create(this, projectName.concat("-api-sg"))
            .vpc(vpc)
            .securityGroupName(projectName.concat("-api-sg"))
            .description("Allow http and ssh access to API instances")
            .allowAllOutbound(true)
            .build();
        this.apiInstanceSecurityGroup.addIngressRule(Peer.ipv4(devMachineIp), Port.tcp(22), "Allow ssh access"); 
        this.apiInstanceSecurityGroup.addIngressRule(this.albSecurityGroup, Port.tcp(80), "Allow traffic from ALB");

        // Creates the UI instance security group
        this.uiInstanceSecurityGroup = SecurityGroup.Builder.create(this, projectName.concat("-ui-sg"))
            .vpc(vpc)
            .securityGroupName(projectName.concat("-ui-sg"))
            .description("Allow http and ssh access to UI instances")
            .allowAllOutbound(true)
            .build();
        this.uiInstanceSecurityGroup.addIngressRule(Peer.ipv4(devMachineIp), Port.tcp(22), "Allow ssh access"); 
        this.uiInstanceSecurityGroup.addIngressRule(this.albSecurityGroup, Port.tcp(80), "Allow traffic from ALB");

        // Creates the security group for proving dev machine and API instances access to the RDS instance
        this.rdsAccessSecurityGroup = SecurityGroup.Builder.create(this, projectName.concat("-rds-access-sg"))
            .vpc(vpc)
            .securityGroupName(projectName.concat("-rds-access-sg"))
            .description("Allow dev machine access")
            .allowAllOutbound(true)
            .build();
        this.rdsAccessSecurityGroup.addIngressRule(Peer.ipv4(devMachineIp), Port.tcp(5432), "Allow dev machine access");
        this.rdsAccessSecurityGroup.addIngressRule(this.apiInstanceSecurityGroup, Port.tcp(5432), "Allow EC2 service instance access");

        // Creates the API application target group
        this.apiTargetGroup = ApplicationTargetGroup.Builder.create(this, projectName.concat("-api-tg"))
            .vpc(this.vpc)
            .targetGroupName(projectName.concat("-api-tg"))
            .targetType(TargetType.INSTANCE)
            .protocol(ApplicationProtocol.HTTP)
            .port(80)
            .healthCheck(HealthCheck.builder()
                .path("/actuator/health")
                .port("80")
                .build())
            .build();

        // Creates the UI application target group
        this.uiTargetGroup = ApplicationTargetGroup.Builder.create(this, projectName.concat("-ui-tg"))
            .vpc(this.vpc)
            .targetGroupName(projectName.concat("-ui-tg"))
            .targetType(TargetType.INSTANCE)
            .protocol(ApplicationProtocol.HTTP)
            .port(80)
            .healthCheck(HealthCheck.builder()
                .path("/")
                .port("80")
                .build())
            .build();

        // Creates the API application load balancer
        this.apiLoadBalancer = ApplicationLoadBalancer.Builder.create(this, projectName.concat("-api-alb"))
            .loadBalancerName(projectName.concat("-api-alb"))    
            .vpc(this.vpc)
            .crossZoneEnabled(true)
            .internetFacing(true)
            .securityGroup(this.albSecurityGroup)
            .build();
        this.apiListener = this.apiLoadBalancer.addListener(projectName.concat("-api-listener"), BaseApplicationListenerProps.builder()
            .protocol(ApplicationProtocol.HTTP)
            .port(80)
            .build());

        List<IApplicationTargetGroup> apiTargetGroups = new ArrayList<IApplicationTargetGroup>();
        apiTargetGroups.add(this.apiTargetGroup);
        this.apiListener.addTargetGroups(projectName.concat("-api-tgs"), AddApplicationTargetGroupsProps.builder().targetGroups(apiTargetGroups).build()); 

        // Creates the UI application load balancer
        this.uiLoadBalancer = ApplicationLoadBalancer.Builder.create(this, projectName.concat("-ui-alb"))
            .loadBalancerName(projectName.concat("-ui-alb"))    
            .vpc(this.vpc)
            .crossZoneEnabled(true)
            .internetFacing(true)
            .securityGroup(this.albSecurityGroup)
            .build();
        this.uiListener = this.uiLoadBalancer.addListener(projectName.concat("-ui-listener"), BaseApplicationListenerProps.builder()
            .protocol(ApplicationProtocol.HTTP)
            .port(80)
            .build());

        List<IApplicationTargetGroup> uiTargetGroups = new ArrayList<IApplicationTargetGroup>();
        uiTargetGroups.add(this.uiTargetGroup);
        this.uiListener.addTargetGroups(projectName.concat("-ui-tgs"), AddApplicationTargetGroupsProps.builder().targetGroups(uiTargetGroups).build());

        // Creates the hosted zone
        HostedZoneProviderProps zoneProps = HostedZoneProviderProps.builder()
            .domainName(domainName)
            .build();
        IHostedZone hostedZone = HostedZone.fromLookup(this, projectName.concat("-hz"), zoneProps);
        CnameRecord.Builder.create(this, projectName.concat("-api-cname"))
            .recordName(apiDomain)
            .domainName(this.apiLoadBalancer.getLoadBalancerDnsName())
            .zone(hostedZone)
            .ttl(Duration.seconds(300))
            .build();
        CnameRecord.Builder.create(this, projectName.concat("-ui-cname"))
            .recordName(uiDomain)
            .domainName(this.uiLoadBalancer.getLoadBalancerDnsName())
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

    public SecurityGroup getApiInstanceSecurityGroup() {
        return this.apiInstanceSecurityGroup;
    }

    public SecurityGroup getUiInstanceSecurityGroup() {
        return this.uiInstanceSecurityGroup;
    }    

    public ApplicationTargetGroup getApiTargetGroup() {
        return this.apiTargetGroup;
    }

    public ApplicationTargetGroup getUiTargetGroup() {
        return this.uiTargetGroup;
    } 
}