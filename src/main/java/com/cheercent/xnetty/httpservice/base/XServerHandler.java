package com.cheercent.xnetty.httpservice.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.cheercent.xnetty.httpservice.base.MessageFactory.MessageRequest;
import com.cheercent.xnetty.httpservice.base.MessageFactory.MessageResponse;
import com.cheercent.xnetty.httpservice.conf.ServiceConfig;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class XServerHandler extends SimpleChannelInboundHandler<JSONObject> {

    private static final Logger logger = LoggerFactory.getLogger(XServerHandler.class);
    
    private final ServiceConfig serviceConfig;

    public XServerHandler(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }
    
    @Override
    public void channelRead0(final ChannelHandlerContext ctx,final JSONObject data) throws Exception {
    	logger.info("channelRead0: data = {}", data.toString());
    	JSONObject responseData = handleRequest(data);
    	if(responseData != null) {
    		ctx.writeAndFlush(responseData);
    		logger.info("channelRead0: send data = {}", responseData.toString());
    	}
        
    }

    private JSONObject handleRequest(JSONObject data) {
    	MessageRequest request = MessageFactory.toMessageRequest(data);
    	if(request != null) {
    		String requestid = request.getRequestid();
    		if(!MessageFactory.isHeartBeatMessage(requestid)) {
    			int errcode = 1;
    			MessageResponse response = MessageFactory.newMessageResponse(requestid);
    			if(MessageFactory.isSuccessful(request)) {
    				response.setData(serviceConfig.runLogic(request.getModule(), request.getAction(), request.getVersion(), request.getParameters()));
        			errcode = 0;
    			}
    			response.setErrcode(errcode);
    			return response.toJSONObject();
        	}else {
        		return MessageFactory.newMessageResponse(requestid).toJSONObject();
        	}
    	}
		return null;
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("exceptionCaught", cause);
    }
}
