package com.tsurugidb.iceaxe.result;

import java.io.IOException;

import javax.annotation.OverridingMethodsMustInvokeSuper;

import com.tsurugidb.iceaxe.transaction.TsurugiTransaction;
import com.tsurugidb.iceaxe.transaction.exception.TsurugiTransactionException;

/**
 * Tsurugi SQL result
 */
public abstract class TsurugiResult implements AutoCloseable {

    private final int iceaxeSqlExecuteId;
    private final TsurugiTransaction ownerTransaction;

    // internal
    public TsurugiResult(int sqlExecuteId, TsurugiTransaction transaction) throws IOException {
        this.iceaxeSqlExecuteId = sqlExecuteId;
        this.ownerTransaction = transaction;
        transaction.addChild(this);
    }

    /**
     * get iceaxe SQL executeId
     *
     * @return iceaxe SQL executeId
     */
    public int getIceaxeSqlExecuteId() {
        return this.iceaxeSqlExecuteId;
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public void close() throws IOException, TsurugiTransactionException {
        ownerTransaction.removeChild(this);
    }
}
