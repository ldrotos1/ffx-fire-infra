package com.ffx.stacks;

import software.constructs.Construct;

import java.util.Map;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.Bucket;

public class FfxFireAppStack extends Stack {

    public FfxFireAppStack(final Construct scope, final String id, final Map<String, Object> config) {
        this(scope, id, null, null);
    }

    public FfxFireAppStack(final Construct scope, final String id, final StackProps props, final Map<String, Object> config) {
        super(scope, id, props);

        final String projectName = config.get("project_name").toString();

        Bucket bucket = new Bucket(this, projectName.concat("-staging"));
    }
}