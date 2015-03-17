package com.emc.mongoose.common.conf;
//
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
/**
 Created by kurila on 17.03.15.
 */
public interface Constants {
	String DEFAULT_ENC = StandardCharsets.UTF_8.name();
	String DIR_ROOT = Paths.get("").toAbsolutePath().normalize().toString();
	String DIR_CONF = "conf";
	String DIR_PROPERTIES = "properties";
	String DOT = ".";
	String EMPTY = "";
	String FNAME_POLICY = "security.policy";
	String KEY_DIR_ROOT = "dir.root";
	String KEY_POLICY = "java.security.policy";
	String RUN_MODE_STANDALONE = "standalone";
	String RUN_MODE_CLIENT = "client";
	String RUN_MODE_COMPAT_CLIENT = "controller";
	String RUN_MODE_SERVER = "server";
	String RUN_MODE_COMPAT_SERVER = "driver";
	String RUN_MODE_WEBUI = "webui";
	String RUN_MODE_CINDERELLA = "cinderella";
	//
	String DIR_WEBAPP = "webapp";
	String DIR_WEBINF = "WEB-INF";
}
