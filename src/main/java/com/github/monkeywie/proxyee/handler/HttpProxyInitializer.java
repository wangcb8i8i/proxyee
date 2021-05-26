package com.github.monkeywie.proxyee.handler;

import com.github.monkeywie.proxyee.exception.HttpProxyExceptionHandle;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipeline;
import com.github.monkeywie.proxyee.server.RequestProto;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * HTTP代理，转发解码后的HTTP报文
 */
public class HttpProxyInitializer extends ChannelInitializer {

    private Channel clientChannel;
    private RequestProto requestProto;
    private ProxyHandler proxyHandler;
    private HttpProxyExceptionHandle httpProxyExceptionHandle;
    private ProxyInterceptPipeline proxyInterceptPipeline;

    public HttpProxyInitializer(Channel clientChannel,
                                ProxyHandler proxyHandler,
                                ProxyInterceptPipeline httpProxyProxyInterceptPipeline,
                                HttpProxyExceptionHandle httpProxyExceptionHandle) {
        this.clientChannel = clientChannel;
        this.proxyHandler = proxyHandler;
        this.httpProxyExceptionHandle = httpProxyExceptionHandle;
        this.proxyInterceptPipeline = httpProxyProxyInterceptPipeline;
        this.requestProto = this.proxyInterceptPipeline.getRequestProto();
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        if (proxyHandler != null) {
            ch.pipeline().addLast(proxyHandler);
        }
        if (requestProto.getSsl()) {
            SslHandler sslHandler = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build().newHandler(ch.alloc(), requestProto.getHost(), requestProto.getPort());
            ch.pipeline().addLast(sslHandler);
        }
        ch.pipeline().addLast("httpCodec", new HttpClientCodec());
        ch.pipeline().addLast("proxyClientHandle", new HttpProxyClientHandler(clientChannel,
                proxyInterceptPipeline, httpProxyExceptionHandle));
    }
}
