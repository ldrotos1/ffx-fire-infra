package com.ffx.stacks;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.constructs.Construct;

public class FfxFireStagingStack extends Stack {
    
    private final Bucket stagingBucket;
    private final Role assetsAccessRole;

    public FfxFireStagingStack(final Construct scope, final String id, final Map<String, Object> config) {
        this(scope, id, null, null);
    }

    public FfxFireStagingStack(final Construct scope, final String id, final StackProps props, final Map<String, Object> config) {
        super(scope, id, props);

        final String projectName = config.get("project_name").toString();
        
        // Creates the staging bucket for storing artifacts that will be deployed
        this.stagingBucket = Bucket.Builder.create(this, projectName.concat("-staging"))
            .bucketName(projectName.concat("-staging"))
            .removalPolicy(RemovalPolicy.DESTROY)
            .autoDeleteObjects(true)
            .build(); 
        BucketDeployment.Builder.create(this, "deploy-service-assets")
            .sources(List.of(Source.asset("./assets")))
            .destinationBucket(this.stagingBucket)
            .build();

        // Creates the IAM role that will give read access to the staging bucket 
        this.assetsAccessRole = Role.Builder.create(this, projectName.concat("-assets-access-role"))
            .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
            .description("Provides access to staging bucket")
            .roleName(projectName.concat("-assets-access-role"))
            .build();
        this.assetsAccessRole.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AmazonS3ReadOnlyAccess"));
    }

    public Role getAssetsAccessRole() {
        return this.assetsAccessRole;
    }

    public String getStagingBucketName() {
        return this.stagingBucket.getBucketName();
    }
}
