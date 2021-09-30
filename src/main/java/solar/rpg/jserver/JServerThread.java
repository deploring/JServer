package solar.rpg.jserver;

import org.jetbrains.annotations.NotNull;
import solar.rpg.jserver.connection.JServerConnectionContextType;
import solar.rpg.jserver.connection.handlers.socket.JServerSocketHandler;

import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@code JServerThread} object is used to continually run a blocking operation on a
 * separate {@link Thread} until {@link #stop()} is called. These threads are generally
 * used for the lifetime of a {@link JServerSocketHandler} to process the I/O of packets
 * between two separate parties over a {@link Socket} connection. However, they can be
 * used for any blocking operation.
 *
 * @author jskinner
 * @since 1.0.0
 */
public abstract class JServerThread implements Runnable {

    @NotNull
    protected final Logger logger;
    private final boolean daemon;
    private final int priority;
    @NotNull
    private final JServerConnectionContextType contextType;

    /**
     * True, if the associated {@code Thread} is currently running.
     */
    private volatile boolean active;

    /**
     * Constructs a {@code JServerThread} with the given {@code Thread} properties.
     *
     * @param daemon      True, if this is a daemon thread.
     * @param priority    Thread priority.
     * @param contextType Owning context of this thread.
     * @param logger      Logger object.
     */
    public JServerThread(boolean daemon, int priority, @NotNull JServerConnectionContextType contextType, @NotNull Logger logger) {
        this.daemon = daemon;
        this.priority = priority;
        this.contextType = contextType;
        this.logger = logger;
        active = false;
    }

    /**
     * Constructs a non-daemon {@code JServerThread} with normal priority.
     *
     * @param contextType Owning context of this thread.
     * @param logger      Logger object.
     */
    public JServerThread(@NotNull JServerConnectionContextType contextType, @NotNull Logger logger) {
        this(false, Thread.NORM_PRIORITY, contextType, logger);
    }

    /**
     * @return True, if the associated {@code Thread} is currently running.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Starts the associated {@code Thread} which will continually run the blocking
     * operation implemented by {@link #run()}, until requested to stop.
     *
     * @throws IllegalStateException Thread is already active.
     */
    public void start() throws IllegalStateException {
        logger.log(Level.INFO, String.format("(%s) Starting thread %s", contextType, getClass().getSimpleName()));

        if (active) throw new IllegalStateException("Thread is already active");

        active = true;

        Thread thread = new Thread(() -> {
            while (active) {
                run();
            }
        });
        thread.setDaemon(daemon);
        thread.setPriority(priority);
        thread.start();
    }

    /**
     * Stops the associated {@code Thread} by marking it as inactive.
     *
     * @throws IllegalStateException Thread is not active.
     */
    public void stop() throws IllegalStateException {
        logger.log(Level.INFO, String.format("(%s) Stopping thread %s", contextType, getClass().getSimpleName()));

        if (!active) throw new IllegalStateException("Thread is not active");

        active = false;
    }
}
