package solar.rpg.jserver.connection.handlers.socket;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import solar.rpg.jserver.connection.JServerConnectionContextType;
import solar.rpg.jserver.packet.JServerPacket;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@code JServerSocketHandler} manages communication between two parties connected using {@link Socket} objects where
 * the medium of communication is {@link JServerPacket} objects. It can write packets to the other party which are
 * continually being listened for. A {@link Consumer} instance is passed in that is responsible for taking these packets
 * and performing the appropriate operation(s) with them.
 *
 * @author jskinner
 * @since 1.0.0
 */
public final class JServerSocketHandler implements Publisher<JServerPacket> {

    @NotNull
    private final Logger logger;
    @NotNull
    private final Socket socket;
    @NotNull
    private final ObjectOutputStream outputStream;
    @NotNull
    private final ObjectInputStream inputStream;
    @NotNull
    private final JServerConnectionContextType contextType;

    // --- Publisher/Subscriber stuff for Packets //
    @NotNull
    private final ExecutorService executor;
    @Nullable
    private JServerPacketSubscription subscription;

    /**
     * Constructs a {@code JServerSocketHandler}.
     *
     * @param socket      Active {@code Socket} connection.
     * @param executor    Executor service responsible for running socket handler logic.
     * @param contextType Context of this {@code Socket} connection.
     * @param logger      Logger object.
     * @throws IOException I/O error while attempting to create object streams.
     */
    public JServerSocketHandler(
            @NotNull Socket socket,
            @NotNull ExecutorService executor,
            @NotNull JServerConnectionContextType contextType,
            @NotNull Logger logger) throws IOException {
        this.socket = socket;
        this.executor = executor;
        this.contextType = contextType;
        this.logger = logger;

        logger.log(Level.INFO, String.format("(%s) Opening connection to %s", contextType, getAddress()));

        try {
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            logger.log(Level.WARNING,
                       String.format("(%s) Unable to open socket IO stream(s) to %s", contextType, getAddress()),
                       e);
            throw e;
        }
    }

    @NotNull
    public InetSocketAddress getAddress() {
        return (InetSocketAddress) socket.getRemoteSocketAddress();
    }

    /**
     * Writes a {@link JServerPacket} to the other side of the {@code Socket} connection.
     *
     * @param packet Packet to send to the other side of the {@code Socket} connection.
     * @throws IOException Exception thrown by the underlying {@link ObjectOutputStream}.
     */
    public void writePacket(@NotNull JServerPacket packet) {
        assert !socket.isClosed() : "Socket is closed";
        assert subscription != null : "Subscription is not set";

        logger.log(Level.FINEST,
                   String.format("(%s) Writing packet to %s", contextType, socket.getRemoteSocketAddress()));

        try {
            outputStream.writeObject(packet);
        } catch (SocketException e) {
            logger.log(Level.INFO,
                       String.format(
                               "(%s) Socket error while writing packet to %s",
                               contextType,
                               socket.getRemoteSocketAddress()),
                       e);
            subscription.cancel();
        } catch (IOException e) {
            logger.log(Level.INFO, String.format("(%s) Error writing packet", contextType), e);
            subscription.cancel();
        }
    }

    @Override
    public void subscribe(@NotNull Subscriber<? super JServerPacket> subscriber) {
        if (subscription != null) {
            subscriber.onError(new IllegalStateException("There is already an active subscription"));
            return;
        }

        subscription = new JServerPacketSubscription(subscriber, executor);
        subscriber.onSubscribe(subscription);
    }

    /**
     * Represents a {@link Subscription} between this socket handler and a subscriber of {@link JServerPacket} objects.
     */
    public final class JServerPacketSubscription implements Subscription {

        @NotNull
        private final Subscriber<? super JServerPacket> subscriber;
        @NotNull
        private final ExecutorService executor;
        @NotNull
        private final AtomicBoolean wantToClose;

        JServerPacketSubscription(
                @NotNull Subscriber<? super JServerPacket> subscriber,
                @NotNull ExecutorService executor) {
            this.subscriber = subscriber;
            this.executor = executor;
            wantToClose = new AtomicBoolean(false);
        }

        public InetSocketAddress getAddress() {
            return JServerSocketHandler.this.getAddress();
        }

        @Override
        public void request(long n) {
            assert !socket.isClosed() : "Expected open socket";

            if (n != 1) {
                executor.execute(() -> subscriber.onError(new IllegalArgumentException()));
                return;
            }

            executor.submit(() -> {
                try {
                    JServerPacket received = (JServerPacket) inputStream.readObject();
                    received.onReceived(getAddress());

                    subscriber.onNext(received);
                } catch (EOFException | SocketException e) {
                    logger.log(Level.INFO,
                               String.format("(%s) Socket closed while reading packet from %s",
                                             contextType,
                                             getAddress()));
                    if (!wantToClose.get()) cancel();
                } catch (ClassNotFoundException e) {
                    logger.log(Level.INFO, String.format("(%s) Class not found", contextType), e);
                } catch (IOException e) {
                    logger.log(Level.INFO,
                               String.format("(%s) Error reading packet from %s: %s",
                                             contextType,
                                             getAddress(),
                                             e.getMessage()));
                }
            });
        }

        @Override
        public void cancel() {
            assert !wantToClose.get() : "Socket is already wanting to close";
            assert !socket.isClosed() : "Socket is already closed";

            logger.log(Level.INFO, String.format("(%s) Closing socket handler for %s", contextType, getAddress()));
            wantToClose.set(true);

            try {
                socket.close();
            } catch (IOException e) {
                logger.log(Level.WARNING,
                           String.format("(%s) Unable to close socket %s", contextType, getAddress()),
                           e);
            }

            subscriber.onComplete();
        }
    }
}
