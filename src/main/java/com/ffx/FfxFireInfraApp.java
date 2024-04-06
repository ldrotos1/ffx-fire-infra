package com.ffx;

import java.io.IOException;
import java.util.Map;

import com.ffx.config.Bootstrap;
import com.ffx.config.Configuration;
import com.ffx.stacks.FfxFireNetworkStack;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

@Configuration
public class FfxFireInfraApp {
	
	public static void main(final String[] args) {
		try {
			App app = new App();
			
			Map<String, Object> configMap = Bootstrap.load(FfxFireInfraApp.class);
			StackProps props = StackProps.builder()
				.env(Environment.builder()
					.account(configMap.get("aws_account_number").toString())
					.region(configMap.get("aws_region").toString())
					.build())
				.build();

			new FfxFireNetworkStack(app, "FfxFireNetworkStack", props, configMap);

        	app.synth();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
    }
}

