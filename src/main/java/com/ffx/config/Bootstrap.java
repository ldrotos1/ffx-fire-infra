package com.ffx.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Map;

public class Bootstrap {

    private static String configFilePath;

	@SuppressWarnings("rawtypes")
    public static Map<String, Object> load(Class clazz) throws IOException {
        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation instanceof Configuration) {
                Configuration configuration = (Configuration) annotation;
                configFilePath = configuration.name();
            }
        }
        return loadProps(configFilePath);
    }

    @SuppressWarnings("unchecked")
	private static Map<String, Object> loadProps(String filePath) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = Bootstrap.class.getResourceAsStream(filePath)) {
            Iterable<Object> itr = yaml.loadAll(is);
            for (Object o : itr) {
                return (Map<String, Object>) o;
            }
        }
        return null;
    }
}