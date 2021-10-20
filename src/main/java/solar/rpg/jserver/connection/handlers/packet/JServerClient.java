package solar.rpg.jserver.connection.handlers.packet;

import org.jetbrains.annotations.NotNull;
import solar.rpg.jserver.connection.JServerConnectionContextType;
import solar.rpg.jserver.packet.JServerPacket;

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

    public void tryConnect() throws IOException {
        try {
            Socket newSocket = new Socket();
            newSocket.connect(this.hostAddr, (int) TimeUnit.SECONDS.toMillis(5));
            newSocket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(15));
            registerSocket(newSocket);
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

    @NotNull
    public InetSocketAddress getHostAddr() {
        return hostAddr;
    }

    public void writePacket(@NotNull JServerPacket packetToWrite) {
        writePacket(getHostAddr(), packetToWrite);
    }

    @Override
    public void onBeforeClosed() {
        // Clients do not usually need logic for this event, so just override if needed
    }

}
