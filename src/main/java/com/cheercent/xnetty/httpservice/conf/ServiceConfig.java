package com.cheercent.xnetty.httpservice.conf;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cheercent.xnetty.httpservice.base.XLogic;
import com.cheercent.xnetty.httpservice.base.XLogicConfig;

public class ServiceConfig {

	private static Logger logger = LoggerFactory.getLogger(ServiceConfig.class);
	
	public final String product;
	public final String business;
	public final String host;
	public final int port;
	
	private static final int statisticDelayTime = 10 * 60;
	private static final ScheduledExecutorService statisticExecutor = Executors.newScheduledThreadPool(1); 
	
	private final static Map<String, Map<String, XLogicConfig>> LOGIC_CONFIGS = new HashMap<String, Map<String, XLogicConfig>>();
	private final static Map<String, Map<String, XLogic>> LOGIC_SOURCES = new HashMap<String, Map<String, XLogic>>();
	
	private static final String PACKAGE_LOGIC = "com.cheercent.xnetty.httpservice.logic";
	private static final String[] MODULE_LIST = {
			"user",
	};
	
	public enum ActionMethod {
		GET, POST
	}


    public ServiceConfig(String product, String business, String host, int port){
    	this.product = product;
    	this.business = business;
    	this.host = host;
    	this.port = port;
    	
    	for(int i=0; i<MODULE_LIST.length; i++){
			initModule(MODULE_LIST[i]);
		}
    	
    	statisticExecutor.scheduleWithFixedDelay(new Runnable(){
			@Override
			public void run() {
				JSONObject statisticData = new JSONObject();
				statisticData.put("product", ServiceConfig.this.product);
				statisticData.put("business", ServiceConfig.this.business);
				statisticData.put("host", ServiceConfig.this.host);
				statisticData.put("port", ServiceConfig.this.port);
				Map<String, XLogic> actionList = null;
				for(String module : LOGIC_SOURCES.keySet()) {
					actionList = LOGIC_SOURCES.get(module);
					for(String action : actionList.keySet()) {
						statisticData.put(module+"#"+action, actionList.get(action).getCloneCount());
					}
				}
				logger.info("ServiceStatisticData="+statisticData.toJSONString());
			}
		}, statisticDelayTime, statisticDelayTime, TimeUnit.SECONDS);
    }
    
    public String getProduct(){
    	return this.product;
    }
    
    public String getBusiness(){
    	return this.business;
    }

    public String getHost(){
    	return this.host;
    }
    
    public int getPort(){
    	return this.port;
    }
    
	public String getConfigData(){
		Map<String, XLogicConfig> configList = null;
		XLogicConfig logicConfig = null;
		JSONObject itemData = null;
		JSONArray listData = new JSONArray();
		for(String module : LOGIC_CONFIGS.keySet()){
			configList = LOGIC_CONFIGS.get(module);
			for (String action : configList.keySet()) {
				logicConfig = configList.get(action);
				itemData = new JSONObject();
				itemData.put("module", module);
				itemData.put("action", logicConfig.name());
				itemData.put("version", logicConfig.version());
				itemData.put("method", logicConfig.method().toString());
				itemData.put("requiredPeerid", logicConfig.requiredPeerid());
				itemData.put("requiredParameters", logicConfig.requiredParameters());
				itemData.put("allow", logicConfig.allow());
				listData.add(itemData);
			}
		}
		JSONObject configData = new JSONObject();
		configData.put("host", this.host);
		configData.put("port", this.port);
		configData.put("services", listData);
		System.out.println(configData.toJSONString());
		return configData.toJSONString();
	}

	public XLogicConfig getLogicConfig(String module, String action) {
		if(LOGIC_CONFIGS.containsKey(module)){
			return LOGIC_CONFIGS.get(module).get(action);
		}
		return null;
	}
	
	public String runLogic(String module, String action, int version, JSONObject parameters){
		try{
			if(LOGIC_SOURCES.containsKey(module)){
				XLogic logic = LOGIC_SOURCES.get(module).get(getVersionAction(action, version));
				if(logic != null){
					logic = (XLogic) logic.clone();
					return logic.handle(parameters);
				}
			}
		} catch(Exception e){
			logger.error("ServiceConfig.runLogic.exception:module="+module+", action="+action+", parameters="+parameters.toString());
		}finally{
			
		}
		return XLogic.errorRequestResult();
	}
	
	private static String getVersionAction(String action, int version) {
		return action + "@" + version;
	}
	
	private static String getVersionAction(XLogicConfig logicConfig) {
		return logicConfig.name() + "@" + logicConfig.version();
	}
	
	public static void initModule(String module){
		File file = null;
		List<String> classFiles = null;
		Class<?> clazz = null;
		XLogicConfig logicConfig = null;
		
		Map<String, XLogicConfig> configList = new HashMap<String, XLogicConfig>();
		Map<String, XLogic> sourceList = new HashMap<String, XLogic>();
		
		String modulePackage = PACKAGE_LOGIC + "." + module;
		String modulePath = modulePackage.replace(".", "/");
		String versionAction = null;
		try{
			URL url = null;
			JarURLConnection jarConnection = null;
			JarFile jarFile = null;
			Enumeration<JarEntry> jarEntryEnumeration = null;
			String jarEntryName = null;
			String fullClazz = null;
			Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources(modulePath);
			while (urls.hasMoreElements()) {
				url = urls.nextElement();
				if ("jar".equalsIgnoreCase(url.getProtocol())) {
					jarConnection = (JarURLConnection) url.openConnection();
					if (jarConnection != null) {
						jarFile = jarConnection.getJarFile();
						if (jarFile != null) {
							jarEntryEnumeration = jarFile.entries();
							while (jarEntryEnumeration.hasMoreElements()) {
								jarEntryName = jarEntryEnumeration.nextElement().getName();
								if (jarEntryName.contains(".class") && jarEntryName.replace("/",".").startsWith(modulePackage)) {
									fullClazz = jarEntryName.substring(0, jarEntryName.lastIndexOf(".")).replace("/", ".");
									clazz = Class.forName(fullClazz);
									logicConfig = clazz.getAnnotation(XLogicConfig.class);
									if(logicConfig != null){
										versionAction = getVersionAction(logicConfig);
										configList.put(versionAction, logicConfig);
										sourceList.put(versionAction, (XLogic)clazz.newInstance());
									}
								}
							}
						}
					}
				}else{
					file = new File(url.toURI());
					if (file != null) {
						classFiles = new ArrayList<String>();
						listClassFiles(file, classFiles);
						for (String clz : classFiles) {
							fullClazz = clz.replaceAll("[/\\\\]", ".");
							fullClazz = fullClazz.substring(fullClazz.indexOf(modulePackage), clz.length() - 6);
							clazz = Class.forName(fullClazz);
							logicConfig = clazz.getAnnotation(XLogicConfig.class);
							if (logicConfig != null) {
								versionAction = getVersionAction(logicConfig);
								configList.put(versionAction, logicConfig);
								sourceList.put(versionAction, (XLogic) clazz.newInstance());
							}
						}
					}
				}
			}
		}catch(Exception e){
			logger.error("initModule.Exception", e);
		}
		LOGIC_CONFIGS.put(module, configList);
		LOGIC_SOURCES.put(module, sourceList);
	}

	private static void listClassFiles(File file, List<String> classFiles){
		File tf = null;
		File[] files = file.listFiles();
		for(int i=0; i<files.length; i++){
			tf = files[i];
			if(tf.isDirectory()){
				listClassFiles(tf, classFiles);
			}else if(tf.isFile() && tf.getName().endsWith(".class")){
				classFiles.add(tf.getAbsolutePath());
			}
		}
	}

}
