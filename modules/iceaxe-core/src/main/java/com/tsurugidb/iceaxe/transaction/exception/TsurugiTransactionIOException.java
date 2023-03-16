package com.tsurugidb.iceaxe.transaction.exception;

import com.tsurugidb.iceaxe.exception.TsurugiIOException;
import com.tsurugidb.iceaxe.transaction.TsurugiTransaction;
import com.tsurugidb.iceaxe.transaction.manager.option.TgTmTxOptionSupplier;
import com.tsurugidb.iceaxe.transaction.option.TgTxOption;

/**
 * Tsurugi Transaction IOException
 */
@SuppressWarnings("serial")
public class TsurugiTransactionIOException extends TsurugiIOException {

    private final int iceaxeTxId;
    private final int iceaxeTmExecuteId;
    private final int attempt;
    private final TgTxOption txOption;

    // internal
    public TsurugiTransactionIOException(String message, TsurugiTransaction transaction, Exception cause) {
        super(createMessage(message, transaction), cause);
        this.iceaxeTxId = transaction.getIceaxeTxId();
        this.iceaxeTmExecuteId = transaction.getIceaxeTmExecuteId();
        this.attempt = transaction.getAttempt();
        this.txOption = transaction.getTransactionOption();
    }

    private static String createMessage(String message, TsurugiTransaction transaction) {
        return message + ". " + transaction;
    }

    /**
     * get iceaxe transactionId
     *
     * @return iceaxe transactionId
     */
    public int getIceaxeTxId() {
        return this.iceaxeTxId;
    }

    /**
     * get iceaxe tm executeId
     *
     * @return iceaxe tm executeId
     */
    public int getIceaxeTmExecuteId() {
        return this.iceaxeTmExecuteId;
    }

    /**
     * get attempt number
     *
     * @return attempt number
     * @see TgTmTxOptionSupplier#get(int, TsurugiTransactionException)
     */
    public int getAttempt() {
        return this.attempt;
    }

    /**
     * get transaction option
     *
     * @return transaction option
     */
    public TgTxOption getTransactionOption() {
        return this.txOption;
    }
}
