package com.github.monkeywie.proxyee.handler;

import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipeline;
import com.github.monkeywie.proxyee.proxy.ProxyHandleFactory;
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig;
import com.github.monkeywie.proxyee.server.RequestProto;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslHandler;

import java.util.Optional;

/**
 * HTTP代理，转发解码后的HTTP报文
 */
public class HttpProxyInitializer extends ChannelInitializer {

    private Channel clientChannel;
    private RequestProto requestProto;
    private ProxyInterceptPipeline proxyInterceptPipeline;
    private HttpProxyServerConfig httpProxyServerConfig;

    public HttpProxyInitializer(Channel clientChannel,
                                HttpProxyServerConfig httpProxyServerConfig,
                                ProxyInterceptPipeline httpProxyProxyInterceptPipeline) {
        this.clientChannel = clientChannel;
        this.httpProxyServerConfig = httpProxyServerConfig;
        this.proxyInterceptPipeline = httpProxyProxyInterceptPipeline;
        this.requestProto = this.proxyInterceptPipeline.getRequestProto();
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        Optional.ofNullable(httpProxyServerConfig.getProxyConfig()).map(ProxyHandleFactory::build)
                .ifPresent(ch.pipeline()::addLast);
        if (requestProto.getSsl()) {
            SslHandler sslHandler = httpProxyServerConfig.getClientSslCtx().newHandler(ch.alloc(),
                    requestProto.getHost(), requestProto.getPort());
            ch.pipeline().addLast(sslHandler);
        }
        ch.pipeline().addLast("httpCodec", new HttpClientCodec());
        ch.pipeline().addLast("aggregator", new HttpObjectAggregator(1024 * 1024 * 10));
        ch.pipeline().addLast("proxyClientHandle", new HttpProxyClientHandler(clientChannel,
                proxyInterceptPipeline, httpProxyServerConfig.getHttpProxyExceptionHandle()));
    }
}
