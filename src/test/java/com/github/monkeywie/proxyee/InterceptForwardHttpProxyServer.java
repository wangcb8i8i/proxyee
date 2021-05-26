package com.github.monkeywie.proxyee;

import com.github.monkeywie.proxyee.exception.HttpProxyExceptionHandle;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptHandler;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipeline;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipelineInitializer;
import com.github.monkeywie.proxyee.server.HttpProxyServer;
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig;
import com.github.monkeywie.proxyee.util.HttpUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;

/**
 * @Author: LiWei
 * @Description 请求转发功能实现
 * @Date: 2019/3/4 16:23
 */
public class InterceptForwardHttpProxyServer {


    // curl -k -x 127.0.0.1:9999 https://www.baidu.com

    public static void main(String[] args) throws Exception {
        HttpProxyServerConfig config = new HttpProxyServerConfig();
        config.setHandleSsl(true);
        new HttpProxyServer()
                .serverConfig(config)
                .proxyInterceptInitializer(new ProxyInterceptPipelineInitializer() {
                    @Override
                    public void init(ProxyInterceptPipeline pipeline) {
                        pipeline.addLast(new ProxyInterceptHandler() {
                            @Override
                            public void onRequest(Channel clientChannel, HttpRequest httpRequest,
                                                  ProxyInterceptPipeline pipeline) throws Exception {
                                //匹配到百度的请求转发到淘宝
                                if (HttpUtil.checkUrl(httpRequest, "^www.baidu.com$")) {
                                    pipeline.getRequestProto().setHost("www.taobao.com");
                                    pipeline.getRequestProto().setPort(443);
                                    pipeline.getRequestProto().setSsl(true);
                                }
                                pipeline.onRequest(clientChannel, httpRequest);
                            }
                        });
                    }
                })
                .httpProxyExceptionHandle(new HttpProxyExceptionHandle() {
                    @Override
                    public void beforeCatch(Channel clientChannel, Throwable cause) throws Exception {
                        cause.printStackTrace();
                    }

                    @Override
                    public void afterCatch(Channel clientChannel, Channel proxyChannel, Throwable cause)
                            throws Exception {
                        cause.printStackTrace();
                    }
                })
                .start(9999);
    }
}
