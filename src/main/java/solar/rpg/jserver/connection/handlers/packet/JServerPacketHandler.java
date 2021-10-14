package solar.rpg.jserver.connection.handlers.packet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import solar.rpg.jserver.connection.JServerConnectionContextType;
import solar.rpg.jserver.connection.handlers.socket.JServerSocketHandler;
import solar.rpg.jserver.packet.JServerPacket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@code JServerPacketHandler} is a {@code Consumer} of {@link JServerPacket} objects. When packets are accepted,
 * they are placed into a blocking queue and are processed one at a time. Primitive packet types are automatically
 * handled by this abstract class. Custom packet types are handled by the concrete implementation of this class.
 *
 * @author jskinner
 * @since 1.0.0
 */
public abstract class JServerPacketHandler {

    @NotNull
    protected final Logger logger;
    @NotNull
    protected final JServerConnectionContextType contextType;
    @NotNull
    private final ExecutorService executor;
    @NotNull
    private final Map<InetSocketAddress, SimpleImmutableEntry<JServerSocketHandler, JServerPacketSubscriber>> socketHandlerSubscriberMap;
    @NotNull
    private final AtomicBoolean closed;

    public JServerPacketHandler(
            @NotNull JServerConnectionContextType contextType,
            @NotNull ExecutorService executor,
            @NotNull Logger logger) {
        this.logger = logger;
        this.executor = executor;
        this.contextType = contextType;
        socketHandlerSubscriberMap = Collections.synchronizedMap(new ConcurrentHashMap<>());
        closed = new AtomicBoolean(false);
    }

    /**
     * Creates a new socket handler for reading and writing packets. This packet handler subscribes to the socket
     * handler, so it can process incoming packets.
     *
     * @param connection New {@code Socket} connection to handle.
     * @throws IOException I/O error while initialising the socket handler.
     */
    protected void registerSocket(@NotNull Socket connection) throws IOException {
        assert connection.isConnected() : "Expected established socket connection";

        JServerSocketHandler socketHandler = new JServerSocketHandler(connection, executor, contextType, logger);
        JServerPacketSubscriber subscriber = new JServerPacketSubscriber(socketHandler.getAddress());
        socketHandlerSubscriberMap.put(
                socketHandler.getAddress(),
                new SimpleImmutableEntry<>(socketHandler, subscriber));
        socketHandler.subscribe(subscriber);
    }

    public abstract void onNewConnection(@NotNull InetSocketAddress originAddress);

    public void writePacket(
            @NotNull InetSocketAddress originAddress,
            @NotNull JServerPacket packetToSend) throws IOException {
        if (!socketHandlerSubscriberMap.containsKey(originAddress))
            throw new IllegalArgumentException("Unknown connection");

        socketHandlerSubscriberMap.get(originAddress).getKey().writePacket(packetToSend);
    }

    public void close() {
        assert !closed.get() : "Packet handler is already closed";

        logger.log(Level.FINE,
                   String.format("(%s) Attempting to close packet handler with %d active connections",
                                 contextType,
                                 socketHandlerSubscriberMap.size()));

        onBeforeClosed();
        closed.set(true);

        for (InetSocketAddress originAddress : socketHandlerSubscriberMap.keySet())
            closeSocket(originAddress);

        assert socketHandlerSubscriberMap.size() == 0 : "Expected all connections to be closed";

        //TODO: Probably have some field to stop accepting writes/reads.
    }

    public abstract void onBeforeClosed();

    public void closeSocket(@NotNull InetSocketAddress originAddress) {
        if (!socketHandlerSubscriberMap.containsKey(originAddress))
            throw new IllegalArgumentException("Unknown connection");

        socketHandlerSubscriberMap.get(originAddress).getValue().cancel();
    }

    public abstract void onSocketClosed(@NotNull InetSocketAddress originAddress);

    /**
     * Concrete implementations of {@code JServerPacketHandler} are required to handle any custom packet types
     * introduced at a higher level.
     *
     * @param packet Custom packet to handle.
     */
    public abstract void onPacketReceived(@NotNull InetSocketAddress originAddress, @NotNull JServerPacket packet);

    private final class JServerPacketSubscriber implements Subscriber<JServerPacket> {

        @NotNull
        private final InetSocketAddress originAddress;
        @Nullable
        private Subscription subscription;
        @NotNull
        private final AtomicBoolean wantToClose;

        public JServerPacketSubscriber(@NotNull InetSocketAddress originAddress) {
            this.originAddress = originAddress;
            wantToClose = new AtomicBoolean(false);
        }

        public void cancel() {
            assert subscription != null : "Expected subscription to be set";

            wantToClose.set(true);
            subscription.cancel();
        }

        @Override
        public void onSubscribe(@NotNull Subscription subscription) {
            assert this.subscription == null : "Subscription cannot be set twice";

            this.subscription = subscription;
            onNewConnection(originAddress);
            subscription.request(1);
        }

        @Override
        public void onNext(@NotNull JServerPacket packet) {
            assert subscription != null : "Expected subscription to be set";
            assert packet.getOriginAddress() != null : "Expected origin address to be set";
            assert originAddress.equals(packet.getOriginAddress()) : "Mismatched origin address?";

            logger.log(Level.FINEST, String.format("(%s) Received packet from %s", contextType, originAddress));

            onPacketReceived(originAddress, packet);

            if (!wantToClose.get()) subscription.request(1);
        }

        @Override
        public void onError(@NotNull Throwable throwable) {
            logger.log(Level.INFO,
                       String.format("(%s) Error receiving packet from %s",
                                     contextType,
                                     originAddress),
                       throwable);
        }

        @Override
        public void onComplete() {
            onSocketClosed(originAddress);
            socketHandlerSubscriberMap.remove(originAddress);
        }
    }
}
