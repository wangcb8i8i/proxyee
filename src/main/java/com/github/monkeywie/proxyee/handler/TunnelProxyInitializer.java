package com.github.monkeywie.proxyee.handler;

import com.github.monkeywie.proxyee.proxy.ProxyHandleFactory;
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;

import java.util.Optional;

/**
 * http代理隧道，转发原始报文
 */
public class TunnelProxyInitializer extends ChannelInitializer {

    private Channel clientChannel;
    private HttpProxyServerConfig httpProxyServerConfig;
    public TunnelProxyInitializer(Channel clientChannel,HttpProxyServerConfig httpProxyServerConfig) {
        this.clientChannel = clientChannel;
        this.httpProxyServerConfig = httpProxyServerConfig;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        Optional.ofNullable(httpProxyServerConfig.getProxyConfig()).map(ProxyHandleFactory::build)
                .ifPresent(ch.pipeline()::addLast);
        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx0, Object msg0) throws Exception {
                clientChannel.writeAndFlush(msg0);
            }

            @Override
            public void channelUnregistered(ChannelHandlerContext ctx0) throws Exception {
                ctx0.channel().close();
                clientChannel.close();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx0, Throwable cause) throws Exception {
                ctx0.channel().close();
                clientChannel.close();
                httpProxyServerConfig.getHttpProxyExceptionHandle().backendFailed(clientChannel, ctx0.channel(), cause);
            }
        });
    }
}
