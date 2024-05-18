package com.ffx;

import java.io.IOException;
import java.util.Map;

import com.ffx.config.Bootstrap;
import com.ffx.config.Configuration;
import com.ffx.stacks.FfxFireNetworkStack;
import com.ffx.stacks.FfxFireAppStack;
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
		final FfxFireAppStack ffxFireAppStack;

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
			ffxFireAppStack = new FfxFireAppStack(app, "FfxFireAppStack", props, configMap, ffxFireNetworkStack);

      app.synth();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
  }
}

