package solar.rpg.jserver.packet;

import java.io.Serial;
import java.io.Serializable;
import java.net.InetSocketAddress;

public abstract class JServerPacket implements Serializable {

    @Serial
    private static final long serialVersionUID = -2549633621349122038L;

    private transient InetSocketAddress originAddress;

    public void onReceived(InetSocketAddress originAddress) {
        this.originAddress = originAddress;
    }

    public InetSocketAddress getOriginAddress() {
        return originAddress;
    }
}
