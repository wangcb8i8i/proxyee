package com.github.monkeywie.proxyee;

import com.github.monkeywie.proxyee.intercept.ProxyInterceptHandler;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipeline;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipelineInitializer;
import com.github.monkeywie.proxyee.intercept.common.CertDownIntercept;
import com.github.monkeywie.proxyee.server.HttpProxyServer;
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

public class NormalHttpProxyServer {

    public static void main(String[] args) throws Exception {
        //new HttpProxyServer().start(9998);

        HttpProxyServerConfig config = new HttpProxyServerConfig();
        config.setBossGroupThreads(1);
        config.setWorkerGroupThreads(1);
        config.setProxyGroupThreads(1);
        config.setSslSupported(true)
                .setProxyInterceptInitializer(new ProxyInterceptPipelineInitializer() {
                    @Override
                    public void init(ProxyInterceptPipeline proxyInterceptPipeline) {
                        proxyInterceptPipeline.addFirst(new CertDownIntercept());
                        proxyInterceptPipeline.addFirst(new ProxyInterceptHandler() {

                            @Override
                            public void onRequest(Channel clientChannel, HttpRequest httpRequest, ProxyInterceptPipeline proxyInterceptPipeline) throws Exception {
                                System.out.println("onRequest:" + httpRequest.getClass());
//                                RequestProto requestProto = proxyInterceptPipeline.getRequestProto();
//                                if (requestProto.getHost().contains("www.baidu.com")) {
//                                    requestProto.setHost("192.168.29.150");
//                                    requestProto.setPort(8891);
//                                    httpRequest.setUri("/index.html");
//                                }
                                ProxyInterceptHandler.super.onRequest(clientChannel, httpRequest, proxyInterceptPipeline);
                            }

                            @Override
                            public void onResponse(Channel clientChannel, Channel serverChannel, HttpResponse httpResponse, ProxyInterceptPipeline proxyInterceptPipeline) throws Exception {
                                System.out.println("onResponse:" + httpResponse.getClass());
//                                httpResponse.headers().set("aaaaa", "hahahaha");
                                ProxyInterceptHandler.super.onResponse(clientChannel, serverChannel, httpResponse, proxyInterceptPipeline);
                            }

                            @Override
                            public void onRequestContent(Channel clientChannel, HttpContent httpContent, ProxyInterceptPipeline proxyInterceptPipeline) throws Exception {
                                System.out.println("onRequestContent:" + httpContent.getClass());
                                ProxyInterceptHandler.super.onRequestContent(clientChannel, httpContent, proxyInterceptPipeline);
                            }

                            @Override
                            public void onResponseContent(Channel clientChannel, Channel serverChannel, HttpContent httpContent, ProxyInterceptPipeline proxyInterceptPipeline) throws Exception {
                                System.out.println("onResponseContent:" + httpContent.getClass());
                                ProxyInterceptHandler.super.onResponseContent(clientChannel, serverChannel, httpContent, proxyInterceptPipeline);
                            }
                        });
                    }
                });
        new HttpProxyServer()
                .serverConfig(config)
                .start(9999);
    }
}
