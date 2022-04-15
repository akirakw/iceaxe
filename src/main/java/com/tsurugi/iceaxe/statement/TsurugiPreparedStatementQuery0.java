package com.tsurugi.iceaxe.statement;

import java.io.IOException;
import java.util.function.Function;

import com.tsurugi.iceaxe.result.TsurugiResultRecord;
import com.tsurugi.iceaxe.result.TsurugiResultSet;
import com.tsurugi.iceaxe.session.TsurugiSession;
import com.tsurugi.iceaxe.transaction.TsurugiTransaction;
import com.tsurugi.iceaxe.transaction.TsurugiTransactionIOException;

/**
 * Tsurugi PreparedStatement
 * <ul>
 * <li>TODO+++翻訳: クエリー系SQL</li>
 * <li>TODO+++翻訳: SQLのパラメーターなし</li>
 * </ul>
 * 
 * @param <R> record type
 */
public class TsurugiPreparedStatementQuery0<R> extends TsurugiPreparedStatement {

    private final String sql;
    private final Function<TsurugiResultRecord, R> recordConverter;

    // internal
    public TsurugiPreparedStatementQuery0(TsurugiSession session, String sql, Function<TsurugiResultRecord, R> recordConverter) {
        super(session);
        this.sql = sql;
        this.recordConverter = recordConverter;
    }

    /**
     * execute query
     * 
     * @param transaction Transaction
     * @return result
     * @throws TsurugiTransactionIOException
     */
    public TsurugiResultSet<R> execute(TsurugiTransaction transaction) throws TsurugiTransactionIOException {
        try {
            var lowTransaction = transaction.getLowTransaction();
            var lowResultSetFuture = lowTransaction.executeQuery(sql);
            var result = new TsurugiResultSet<>(this, lowResultSetFuture, recordConverter);
            addChild(result);
            return result;
        } catch (IOException e) {
            throw new TsurugiTransactionIOException(e);
        }
    }
}
