package com.tsurugidb.iceaxe.test.timeout;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.tsurugidb.iceaxe.session.TgSessionInfo;
import com.tsurugidb.iceaxe.session.TgSessionInfo.TgTimeoutKey;
import com.tsurugidb.iceaxe.session.TsurugiSession;
import com.tsurugidb.iceaxe.transaction.TgTxOption;
import com.tsurugidb.iceaxe.transaction.TsurugiTransaction;

/**
 * transaction begin timeout test
 */
@Disabled // TODO remove Disabled
public class DbTimeoutTransactionBeginTest extends DbTimetoutTest {

    @Test
    void timeoutDefault() throws IOException {
        testTimeout(new TimeoutModifier() {
            @Override
            public void modifySessionInfo(TgSessionInfo info) {
                info.timeout(TgTimeoutKey.DEFAULT, 1, TimeUnit.SECONDS);
            }
        });
    }

    @Test
    void timeoutSpecified() throws IOException {
        testTimeout(new TimeoutModifier() {
            @Override
            public void modifySessionInfo(TgSessionInfo info) {
                info.timeout(TgTimeoutKey.TRANSACTION_BEGIN, 1, TimeUnit.SECONDS);
            }
        });
    }

    @Test
    void timeoutSet() throws IOException {
        testTimeout(new TimeoutModifier() {
            @Override
            public void modifyTransaction(TsurugiTransaction transaction) {
                transaction.setBeginTimeout(1, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    protected void clientTask(PipeServerThtread pipeServer, TsurugiSession session, TimeoutModifier modifier) throws Exception {
        session.getLowSqlClient();

        pipeServer.setPipeWrite(false);
        try (var transaction = session.createTransaction(TgTxOption.ofOCC())) {
            modifier.modifyTransaction(transaction);

            try {
                transaction.getLowTransaction();
            } catch (IOException e) {
                assertInstanceOf(TimeoutException.class, e.getCause());
                LOG.trace("timeout success");
                return;
            } finally {
                pipeServer.setPipeWrite(true);
            }
            fail("didn't time out");
        }
    }
}
