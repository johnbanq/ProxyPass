package com.nukkitx.proxypass.network.bedrock.logging;

public class LogBufferClosedException extends RuntimeException {

    public LogBufferClosedException() {
        super("log buffer is closed!");
    }

}
