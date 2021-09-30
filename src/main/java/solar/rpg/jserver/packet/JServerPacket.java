package solar.rpg.jserver.packet;

import java.io.Serializable;
import java.net.InetSocketAddress;

public abstract class JServerPacket implements Serializable {

    private InetSocketAddress originAddress;

    public void onReceived(InetSocketAddress originAddress) {
        if (this.originAddress != null)
            throw new IllegalStateException("Origin address cannot be set twice");

        this.originAddress = originAddress;
    }

    public InetSocketAddress getOriginAddress() {
        return originAddress;
    }
}
