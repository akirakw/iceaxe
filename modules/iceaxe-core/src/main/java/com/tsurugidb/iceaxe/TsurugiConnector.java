package com.tsurugidb.iceaxe;

import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nautilus_technologies.tsubakuro.channel.common.connection.Connector;
import com.nautilus_technologies.tsubakuro.impl.low.common.SessionImpl;
import com.nautilus_technologies.tsubakuro.low.common.Session;
import com.tsurugidb.iceaxe.session.TgSessionInfo;
import com.tsurugidb.iceaxe.session.TsurugiSession;

/**
 * Tsurugi Connector
 */
public class TsurugiConnector {
    private static final Logger LOG = LoggerFactory.getLogger(TsurugiConnector.class);

    /**
     * create Tsurugi Connector
     * 
     * @param endpoint the end-point URI
     * @return Tsurugi Connector
     */
    public static TsurugiConnector createConnector(String endpoint) {
        var lowConnector = Connector.create(endpoint);
        var connector = new TsurugiConnector(lowConnector);
        return connector;
    }

    /**
     * create Tsurugi Connector
     * 
     * @param endpoint the end-point URI
     * @return Tsurugi Connector
     */
    public static TsurugiConnector createConnector(URI endpoint) {
        var lowConnector = Connector.create(endpoint);
        var connector = new TsurugiConnector(lowConnector);
        return connector;
    }

    private final Connector lowConnector;

    protected TsurugiConnector(Connector lowConnector) {
        this.lowConnector = lowConnector;
    }

    /**
     * create Tsurugi Session
     * 
     * @param info Session Information
     * @return Tsurugi Session
     * @throws IOException
     */
    public TsurugiSession createSession(TgSessionInfo info) throws IOException {
        LOG.trace("session create. info={}", info);
        var lowSession = createLowSession();
        var lowCredential = info.credential();
        var lowWireFuture = lowConnector.connect(lowCredential);
        var session = new TsurugiSession(info, lowSession, lowWireFuture);
        return session;
    }

    protected Session createLowSession() {
        return new SessionImpl();
    }
}
