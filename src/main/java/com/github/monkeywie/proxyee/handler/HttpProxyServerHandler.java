package com.github.monkeywie.proxyee.handler;

import com.github.monkeywie.proxyee.crt.CertPool;
import com.github.monkeywie.proxyee.exception.HttpProxyExceptionHandle;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptHandler;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipeline;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipelineInitializer;
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig;
import com.github.monkeywie.proxyee.server.RequestProto;
import com.github.monkeywie.proxyee.server.auth.HttpProxyAuthenticationProvider;
import com.github.monkeywie.proxyee.util.ProtoUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

@Slf4j
public class HttpProxyServerHandler extends ChannelInboundHandlerAdapter {


    private static final int STATUS_CONNECTION_NONE = 0;
    private static final int STATUS_CONNECTION_LIVE = 1;
    private static final int STATUS_CONNECTION_OVER = 2;

    private ChannelFuture cf;
    private String host;
    private int port;
    private boolean isSsl = false;
    private int status = 0;
    private final HttpProxyServerConfig httpProxyServerConfig;
    private final HttpProxyExceptionHandle httpProxyExceptionHandle;
    private List requestList;
    private boolean isConnect;

    private ProxyInterceptPipeline proxyInterceptPipeline;


    public HttpProxyServerHandler(HttpProxyServerConfig httpProxyServerConfig) {
        this.httpProxyServerConfig = httpProxyServerConfig;
        this.httpProxyExceptionHandle = httpProxyServerConfig.getHttpProxyExceptionHandle();
        initInterceptPipeline(httpProxyServerConfig.getProxyInterceptInitializer());
    }

    private void initInterceptPipeline(ProxyInterceptPipelineInitializer interceptInitializer) {
        proxyInterceptPipeline = new ProxyInterceptPipeline(new ProxyInterceptHandler() {
        }, new ProxyInterceptHandler() {

            @Override
            public void onRequest(Channel clientChannel, HttpRequest httpRequest, ProxyInterceptPipeline proxyInterceptPipeline) {
                if (ProtoUtil.isSelfRequest(clientChannel, proxyInterceptPipeline.getRequestProto())) {
                    clientChannel.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN))
                            .addListener(future -> {
                                if (future.isDone()) {
                                    clientChannel.close();
                                }
                            });
                    return;
                }
                if (httpRequest instanceof FullHttpRequest) {
                    log.debug("request |{} -> {}| {} {} {} | body {} bytes", clientChannel.remoteAddress(), proxyInterceptPipeline.getRequestProto(),
                            httpRequest.protocolVersion().text(), httpRequest.method().name(), httpRequest.uri(),
                            ((FullHttpRequest) httpRequest).content().readableBytes());
                } else {
                    log.debug("request |{} -> {}| {} {} {}", clientChannel.remoteAddress(), proxyInterceptPipeline.getRequestProto(),
                            httpRequest.protocolVersion().text(), httpRequest.method().name(), httpRequest.uri());
                }
                handleProxyData(clientChannel, httpRequest, true);
            }

            @Override
            public void onRequestContent(Channel clientChannel, HttpContent httpContent, ProxyInterceptPipeline proxyInterceptPipeline) {
                log.debug("request body |{} -> {}| {} bytes", clientChannel.remoteAddress(), proxyInterceptPipeline.getRequestProto(),
                        httpContent.content().readableBytes());
                handleProxyData(clientChannel, httpContent, true);
            }

            @Override
            public void onResponse(Channel clientChannel, Channel serverChannel, HttpResponse httpResponse, ProxyInterceptPipeline proxyInterceptPipeline) {
                if (httpResponse instanceof FullHttpResponse) {
                    log.debug("response |{} -> {}| {} {} | body {} bytes", proxyInterceptPipeline.getRequestProto(), clientChannel.remoteAddress(),
                            httpResponse.protocolVersion().text(), httpResponse.status().toString(),
                            ((FullHttpResponse) httpResponse).content().readableBytes());
                } else {
                    log.debug("response |{} -> {}| {} {}", proxyInterceptPipeline.getRequestProto(), clientChannel.remoteAddress(),
                            httpResponse.protocolVersion().text(), httpResponse.status().toString());
                }
                clientChannel.writeAndFlush(httpResponse);
                if (HttpHeaderValues.WEBSOCKET.toString().equals(httpResponse.headers().get(HttpHeaderNames.UPGRADE))) {
                    // websocket转发原始报文
                    serverChannel.pipeline().remove("httpCodec");
                    clientChannel.pipeline().remove("httpCodec");
                }
            }

            @Override
            public void onResponseContent(Channel clientChannel, Channel serverChannel, HttpContent httpContent, ProxyInterceptPipeline proxyInterceptPipeline) {
                log.debug("response |{} -> {}| {} bytes", proxyInterceptPipeline.getRequestProto(), clientChannel.remoteAddress(),
                        httpContent.content().readableBytes());
                clientChannel.writeAndFlush(httpContent);
            }
        });
        interceptInitializer.init(proxyInterceptPipeline);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        log.trace("received msg:{}", msg);
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            // 第一次建立连接取host和端口号和处理代理握手
            if (status == STATUS_CONNECTION_NONE) {
                RequestProto requestProto = ProtoUtil.getRequestProto(request);
                if (requestProto == null) { // bad request
                    ctx.channel().close();
                    return;
                }
                // 首次连接处理
                if (httpProxyServerConfig.getHttpProxyAcceptHandler() != null
                        && !httpProxyServerConfig.getHttpProxyAcceptHandler().onAccept(request, ctx.channel())) {
                    status = STATUS_CONNECTION_OVER;
                    ctx.channel().close();
                    return;
                }
                // 代理身份验证
                if (!authenticate(ctx, request)) {
                    status = STATUS_CONNECTION_OVER;
                    ctx.channel().close();
                    return;
                }
                status = STATUS_CONNECTION_LIVE;
                this.host = requestProto.getHost();
                this.port = requestProto.getPort();
                if ("CONNECT".equalsIgnoreCase(request.method().name())) {// 建立代理握手
                    status = STATUS_CONNECTION_OVER;
                    HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    ctx.writeAndFlush(response);
                    ctx.channel().pipeline().remove("httpCodec");
                    // fix issue #42
                    ReferenceCountUtil.release(msg);
                    return;
                }
            }
            proxyInterceptPipeline.setRequestProto(new RequestProto(host, port, isSsl));
            // fix issue #27
            if (request.uri().indexOf("/") != 0) {
                URL url = new URL(request.uri());
                request.setUri(url.getFile());
            }
            proxyInterceptPipeline.onRequest(ctx.channel(), request);
        } else if (msg instanceof HttpContent) {
            if (status != STATUS_CONNECTION_OVER) {
                proxyInterceptPipeline.onRequestContent(ctx.channel(), (HttpContent) msg);
            } else {
                ReferenceCountUtil.release(msg);
                status = STATUS_CONNECTION_LIVE;
            }
        } else { // ssl和websocket的握手处理
            if (httpProxyServerConfig.isSslSupported()) {
                ByteBuf byteBuf = (ByteBuf) msg;
                if (byteBuf.getByte(0) == 22) {// ssl握手
                    isSsl = true;
                    int port = ((InetSocketAddress) ctx.channel().localAddress()).getPort();
                    SslContext sslCtx = SslContextBuilder
                            .forServer(httpProxyServerConfig.getServerPriKey(), CertPool.getCert(port, this.host, httpProxyServerConfig)).build();
                    ctx.pipeline().addFirst("httpCodec", new HttpServerCodec());
                    ctx.pipeline().addFirst("sslHandle", sslCtx.newHandler(ctx.alloc()));
                    // 重新过一遍pipeline，拿到解密后的的http报文
                    ctx.pipeline().fireChannelRead(msg);
                    return;
                }
            }
            handleProxyData(ctx.channel(), msg, false);
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (cf != null) {
            cf.channel().close();
        }
        ctx.channel().close();
        if (httpProxyServerConfig.getHttpProxyAcceptHandler() != null) {
            httpProxyServerConfig.getHttpProxyAcceptHandler().onClose(ctx.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cf != null) {
            cf.channel().close();
        }
        ctx.channel().close();
        httpProxyExceptionHandle.frontendFailed(ctx.channel(), cause);
    }

    private boolean authenticate(ChannelHandlerContext ctx, HttpRequest request) {
        HttpProxyAuthenticationProvider authProvider = httpProxyServerConfig.getHttpProxyAuthenticationProvider();
        if (authProvider != null) {
            if (!authProvider.authenticate(request.headers().get(HttpHeaderNames.PROXY_AUTHORIZATION))) {
                HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED);
                response.headers().set(HttpHeaderNames.PROXY_AUTHENTICATE, authProvider.authType() + " realm=\"" + authProvider.authRealm() + "\"");
                ctx.writeAndFlush(response);
                return false;
            }
        }
        return true;
    }

    private void handleProxyData(Channel channel, Object msg, boolean isHttp) {
        if (cf == null) {
            // connection异常 还有HttpContent进来，不转发
            if (isHttp && !(msg instanceof HttpRequest)) {
                return;
            }
            /*
             * 添加SSL client hello的Server Name Indication extension(SNI扩展) 有些服务器对于client
             * hello不带SNI扩展时会直接返回Received fatal alert: handshake_failure(握手错误)
             * 例如：https://cdn.mdn.mozilla.net/static/img/favicon32.7f3da72dcea1.png
             */
            RequestProto requestProto = new RequestProto(host, port, isSsl);
            if (!isHttp) {
                if (httpProxyServerConfig.getTunnelIntercept() != null) {
                    httpProxyServerConfig.getTunnelIntercept().handle(requestProto);
                }
            } else {
                requestProto = proxyInterceptPipeline.getRequestProto();
                HttpRequest httpRequest = (HttpRequest) msg;
                // 更新Host请求头(请求头有可能在拦截过程中被修改)
                if (requestProto.isDefaultPort()) {
                    httpRequest.headers().set(HttpHeaderNames.HOST, requestProto.getHost());
                } else {
                    httpRequest.headers().set(HttpHeaderNames.HOST, requestProto.getHost() + ":" + requestProto.getPort());
                }

            }
            ChannelInitializer channelInitializer = isHttp
                    ? new HttpProxyInitializer(channel, httpProxyServerConfig, proxyInterceptPipeline)
                    : new TunnelProxyInitializer(channel, httpProxyServerConfig);
            Bootstrap bootstrap = new Bootstrap();
            EventLoopGroup proxyEventGroup = new NioEventLoopGroup(httpProxyServerConfig.getProxyGroupThreads());
            channel.eventLoop().terminationFuture().addListener(f -> {
                if (f.isDone()) {
                    proxyEventGroup.shutdownGracefully();
                }
            });
            bootstrap.group(proxyEventGroup) // 注册线程池
                    .channel(NioSocketChannel.class) // 使用NioSocketChannel来作为连接用的channel类
                    .handler(channelInitializer);

            if (httpProxyServerConfig.getProxyConfig() != null) {
                //如果设置了代理，则交给代理服务器解析
                bootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
            } else {
                bootstrap.resolver(httpProxyServerConfig.getNameResolver());
            }
            requestList = new LinkedList();
            cf = bootstrap.connect(requestProto.getHost(), requestProto.getPort());
            cf.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    future.channel().writeAndFlush(msg);
                    synchronized (requestList) {
                        requestList.forEach(obj -> future.channel().writeAndFlush(obj));
                        requestList.clear();
                        isConnect = true;
                    }
                } else {
                    requestList.forEach(obj -> ReferenceCountUtil.release(obj));
                    requestList.clear();
                    httpProxyExceptionHandle.frontendFailed(channel, future.cause());
                    future.channel().close();
                    channel.close();
                }
            });
        } else {
            synchronized (requestList) {
                if (isConnect) {
                    cf.channel().writeAndFlush(msg);
                } else {
                    requestList.add(msg);
                }
            }
        }
    }
}
