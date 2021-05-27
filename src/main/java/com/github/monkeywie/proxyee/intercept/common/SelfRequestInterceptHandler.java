package com.github.monkeywie.proxyee.intercept.common;

import com.github.monkeywie.proxyee.intercept.ProxyInterceptHandler;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipeline;
import com.github.monkeywie.proxyee.server.RequestProto;
import com.github.monkeywie.proxyee.util.ProtoUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SelfRequestInterceptHandler implements ProxyInterceptHandler {

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

    protected void handleSelfRequest(Channel clientChannel, HttpRequest httpRequest) throws Exception {
        FullHttpResponse fullHttpResponse = null;
        for (SelfRequestResponder requestResponder : this.requestResponders) {
            fullHttpResponse = requestResponder.respond(httpRequest);
            if (fullHttpResponse != null) {
                break;
            }
        }
        if (fullHttpResponse == null) {
            fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            fullHttpResponse.content().writeBytes("not found".getBytes());
        }
        clientChannel.writeAndFlush(fullHttpResponse);
    }


    private List<SelfRequestResponder> requestResponders = new CopyOnWriteArrayList<>();

    public SelfRequestInterceptHandler addRequestResponder(SelfRequestResponder requestResponder) {
        this.requestResponders.add(requestResponder);
        return this;
    }

}
