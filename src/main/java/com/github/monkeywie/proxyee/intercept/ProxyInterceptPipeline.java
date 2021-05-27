package com.github.monkeywie.proxyee.intercept;

import com.github.monkeywie.proxyee.server.RequestProto;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;


@Slf4j
public final class ProxyInterceptPipeline {

    private List<HandlerEntry> interceptHandlerList = Collections.synchronizedList(new LinkedList<>());

    private Lock lock = new ReentrantLock();

    private final class HandlerEntry {
        private final String name;
        private final ProxyInterceptHandler handler;

        public HandlerEntry(String name, ProxyInterceptHandler proxyInterceptHandler) {
            this.name = Objects.requireNonNull(name, "handlerName");
            this.handler = Objects.requireNonNull(proxyInterceptHandler, "interceptHandler");
        }

        public HandlerEntry(ProxyInterceptHandler proxyInterceptHandler) {
            this(proxyInterceptHandler.getClass().getName(), proxyInterceptHandler);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public ProxyInterceptPipeline(ProxyInterceptHandler headProxyInterceptHandler, ProxyInterceptHandler tailProxyInterceptHandler) {
        this.interceptHandlerList.add(new HandlerEntry("head", Objects.requireNonNull(headProxyInterceptHandler)));
        this.interceptHandlerList.add(new HandlerEntry("tail", Objects.requireNonNull(tailProxyInterceptHandler)));
    }

    public ProxyInterceptPipeline() {
        ProxyInterceptHandler head = new ProxyInterceptHandler() {
        };
        ProxyInterceptHandler tail = new ProxyInterceptHandler() {
        };
        this.interceptHandlerList.add(new HandlerEntry("head", head)); //add head
        this.interceptHandlerList.add(new HandlerEntry("tail", tail)); //add tail
    }

    private void syncRun(Runnable runnable) {
        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    public ProxyInterceptPipeline addFirst(ProxyInterceptHandler... handlers) {
        syncRun(() -> {
            for (ProxyInterceptHandler handler : handlers) {
                this.interceptHandlerList.add(1, new HandlerEntry(handler));
            }
        });
        return this;
    }

    public ProxyInterceptPipeline addFirst(String name, ProxyInterceptHandler handler) {
        syncRun(() -> {
            this.interceptHandlerList.add(1, new HandlerEntry(name, handler));
        });
        return this;
    }

    public ProxyInterceptPipeline addLast(ProxyInterceptHandler... handlers) {
        syncRun(() -> {
            for (ProxyInterceptHandler handler : handlers) {
                this.interceptHandlerList.add(this.interceptHandlerList.size() - 1, new HandlerEntry(handler));
            }
        });
        return this;
    }

    public ProxyInterceptPipeline replace(String base, String name, ProxyInterceptHandler handler) {
        syncRun(() -> {
            int k = index(e -> e.name.equals(base));
            if (k > 0) {
                this.interceptHandlerList.remove(k);
                this.interceptHandlerList.add(k, new HandlerEntry(name, handler));
            } else {
                notfound(base);
            }
        });
        return this;
    }

    public ProxyInterceptPipeline replace(ProxyInterceptHandler oldHandler, String name, ProxyInterceptHandler newHandler) {
        syncRun(() -> {
            int k = index(e -> e.handler.equals(oldHandler));
            if (k > 0) {
                this.interceptHandlerList.remove(k);
                this.interceptHandlerList.add(k, new HandlerEntry(name, newHandler));
            } else {
                notfound(oldHandler);
            }
        });
        return this;
    }

    public ProxyInterceptPipeline addLast(String name, ProxyInterceptHandler handler) {
        syncRun(() -> {
            this.interceptHandlerList.add(this.interceptHandlerList.size() - 1, new HandlerEntry(name, handler));
        });
        return this;
    }

    public ProxyInterceptPipeline addBefore(String base, ProxyInterceptHandler handler) {
        syncRun(() -> {
            int k = index(e -> e.name.equals(base));
            if (k > 0) {
                this.interceptHandlerList.add(k, new HandlerEntry(handler));
            } else {
                notfound(base);
            }
        });
        return this;
    }


    public ProxyInterceptPipeline addAfter(String base, ProxyInterceptHandler handler) {
        syncRun(() -> {
            int k = index(e -> e.name.equals(base));
            if (k > 0 && k < this.interceptHandlerList.size() - 1) {
                this.interceptHandlerList.add(k + 1, new HandlerEntry(handler));
            } else {
                notfound(base);
            }
        });
        return this;
    }

    private void notfound(String base) {
        throw new IllegalArgumentException("not found handler with name '" + base + "'");
    }

    private void notfound(ProxyInterceptHandler base) {
        throw new IllegalArgumentException("not found handler '" + base + "'");
    }

    public ProxyInterceptPipeline remove(String name) {
        syncRun(() -> {
            int k = index(e -> e.name.equals(name));
            if (k > 0) {
                this.interceptHandlerList.remove(k);
            } else {
                notfound(name);
            }
        });
        return this;
    }

    public ProxyInterceptPipeline remove(ProxyInterceptHandler handler) {
        syncRun(() -> {
            int k = index(e -> e.name.equals(e.name));
            if (k > 0) {
                this.interceptHandlerList.remove(k);
            } else {
                notfound(handler);
            }
        });
        return this;
    }

    private int index(Predicate<HandlerEntry> handlerEntryPredicate) {
        int k = 1;
        int size = this.interceptHandlerList.size();
        for (; k < size - 1 && !handlerEntryPredicate.test(this.interceptHandlerList.get(k)); k++) {
        }
        if (k >= size) {
            return -1;
        }
        return k;
    }


    private int pos = 0;

    public void onRequest(Channel clientChannel, HttpRequest httpRequest) throws Exception {
        if (pos < this.interceptHandlerList.size()) {
            HandlerEntry handlerEntry = this.interceptHandlerList.get(pos++);
            handlerEntry.handler.onRequest(clientChannel, httpRequest, this);
        }
        pos = 0;
    }

    public void onResponse(Channel clientChannel, Channel serverChannel, HttpResponse httpResponse) throws Exception {
        if (pos < this.interceptHandlerList.size()) {
            this.interceptHandlerList.get(pos++).handler.onResponse(clientChannel, serverChannel, httpResponse, this);
        }
        pos = 0;
    }

    public void onRequestContent(Channel clientChannel, HttpContent httpContent) throws Exception {
        if (pos < this.interceptHandlerList.size()) {
            this.interceptHandlerList.get(pos++).handler.onRequestContent(clientChannel, httpContent, this);
        }
        pos = 0;
    }

    public void onResponseContent(Channel clientChannel, Channel serverChannel, HttpContent httpContent) throws Exception {
        if (pos < this.interceptHandlerList.size()) {
            this.interceptHandlerList.get(pos++).handler.onResponseContent(clientChannel, serverChannel, httpContent, this);
        }
        pos = 0;
    }

    @Getter
    @Setter
    private RequestProto requestProto; //用于修改host与port
}
