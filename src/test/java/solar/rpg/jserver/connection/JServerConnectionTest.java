package solar.rpg.jserver.connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

class JServerConnectionTest {

    private final static int TEST_PORT = 65535;

    private final Logger testLogger = LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private JTestServerHost testHost;
    private JTestServerClient testClient1, testClient2;

    @BeforeEach
    void setUp() {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        testLogger.addHandler(handler);
        testLogger.setLevel(Level.ALL);
        testLogger.log(Level.FINE, "Setting up host");
        try {
            testHost = new JTestServerHost(TEST_PORT, executor, testLogger);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void test() {
        testLogger.log(Level.FINE, "Setting up client 1");
        try {
            testClient1 = new JTestServerClient(TEST_PORT, executor, testLogger);
        } catch (IOException e) {
            e.printStackTrace();
        }
        testLogger.log(Level.FINE, "Setting up client 2");
        try {
            testClient2 = new JTestServerClient(TEST_PORT, executor, testLogger);
        } catch (IOException e) {
            e.printStackTrace();
        }
        testLogger.log(Level.FINE, "Closing host");
        testHost.close();
        testLogger.log(Level.FINE, "Closing client 1");
        testClient1.close();
        testLogger.log(Level.FINE, "Closing client 2");
        testClient2.close();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
    }
}