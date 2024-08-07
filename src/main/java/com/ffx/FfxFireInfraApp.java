package com.ffx;

import java.io.IOException;
import java.util.Map;

import com.ffx.config.Bootstrap;
import com.ffx.config.Configuration;
import com.ffx.stacks.FfxFireNetworkStack;
import com.ffx.stacks.FfxFireApiStack;
import com.ffx.stacks.FfxFireUiStack;
import com.ffx.stacks.FfxFireStagingStack;
import com.ffx.stacks.FfxFireDataStoreStack;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

@Configuration
public class FfxFireInfraApp {
	
	@SuppressWarnings("unused")
	public static void main(final String[] args) {
		
		final FfxFireNetworkStack ffxFireNetworkStack;
		final FfxFireDataStoreStack ffxFireDataStoreStack;
		final FfxFireStagingStack ffxFireStagingStack;
		final FfxFireApiStack ffxFireApiStack;
		final FfxFireUiStack ffxFireUiStack;

		try {
			App app = new App();
			
			Map<String, Object> configMap = Bootstrap.load(FfxFireInfraApp.class);
			StackProps props = StackProps.builder()
				.env(Environment.builder()
					.account(configMap.get("aws_account_number").toString())
					.region(configMap.get("aws_region").toString())
					.build())
				.build();

			ffxFireNetworkStack = new FfxFireNetworkStack(app, "FfxFireNetworkStack", props, configMap);
			ffxFireDataStoreStack = new FfxFireDataStoreStack(app, "FfxFireDataStoreStack", props, configMap, ffxFireNetworkStack);
			ffxFireStagingStack = new FfxFireStagingStack(app,  "FfxFireStagingStack", props, configMap);
			ffxFireApiStack = new FfxFireApiStack(app, "FfxFireApiStack", props, configMap, ffxFireNetworkStack, ffxFireStagingStack);
			ffxFireUiStack = new FfxFireUiStack(app, "FfxFireUiStack", props, configMap, ffxFireNetworkStack, ffxFireStagingStack);

      app.synth();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
  }
}

