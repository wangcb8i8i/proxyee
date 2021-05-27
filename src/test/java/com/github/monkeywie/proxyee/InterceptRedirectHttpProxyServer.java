package com.github.monkeywie.proxyee;

import com.github.monkeywie.proxyee.intercept.ProxyInterceptHandler;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipeline;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipelineInitializer;
import com.github.monkeywie.proxyee.server.HttpProxyServer;
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig;
import com.github.monkeywie.proxyee.util.HttpUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;

/**
 * @Author: LiWei
 * @Description 匹配到百度首页时重定向到指定url
 * @Date: 2019/3/4 16:23
 */
public class InterceptRedirectHttpProxyServer {
    public static void main(String[] args) throws Exception {
        HttpProxyServerConfig config = new HttpProxyServerConfig();
        config.setSslSupported(true)
                .setProxyInterceptInitializer(new ProxyInterceptPipelineInitializer() {
                    @Override
                    public void init(ProxyInterceptPipeline pipeline) {
                        pipeline.addLast(new ProxyInterceptHandler() {
                            @Override
                            public void onRequest(Channel clientChannel, HttpRequest httpRequest,
                                                  ProxyInterceptPipeline pipeline) throws Exception {
                                //匹配到百度首页跳转到淘宝
                                if (HttpUtil.checkUrl(httpRequest, "^www.baidu.com$")) {
                                    HttpResponse hookResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                                    hookResponse.setStatus(HttpResponseStatus.FOUND);
                                    hookResponse.headers().set(HttpHeaderNames.LOCATION, "http://www.taobao.com");
                                    clientChannel.writeAndFlush(hookResponse);
                                    HttpContent lastContent = new DefaultLastHttpContent();
                                    clientChannel.writeAndFlush(lastContent);
                                    return;
                                }
                                pipeline.onRequest(clientChannel, httpRequest);
                            }
                        });
                    }
                })
                ;
        new HttpProxyServer()
                .serverConfig(config)
                .start(9999);
    }
}
