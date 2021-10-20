package solar.rpg.jserver.connection.handlers.packet;

import org.jetbrains.annotations.NotNull;
import solar.rpg.jserver.JServerThread;
import solar.rpg.jserver.connection.JServerConnectionContextType;
import solar.rpg.jserver.connection.handlers.socket.JServerSocketHandler;
import solar.rpg.jserver.packet.JServerPacketHeartbeat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@code JServerHost} acts as a server host which accepts and manages incoming
 * client {@link Socket} connections. It is responsible for receiving and processing
 * packets from clients, and also has the capability to send out packets to clients.
 *
 * @author jskinner
 * @since 1.0.0
 */
public abstract class JServerHost extends JServerPacketHandler {

    @NotNull
    private final ServerSocket serverSocket;
    @NotNull
    private final JServerAcceptClientThread acceptClientThread;

    /**
     * Constructs a {@code JServerHost}.
     *
     * @param bindAddr Address which the server host will bind to.
     * @param port     Port that the server socket will be hosted on.
     * @param logger   Logger object.
     * @throws IOException I/O exception while opening server socket.
     */
    public JServerHost(
            @NotNull InetAddress bindAddr,
            int port,
            @NotNull ExecutorService executor,
            @NotNull Logger logger) throws IOException {
        super(JServerConnectionContextType.HOST, executor, logger);

        try {
            serverSocket = new ServerSocket(port, 0, bindAddr);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to open server socket", e);
            throw e;
        }

        acceptClientThread = new JServerAcceptClientThread(logger);
        acceptClientThread.start();
        doHeartbeat();
    }

    /**
     * Closes all client connections and attempts to close the {@link ServerSocket}.
     * The {@code JServerHost} will no longer accept any more incoming connections.
     */
    @Override
    public void onBeforeClosed() {
        acceptClientThread.stop();

        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to close server socket", e);
        }
    }

    /**
     * Periodically sends heartbeat packets so the {@code Socket} does not time out.
     */
    private void doHeartbeat() {
        executor.submit(() -> {
            writePacketAll(new JServerPacketHeartbeat());
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException ignored) {
            }
            if (!isClosed())
                doHeartbeat();
        });
    }

    /**
     * This thread continually listens for (and accepts) incoming client connections.
     */
    private final class JServerAcceptClientThread extends JServerThread {

        public JServerAcceptClientThread(@NotNull Logger logger) {
            super(contextType, logger);
        }

        @Override
        public void run() {
            try {
                registerSocket(serverSocket.accept());
            } catch (IOException e) {
                if (!isActive()) return;
                logger.log(Level.INFO, String.format("(%s) Unable to accept connection", contextType), e);
            }
        }
    }
}
