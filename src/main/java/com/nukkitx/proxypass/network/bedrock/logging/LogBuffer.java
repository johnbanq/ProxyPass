package com.nukkitx.proxypass.network.bedrock.logging;

import java.util.ArrayDeque;
import java.util.Deque;


public class LogBuffer {

    private ArrayDeque<String> logBuffer = new ArrayDeque<>();

    private boolean closing = false;

    /**
     * close the buffer
     */
    public synchronized void close() {
        closing = true;
    }

    /**
     * try to add an entry to the buffer
     * @throws LogBufferClosedException if the buffer is closed
     */
    public synchronized void offer(String content) {
        if(closing) {
            throw new LogBufferClosedException();
        }
        logBuffer.addLast(content);
    }

    /**
     * drain the buffer
     * @throws LogBufferClosedException if the buffer is closed and there is no entry remaining
     */
    public synchronized Deque<String> drain() {
        if(logBuffer.isEmpty() && closing) {
            // empty && closing => raise exception
            throw new LogBufferClosedException();
        } else {
            // !empty => returns what we have, throws exception on next invocation if closing
            // empty && !closing => returns empty queue
            ArrayDeque<String> buffer = this.logBuffer;
            logBuffer = new ArrayDeque<>();
            return buffer;
        }
    }

}
