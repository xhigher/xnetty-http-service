package com.cheercent.xnetty.httpservice.base;

import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cheercent.xnetty.httpservice.conf.ServiceConfig;

public class ServiceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);

    private static final int ZK_SESSION_TIMEOUT = 60000;
    
    private CountDownLatch latch = new CountDownLatch(1);

    private String registryAddress;

    public ServiceRegistry(String registryAddress) {
        this.registryAddress = registryAddress;
    }

    public void register(ServiceConfig serviceConfig) {
        if (serviceConfig != null) {
            ZooKeeper zk = connectServer();
            if (zk != null) {
            	checkRootNode(zk, serviceConfig);
                createChildNode(zk, serviceConfig);
            }
        }
    }

    private ZooKeeper connectServer() {
        ZooKeeper zk = null;
        try {
            zk = new ZooKeeper(registryAddress, ZK_SESSION_TIMEOUT, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getState() == Event.KeeperState.SyncConnected) {
                        latch.countDown();
                    }
                }
            });
            latch.await();
        } catch (Exception e) {
        	logger.error("connectServer.Exception", e);
        }
        return zk;
    }

    private void checkRootNode(ZooKeeper zk, ServiceConfig serviceConfig){
        try {
        	String rootPath = "/" + serviceConfig.getProduct();
            Stat stat = zk.exists(rootPath, false);
            if (stat == null) {
                zk.create(rootPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
            rootPath = rootPath + "/" + serviceConfig.getBusiness();
            stat = zk.exists(rootPath, false);
            if (stat == null) {
                zk.create(rootPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (Exception e) {
        	logger.error("checkRootNode.Exception", e);
        }
    }

    private void createChildNode(ZooKeeper zk, ServiceConfig serviceConfig) {
        try {
        	String nodePath = "/" + serviceConfig.getProduct() + "/" + serviceConfig.getBusiness() + "/" +serviceConfig.getHost()+":"+serviceConfig.getPort();
            byte[] bytes = serviceConfig.getConfigData().getBytes();
            Stat stat = zk.exists(nodePath, false);
            if(stat == null) {
            	zk.create(nodePath, bytes, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            }else {
            	zk.setData(nodePath, bytes, (int) (System.currentTimeMillis() / 1000 - 1500000000));
            }
        } catch (Exception e) {
        	logger.error("createChildNode.Exception", e);
        }
    }
}