package com.github.monkeywie.proxyee.intercept;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;


public interface ProxyInterceptHandler {

    /**
     *
     * @param clientChannel channel between client and proxy
     * @param httpRequest
     * @param proxyInterceptPipeline
     * @throws Exception
     */
    default void onRequest(Channel clientChannel, HttpRequest httpRequest, ProxyInterceptPipeline proxyInterceptPipeline) throws Exception {
        proxyInterceptPipeline.onRequest(clientChannel, httpRequest);
    }

    /**
     *
     * @param clientChannel channel between client and proxy
     * @param serverChannel channel between server and proxy
     * @param httpResponse
     * @param proxyInterceptPipeline
     * @throws Exception
     */
    default void onResponse(Channel clientChannel, Channel serverChannel,
                            HttpResponse httpResponse, ProxyInterceptPipeline proxyInterceptPipeline) throws Exception {
        proxyInterceptPipeline.onResponse(clientChannel, serverChannel, httpResponse);
    }

    default void onRequestContent(Channel clientChannel, HttpContent httpContent,
                                  ProxyInterceptPipeline proxyInterceptPipeline)throws Exception {
        proxyInterceptPipeline.onRequestContent(clientChannel, httpContent);
    }

    default void onResponseContent(Channel clientChannel, Channel serverChannel,
                                   HttpContent httpContent, ProxyInterceptPipeline proxyInterceptPipeline) throws Exception{
        proxyInterceptPipeline.onResponseContent(clientChannel, serverChannel, httpContent);
    }
}
