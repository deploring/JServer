package solar.rpg.jserver.connection.handlers.packet;

import org.jetbrains.annotations.NotNull;
import solar.rpg.jserver.connection.JServerConnectionContextType;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class JServerClient extends JServerPacketHandler {

    public JServerClient(
            @NotNull InetAddress hostAddr,
            int port,
            @NotNull ExecutorService executor,
            @NotNull Logger logger) throws IOException {
        super(JServerConnectionContextType.CLIENT, executor, logger);

        try {
            registerSocket(new Socket(hostAddr, port));
        } catch (IOException e) {
            logger.log(Level.INFO, String.format("Unable to open socket to host %s:%d: %s", hostAddr, port, e.getMessage()));
            throw e;
        }
    }

    @Override
    public void onBeforeClosed() {
        // Clients do not usually need logic for this event, so just override if needed
    }

}
