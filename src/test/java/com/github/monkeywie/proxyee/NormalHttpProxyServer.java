package com.github.monkeywie.proxyee;

import com.github.monkeywie.proxyee.intercept.ProxyInterceptHandler;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipeline;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipelineInitializer;
import com.github.monkeywie.proxyee.intercept.common.CertDownloadResponder;
import com.github.monkeywie.proxyee.intercept.common.SelfRequestInterceptHandler;
import com.github.monkeywie.proxyee.server.HttpProxyServer;
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;

public class NormalHttpProxyServer {

    public static void main(String[] args) throws Exception {
        HttpProxyServerConfig config = new HttpProxyServerConfig()
                .setBossGroupThreads(1)
                .setWorkerGroupThreads(1)
                .setProxyGroupThreads(1)
                .setSslSupported(true)
                .setProxyInterceptInitializer(new ProxyInterceptPipelineInitializer() {
                    @Override
                    public void init(ProxyInterceptPipeline proxyInterceptPipeline) {
                        proxyInterceptPipeline.addLast(new SelfRequestInterceptHandler()
                                .addRequestResponder(new CertDownloadResponder())
                                .addRequestResponder(r -> {
                                    if (r.uri().matches("asdf")) {

                                        return null;
                                    }
                                    return null;
                                })

                        )

                                .addFirst(new ProxyInterceptHandler() {

                                    @Override
                                    public void onRequest(Channel clientChannel, HttpRequest httpRequest, ProxyInterceptPipeline proxyInterceptPipeline) throws Exception {
                                        System.out.println(httpRequest.uri() + " : " + httpRequest.getClass());
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
