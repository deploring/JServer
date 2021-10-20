package solar.rpg.jserver.connection.handlers.packet;

import org.jetbrains.annotations.NotNull;
import solar.rpg.jserver.connection.JServerConnectionContextType;
import solar.rpg.jserver.packet.JServerPacket;
import solar.rpg.jserver.packet.JServerPacketHeartbeat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class JServerClient extends JServerPacketHandler {

    @NotNull
    private final InetSocketAddress hostAddr;

    public JServerClient(
            @NotNull InetAddress hostAddr,
            int port,
            @NotNull ExecutorService executor,
            @NotNull Logger logger) {
        super(JServerConnectionContextType.CLIENT, executor, logger);
        this.hostAddr = new InetSocketAddress(hostAddr, port);
    }

    /**
     * Attempts to connect to the host using the provided details.
     *
     * @throws IOException Unable to open a {@code Socket} to the host.
     */
    public void tryConnect() throws IOException {
        try {
            Socket newSocket = new Socket();
            newSocket.connect(this.hostAddr, (int) TimeUnit.SECONDS.toMillis(5));
            newSocket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(15));
            registerSocket(newSocket);
            doHeartbeat();
        } catch (IOException e) {
            logger.log(Level.INFO,
                       String.format(
                               "Unable to open socket to host %s:%d: %s",
                               hostAddr,
                               hostAddr.getPort(),
                               e.getMessage()));
            throw e;
        }
    }

    /**
     * Periodically sends heartbeat packets so the {@code Socket} does not time out.
     */
    private void doHeartbeat() {
        executor.submit(() -> {
            writePacket(new JServerPacketHeartbeat());
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException ignored) {
            }
            if (!isClosed())
                doHeartbeat();
        });
    }

    @NotNull
    public InetSocketAddress getHostAddr() {
        return hostAddr;
    }

    /**
     * Clients are not usually connected to more than one host.
     * This shortcut method writes packets to the host address.
     *
     * @param packetToWrite Packet to write to the host.
     */
    public void writePacket(@NotNull JServerPacket packetToWrite) {
        writePacket(getHostAddr(), packetToWrite);
    }

    @Override
    public void onBeforeClosed() {
        // This does not need to be used by all client implementations. Override if needed.
    }
}
