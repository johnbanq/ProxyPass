package com.nukkitx.proxypass.network.bedrock.logging;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockSession;
import com.nukkitx.proxypass.ProxyPass;
import lombok.extern.log4j.Log4j2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


/**
 * impl note: stopping procedure
 * - when stop() is called (after both end of the connection are disconnected), the LogBuffer is then closed
 * - stopping the periodic flush task is then left to the task itself,
 * - it should cancel itself after knowing the buffer is closed
 */
@Log4j2
public class SessionLogger {

    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final ProxyPass proxy;

    private final Path dataPath;

    private final Path logPath;

    private final LogBuffer buffer = new LogBuffer();

    private ScheduledFuture<?> flushFuture;

    public SessionLogger(ProxyPass proxy, Path sessionsDir, String displayName, long timestamp) {
        this.proxy = proxy;
        this.dataPath = sessionsDir.resolve(displayName + '-' + timestamp);
        this.logPath = dataPath.resolve("packets.log");
    }

    public void start() {
        if (proxy.getConfiguration().isLoggingPackets()){
            if (proxy.getConfiguration().getLogTo().logToFile) {
                log.debug("Packets will be logged under " + logPath.toString());
                try {
                    Files.createDirectories(dataPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            flushFuture = executor.scheduleAtFixedRate(this::flushLogBuffer, 5, 5, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        buffer.close();
    }

    public void saveImage(String name, BufferedImage image) {
        Path path = dataPath.resolve(name + ".png");
        try (OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ImageIO.write(image, "png", stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveJson(String name, JSONObject object) throws IOException {
        ObjectWriter jsonout = ProxyPass.JSON_MAPPER.writer(new DefaultPrettyPrinter());
        jsonout.writeValue(new FileOutputStream(logPath.getParent().resolve(name + ".json").toFile()), object);
    }

    public void saveJson(String name, JsonNode node) throws IOException {
        ObjectWriter jsonout = ProxyPass.JSON_MAPPER.writer(new DefaultPrettyPrinter());
        jsonout.writeValue(new FileOutputStream(logPath.getParent().resolve(name + ".json").toFile()), node);
    }

    public void saveJson(String name, byte[] encodedJsonString) {
        Path geometryPath = dataPath.resolve(name +".json");
        try {
            Files.write(geometryPath, encodedJsonString, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logPacket(BedrockSession session, BedrockPacket packet, boolean upstream) {
        String logPrefix = getLogPrefix(upstream);
        if (!proxy.isIgnoredPacket(packet.getClass())) {
            if (session.isLogging() && log.isTraceEnabled()) {
                log.trace(logPrefix + " {}: {}", session.getAddress(), packet);
            }

            if (proxy.getConfiguration().isLoggingPackets()) {
                logToBuffer(() -> logPrefix + packet);
            }

            if (proxy.getConfiguration().isLoggingPackets() && proxy.getConfiguration().getLogTo().logToConsole) {
                System.out.println(logPrefix + packet);
            }
        }
    }

    private String getLogPrefix(boolean upstream) {
        return upstream ? "[SERVER BOUND]  -  " : "[CLIENT BOUND]  -  ";
    }

    private void logToBuffer(Supplier<String> supplier) {
        buffer.offer(supplier.get());
    }

    private void flushLogBuffer() {
        try {
            Deque<String> contents = buffer.drain();
            if (proxy.getConfiguration().getLogTo().logToFile) {
                Files.write(logPath, contents, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            }
        } catch (LogBufferClosedException e) {
            // stop if we drained everything
            flushFuture.cancel(false);
            flushFuture = null;
        } catch (IOException e) {
            log.error("Unable to flush packet log", e);
        }
    }

}
