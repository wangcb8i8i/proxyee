package com.github.monkeywie.proxyee.intercept;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author admin
 * @since 2021/5/31 10:00
 */
public abstract class AbstractPipeline<Handler> {

    private class HandlerHolder {
        private HandlerHolder prev;
        private HandlerHolder next;
        private String name;
        private Object handler;

        public HandlerHolder(String name, Object pipelineHandler) {
            this.handler = Objects.requireNonNull(pipelineHandler, "pipeline handler require not null");
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("name is null or empty");
            }
            this.name = name;
            //this.name = name == null || name.trim().isEmpty() ? this.handler.getClass().getName() : name;
        }

        public HandlerHolder(Object pipelineHandler) {
            this(pipelineHandler.getClass().getName(), pipelineHandler);
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    private HandlerHolder head;
    private HandlerHolder tail;

    public AbstractPipeline(Handler headHandler, Handler tailHandler) {
        this.head = new HandlerHolder("head", Objects.requireNonNull(headHandler, "require head handler"));
        this.tail = new HandlerHolder("tail", Objects.requireNonNull(tailHandler, "require tail handler"));
        this.head.next = this.tail;
        this.tail.prev = this.head;
    }

    private HandlerHolder find(Predicate<HandlerHolder> holderPredicate,String errorMessageIfAbsent) {
        HandlerHolder holder = this.head;
        do {
            if (holderPredicate.test(holder)) {
                return holder;
            }
            holder = holder.next;
        } while (holder != null);
        throw new IllegalArgumentException(errorMessageIfAbsent);
    }

    private synchronized void find(String matchedName, Consumer<HandlerHolder> holderConsumer) {
        holderConsumer.accept(find(h -> h.name.equals(matchedName),"no handler named '" + matchedName + "'"));
    }

    private synchronized void find(Handler matchedHandler, Consumer<HandlerHolder> holderConsumer) {
        holderConsumer.accept(find(h -> h.handler.equals(matchedHandler),"no handler instance '" + matchedHandler + "'"));
    }

    private synchronized void setBetween(HandlerHolder prev, HandlerHolder next, HandlerHolder handlerHolder) {
        prev.next = handlerHolder;
        handlerHolder.prev = prev;
        handlerHolder.next = next;
        next.prev = handlerHolder;
    }

//    private boolean setHeadTail(HandlerHolder handlerHolder) {
//        if (this.head == null) {
//            this.head = handlerHolder;
//            return true;
//        } else if (this.head.next == null) {
//            this.tail = handlerHolder;
//            this.head.next = this.tail;
//            this.tail.prev = this.head;
//            return true;
//        } else {
//            return false;
//        }
//    }

    public final AbstractPipeline addFirst(Handler handler) {
        setBetween(head, head.next, new HandlerHolder(handler));
        return this;
    }

    public final AbstractPipeline addFirst(String name, Handler handler) {
        setBetween(head, head.next, new HandlerHolder(name, handler));
        return this;
    }

    public final AbstractPipeline addLast(Handler handler) {
        this.setBetween(tail.prev, tail, new HandlerHolder(handler));
        return this;
    }

    public final AbstractPipeline addLast(String name, Handler handler) {
        this.setBetween(tail.prev, tail, new HandlerHolder(name, handler));
        return this;
    }

    public final AbstractPipeline addBefore(String base, Handler handler) {
        this.find(base, holder -> {
            this.setBetween(holder.prev, holder, new HandlerHolder(handler));
        });
        return this;
    }

    public final AbstractPipeline addBefore(Handler old, Handler handler) {
        this.find(old, holder -> {
            this.setBetween(holder.prev, holder, new HandlerHolder(handler));
        });
        return this;
    }

    public final AbstractPipeline addBefore(String base, String name, Handler handler) {
        this.find(base, holder -> {
            this.setBetween(holder.prev, holder, new HandlerHolder(name, handler));
        });
        return this;
    }

    public final AbstractPipeline addBefore(Handler old, String name, Handler handler) {
        this.find(old, holder -> {
            this.setBetween(holder.prev, holder, new HandlerHolder(name, handler));
        });
        return this;
    }

    public final AbstractPipeline addAfter(String base, Handler handler) {
        this.find(base, holder -> {
            this.setBetween(holder, holder.next, new HandlerHolder(handler));
        });
        return this;
    }

    public final AbstractPipeline addAfter(Handler old, Handler handler) {
        this.find(old, holder -> {
            this.setBetween(holder, holder.next, new HandlerHolder(handler));
        });
        return this;
    }

    public final AbstractPipeline addAfter(String base, String name, Handler handler) {
        this.find(base, holder -> {
            this.setBetween(holder, holder.next, new HandlerHolder(name, handler));
        });
        return this;
    }

    public final AbstractPipeline addAfter(Handler old, String name, Handler handler) {
        this.find(old, holder -> {
            this.setBetween(holder, holder.next, new HandlerHolder(name, handler));
        });
        return this;
    }

    public final AbstractPipeline replace(String name, Handler handler) {
        this.find(name, holder -> {
            holder.handler = handler;
        });
        return this;
    }

    public final AbstractPipeline replace(Handler old, Handler handler) {
        this.find(old, holder -> {
            holder.handler = handler;
        });
        return this;
    }


    public final AbstractPipeline remove(String name) {
        this.find(name, holder -> {
            holder.prev.next = holder.next;
            holder.next.prev = holder.prev;
        });
        return this;
    }

    public final AbstractPipeline remove(Handler handler) {
        this.find(handler, holder -> {
            holder.prev.next = holder.next;
            holder.next.prev = holder.prev;
        });
        return this;
    }

    private HandlerHolder current;

    protected final void invoke(Consumer<Handler> handlerInvoker) {
        Objects.requireNonNull(this.head, "require at least one handler be configurated to this pipeline.");
        current = current == null ? head : current.next;
        if (current != null) {
            handlerInvoker.accept((Handler) current.handler);
        }
        current = null;
    }
}
