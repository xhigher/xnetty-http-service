package com.cheercent.xnetty.httpservice.base;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cheercent.xnetty.httpservice.conf.PublicConfig;
import com.cheercent.xnetty.httpservice.conf.ServiceConfig;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/*
 * @copyright (c) xhigher 2015 
 * @author xhigher    2015-3-26 
 */
public final class XServer {

	private static Logger logger = LoggerFactory.getLogger(XServer.class);
	
	private final String defaultHost = "0.0.0.0";
	private final int defaultPort;
	
    private final ServiceConfig serviceConfig;
    private final ServiceRegistry serviceRegistry;

    private EventLoopGroup bossGroup = null;
    private EventLoopGroup workerGroup = null;
	protected ServerBootstrap bootstrap = null;
	
    private int soRcvbuf = 1024 * 128;
    private int soSndbuf = 1024 * 128;
    
	public XServer(Properties properties) {
		defaultPort = Integer.parseInt(properties.getProperty("service.server.port").trim());
		
		serviceConfig = new ServiceConfig(properties.getProperty("service.product").trim(),
				properties.getProperty("service.business").trim(),
				properties.getProperty("service.server.host").trim(), defaultPort);
	
        serviceRegistry = new ServiceRegistry(properties.getProperty("zookeeper.server.list").trim());
        
        XMySQL.init(properties);
		XRedis.init(properties);
        PublicConfig.init(properties);

	}

	public void start() {
		bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(10);
		try {
			bootstrap = new ServerBootstrap();
			bootstrap.group(bossGroup, workerGroup);
			bootstrap.channel(NioServerSocketChannel.class);
			bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel channel) {
					ChannelPipeline pipeline = channel.pipeline();
					pipeline.addLast(new LengthFieldBasedFrameDecoder(1024*1024, 0, 4, 0, 0));
					pipeline.addLast(new MessageDecoder());
					pipeline.addLast(new MessageEncoder());
					pipeline.addLast(new XServerHandler(serviceConfig));
				}
			});
			bootstrap.option(ChannelOption.SO_BACKLOG, 128);
			bootstrap.option(ChannelOption.SO_RCVBUF, soRcvbuf);
			bootstrap.option(ChannelOption.SO_SNDBUF, soSndbuf);
			bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);

	        ChannelFuture future = bootstrap.bind(defaultHost, defaultPort).sync();

	        if (serviceRegistry != null) {
	            serviceRegistry.register(serviceConfig);
	        }

	        future.channel().closeFuture().sync();
		}catch (Exception e){
			logger.error("XServer.start.Exception",e);
		} finally {
			stop();
		}
	}

	public void stop() {
		XRedis.close();
		if(bossGroup!=null){
			bossGroup.shutdownGracefully();
		}
		if(workerGroup != null){
			workerGroup.shutdownGracefully();
		}
	}

}
