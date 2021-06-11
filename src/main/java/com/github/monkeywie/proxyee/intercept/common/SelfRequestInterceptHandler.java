package com.github.monkeywie.proxyee.intercept.common;

import com.github.monkeywie.proxyee.intercept.ProxyInterceptHandler;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipeline;
import com.github.monkeywie.proxyee.server.RequestProto;
import com.github.monkeywie.proxyee.util.ProtoUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;

@Slf4j
public class SelfRequestInterceptHandler implements ProxyInterceptHandler {

    public SelfRequestInterceptHandler() {
        this.configureRequestResponder(this.requestResponders);
    }

    @Override
    public void onRequest(Channel clientChannel, HttpRequest httpRequest, ProxyInterceptPipeline proxyInterceptPipeline) throws Exception {
        RequestProto requestProto = proxyInterceptPipeline.getRequestProto();
        if (ProtoUtil.isSelfRequest(clientChannel, requestProto)) {
            handleSelfRequest(clientChannel, httpRequest);
            clientChannel.close();
        } else {
            proxyInterceptPipeline.onRequest(clientChannel, httpRequest);
        }
    }

    protected void handleSelfRequest(Channel clientChannel, HttpRequest httpRequest) {
        FullHttpResponse fullHttpResponse = null;
        try {
            for (SelfRequestResponder requestResponder : this.requestResponders) {
                fullHttpResponse = requestResponder.respond(httpRequest);
                if (fullHttpResponse != null) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("failed respond {} {}", httpRequest.method().name(), httpRequest.uri(), e);
            fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
        if (fullHttpResponse == null) {
            fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
            fullHttpResponse.content().writeBytes("Forbidden".getBytes());
        }
        clientChannel.writeAndFlush(fullHttpResponse);
    }


    private List<SelfRequestResponder> requestResponders = new LinkedList<>();

    protected void configureRequestResponder(final List<SelfRequestResponder> requestResponderList) {

    }
}
