package com.tsurugidb.iceaxe.transaction;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tsurugidb.iceaxe.exception.IceaxeErrorCode;
import com.tsurugidb.iceaxe.exception.TsurugiIOException;
import com.tsurugidb.iceaxe.session.TgSessionOption;
import com.tsurugidb.iceaxe.session.TgSessionOption.TgTimeoutKey;
import com.tsurugidb.iceaxe.session.TsurugiSession;
import com.tsurugidb.iceaxe.sql.TsurugiSqlPreparedQuery;
import com.tsurugidb.iceaxe.sql.TsurugiSqlPreparedStatement;
import com.tsurugidb.iceaxe.sql.TsurugiSqlQuery;
import com.tsurugidb.iceaxe.sql.TsurugiSqlStatement;
import com.tsurugidb.iceaxe.sql.result.TsurugiQueryResult;
import com.tsurugidb.iceaxe.sql.result.TsurugiStatementResult;
import com.tsurugidb.iceaxe.transaction.event.TsurugiTransactionEventListener;
import com.tsurugidb.iceaxe.transaction.exception.TsurugiTransactionException;
import com.tsurugidb.iceaxe.transaction.manager.TsurugiTransactionManager;
import com.tsurugidb.iceaxe.transaction.option.TgTxOption;
import com.tsurugidb.iceaxe.util.IceaxeCloseableSet;
import com.tsurugidb.iceaxe.util.IceaxeIoUtil;
import com.tsurugidb.iceaxe.util.IceaxeTimeout;
import com.tsurugidb.iceaxe.util.TgTimeValue;
import com.tsurugidb.iceaxe.util.function.IoFunction;
import com.tsurugidb.iceaxe.util.function.TsurugiTransactionConsumer;
import com.tsurugidb.tsubakuro.sql.Transaction;
import com.tsurugidb.tsubakuro.util.FutureResponse;

/**
 * Tsurugi Transaction
 */
public class TsurugiTransaction implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(TsurugiTransaction.class);

    private static final AtomicInteger TRANSACTION_COUNT = new AtomicInteger(0);
    private static final AtomicInteger TX_EXECUTE_COUNT = new AtomicInteger(0);

    private final int iceaxeTxId;
    private final TsurugiSession ownerSession;
    private FutureResponse<Transaction> lowTransactionFuture;
    private final TgTxOption txOption;
    private Throwable lowFutureException = null;
    private Transaction lowTransaction;
    private boolean calledGetLowTransaction = false;
    private String transactionId = null;
    private TsurugiTransactionManager ownerTm = null;
    private int iceaxeTmExecuteId = 0;
    private int attempt = 0;
    private final IceaxeTimeout beginTimeout;
    private final IceaxeTimeout commitTimeout;
    private final IceaxeTimeout rollbackTimeout;
    private final IceaxeTimeout closeTimeout;
    private List<TsurugiTransactionEventListener> eventListenerList = null;
    private boolean committed = false;
    private boolean rollbacked = false;
    private final IceaxeCloseableSet closeableSet = new IceaxeCloseableSet();
    private boolean closed = false;

    // internal
    public TsurugiTransaction(TsurugiSession session, FutureResponse<Transaction> lowTransactionFuture, TgTxOption txOption) throws IOException {
        this.iceaxeTxId = TRANSACTION_COUNT.incrementAndGet();
        this.ownerSession = session;
        this.lowTransactionFuture = lowTransactionFuture;
        this.txOption = txOption;
        session.addChild(this);

        var sessionOption = session.getSessionOption();
        this.beginTimeout = new IceaxeTimeout(sessionOption, TgTimeoutKey.TRANSACTION_BEGIN);
        this.commitTimeout = new IceaxeTimeout(sessionOption, TgTimeoutKey.TRANSACTION_COMMIT);
        this.rollbackTimeout = new IceaxeTimeout(sessionOption, TgTimeoutKey.TRANSACTION_ROLLBACK);
        this.closeTimeout = new IceaxeTimeout(sessionOption, TgTimeoutKey.TRANSACTION_CLOSE);

        applyCloseTimeout();
    }

    private void applyCloseTimeout() {
        closeTimeout.apply(lowTransaction);
        closeTimeout.apply(lowTransactionFuture);
    }

    /**
     * get iceaxe transactionId.
     *
     * @return iceaxe transactionId
     */
    public int getIceaxeTxId() {
        return this.iceaxeTxId;
    }

    /**
     * get session.
     *
     * @return session
     */
    @Nonnull
    public TsurugiSession getSession() {
        return this.ownerSession;
    }

    /**
     * get transaction option.
     *
     * @return transaction option
     */
    @Nonnull
    public TgTxOption getTransactionOption() {
        return this.txOption;
    }

    /**
     * set owner information.
     *
     * @param tm                owner transaction manager
     * @param iceaxeTmExecuteId iceaxe tm executeId
     * @param attempt           attempt number
     */
    public void setOwner(TsurugiTransactionManager tm, int iceaxeTmExecuteId, int attempt) {
        this.ownerTm = tm;
        this.iceaxeTmExecuteId = iceaxeTmExecuteId;
        this.attempt = attempt;
    }

    /**
     * get transaction manager.
     *
     * @return transaction manager, null if this transaction is not created by transaction manager
     */
    @Nullable
    public TsurugiTransactionManager getTransactionManager() {
        return this.ownerTm;
    }

    /**
     * get iceaxe tm executeId.
     *
     * @return iceaxe tm executeId, 0 if this transaction is not created by transaction manager
     */
    public int getIceaxeTmExecuteId() {
        return this.iceaxeTmExecuteId;
    }

    /**
     * get attempt number.
     *
     * @return attempt number, 0 if this transaction is not created by transaction manager
     */
    public int getAttempt() {
        return this.attempt;
    }

    /**
     * set transaction-begin-timeout
     *
     * @param time timeout time
     * @param unit timeout unit
     */
    public void setBeginTimeout(long time, TimeUnit unit) {
        setBeginTimeout(TgTimeValue.of(time, unit));
    }

    /**
     * set transaction-begin-timeout
     *
     * @param timeout time
     */
    public void setBeginTimeout(TgTimeValue timeout) {
        beginTimeout.set(timeout);
    }

    /**
     * set transaction-commit-timeout
     *
     * @param time timeout time
     * @param unit timeout unit
     */
    public void setCommitTimeout(long time, TimeUnit unit) {
        setCommitTimeout(TgTimeValue.of(time, unit));
    }

    /**
     * set transaction-commit-timeout
     *
     * @param timeout time
     */
    public void setCommitTimeout(TgTimeValue timeout) {
        commitTimeout.set(timeout);
    }

    /**
     * set transaction-rollback-timeout
     *
     * @param time timeout time
     * @param unit timeout unit
     */
    public void setRollbackTimeout(long time, TimeUnit unit) {
        setRollbackTimeout(TgTimeValue.of(time, unit));
    }

    /**
     * set transaction-rollback-timeout
     *
     * @param timeout time
     */
    public void setRollbackTimeout(TgTimeValue timeout) {
        rollbackTimeout.set(timeout);
    }

    /**
     * set transaction-close-timeout
     *
     * @param time timeout time
     * @param unit timeout unit
     */
    public void setCloseTimeout(long time, TimeUnit unit) {
        setCloseTimeout(TgTimeValue.of(time, unit));
    }

    /**
     * set transaction-close-timeout
     *
     * @param timeout time
     */
    public void setCloseTimeout(TgTimeValue timeout) {
        closeTimeout.set(timeout);

        applyCloseTimeout();
    }

    /**
     * add event listener
     *
     * @param listener event listener
     * @return this
     */
    public TsurugiTransaction addEventListener(TsurugiTransactionEventListener listener) {
        if (this.eventListenerList == null) {
            this.eventListenerList = new ArrayList<>();
        }
        eventListenerList.add(listener);
        return this;
    }

    private void event(Throwable occurred, Consumer<TsurugiTransactionEventListener> action) {
        if (this.eventListenerList != null) {
            try {
                for (var listener : eventListenerList) {
                    action.accept(listener);
                }
            } catch (Throwable e) {
                if (occurred != null) {
                    e.addSuppressed(occurred);
                }
                throw e;
            }
        }
    }

    // internal
    public final TgSessionOption getSessionOption() {
        return ownerSession.getSessionOption();
    }

    // internal
//  @ThreadSafe
    public final synchronized Transaction getLowTransaction() throws IOException {
        this.calledGetLowTransaction = true;
        if (this.lowTransaction == null) {
            if (this.lowFutureException != null) {
                throw new TsurugiIOException(IceaxeErrorCode.TX_LOW_ERROR, lowFutureException);
            }

            LOG.trace("lowTransaction get start");
            event(null, listener -> listener.lowTransactionGetStart(this));
            try {
                this.lowTransaction = IceaxeIoUtil.getAndCloseFuture(lowTransactionFuture, beginTimeout);
            } catch (Throwable e) {
                this.lowFutureException = e;
                event(e, listener -> listener.lowTransactionGetEnd(this, null, e));
                throw e;
            }
            LOG.trace("lowTransaction get end");

            this.lowTransactionFuture = null;
            applyCloseTimeout();

            this.transactionId = lowTransaction.getTransactionId();
            event(null, listener -> listener.lowTransactionGetEnd(this, transactionId, null));
        }
        return this.lowTransaction;
    }

    /**
     * Provides transaction id that is unique to for the duration of the database server's lifetime
     *
     * @return the id String for this transaction
     * @throws IOException
     */
    public String getTransactionId() throws IOException {
        if (this.transactionId == null) {
            var lowTx = getLowTransaction();
            if (this.transactionId == null) {
                this.transactionId = lowTx.getTransactionId();
            }
        }
        return this.transactionId;
    }

    // execute statement

    /**
     * Tsurugi transaction execute method
     */
    public enum TgTxMethod {
        /** execute ddl */
        EXECUTE_DDL("executeDdl"),
        /** execute query */
        EXECUTE_QUERY("executeQuery"),
        /** execute statement */
        EXECUTE_SATTEMENT("executeStatement"),
        /** execute and for each */
        EXECUTE_FOR_EACH("executeAndForEach"),
        /** execute and get list */
        EXECUTE_GET_LIST("executeAndGetList"),
        /** execute and find record */
        EXECUTE_FIND_RECORD("executeAndFindRecord"),
        /** execute and get count */
        EXECUTE_GET_COUNT("executeAndGetCount"),
        /** commit */
        COMMIT("commit"),
        /** rollback */
        ROLLBACK("rollback");

        private final String methodName;

        private TgTxMethod(String methodName) {
            this.methodName = methodName;
        }

        /**
         * get method name.
         *
         * @return method name
         */
        public String getMethodName() {
            return this.methodName;
        }
    }

    private int getNewIceaxeTxExecuteId() {
        return TX_EXECUTE_COUNT.incrementAndGet();
    }

    /**
     * execute ddl.
     *
     * @param sql DDL
     * @throws IOException
     * @throws TsurugiTransactionException
     */
    public void executeDdl(String sql) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_DDL;
        int txExecuteId = getNewIceaxeTxExecuteId();
        try (var ps = ownerSession.createStatement(sql)) {
            event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, null));

            TsurugiStatementResult result = null;
            Throwable occurred = null;
            try (var rs = ps.execute(this)) {
                result = rs;
                rs.getUpdateCount();
            } catch (TsurugiTransactionException e) {
                occurred = e;
                e.setTxMethod(method, txExecuteId);
                throw e;
            } catch (Throwable e) {
                occurred = e;
                throw e;
            } finally {
                var finalResult = result;
                var finalOccurred = occurred;
                event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, null, finalResult, finalOccurred));
            }
        }
    }

    /**
     * execute query.
     *
     * @param <R> result type
     * @param ps  SQL statement
     * @return SQL result ({@link java.io.Closeable})
     * @throws IOException
     * @throws TsurugiTransactionException
     * @see #executeAndForEach(TsurugiSqlQuery, TsurugiTransactionConsumer)
     * @see #executeAndFindRecord(TsurugiSqlQuery)
     * @see #executeAndGetList(TsurugiSqlQuery)
     */
    public <R> TsurugiQueryResult<R> executeQuery(TsurugiSqlQuery<R> ps) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_QUERY;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, null));

        TsurugiQueryResult<R> result = null;
        Throwable occurred = null;
        try {
            result = ps.execute(this);
            return result;
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, null, finalResult, finalOccurred));
        }
    }

    /**
     * execute query.
     *
     * @param <P>       parameter type
     * @param <R>       result type
     * @param ps        SQL statement
     * @param parameter SQL parameter
     * @return SQL result ({@link java.io.Closeable})
     * @throws IOException
     * @throws TsurugiTransactionException
     * @see #executeAndForEach(TsurugiSqlPreparedQuery, P, TsurugiTransactionConsumer)
     * @see #executeAndFindRecord(TsurugiSqlPreparedQuery, P)
     * @see #executeAndGetList(TsurugiSqlPreparedQuery, P)
     */
    public <P, R> TsurugiQueryResult<R> executeQuery(TsurugiSqlPreparedQuery<P, R> ps, P parameter) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_QUERY;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, parameter));

        TsurugiQueryResult<R> result = null;
        Throwable occurred = null;
        try {
            result = ps.execute(this, parameter);
            return result;
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, parameter, finalResult, finalOccurred));
        }
    }

    /**
     * execute statement.
     *
     * @param ps SQL statement
     * @return SQL result ({@link java.io.Closeable})
     * @throws IOException
     * @throws TsurugiTransactionException
     * @see #executeAndGetCount(TsurugiSqlStatement)
     */
    public TsurugiStatementResult executeStatement(TsurugiSqlStatement ps) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_SATTEMENT;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, null));

        TsurugiStatementResult result = null;
        Throwable occurred = null;
        try {
            result = ps.execute(this);
            return result;
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, null, finalResult, finalOccurred));
        }
    }

    /**
     * execute statement.
     *
     * @param <P>       parameter type
     * @param ps        SQL statement
     * @param parameter SQL parameter
     * @return SQL result ({@link java.io.Closeable})
     * @throws IOException
     * @throws TsurugiTransactionException
     * @see #executeAndGetCount(TsurugiSqlPreparedStatement, P)
     */
    public <P> TsurugiStatementResult executeStatement(TsurugiSqlPreparedStatement<P> ps, P parameter) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_SATTEMENT;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, parameter));

        TsurugiStatementResult result = null;
        Throwable occurred = null;
        try {
            result = ps.execute(this, parameter);
            return result;
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, parameter, finalResult, finalOccurred));
        }
    }

    /**
     * execute query.
     *
     * @param <R>       result type
     * @param ps        SQL statement
     * @param parameter SQL parameter
     * @param action    The action to be performed for each record
     * @throws IOException
     * @throws TsurugiTransactionException
     */
    public <R> void executeAndForEach(TsurugiSqlQuery<R> ps, TsurugiTransactionConsumer<R> action) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_FOR_EACH;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, null));

        TsurugiQueryResult<R> result = null;
        Throwable occurred = null;
        try (var rs = ps.execute(this)) {
            result = rs;
            rs.whileEach(action);
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, null, finalResult, finalOccurred));
        }
    }

    /**
     * execute query.
     *
     * @param <P>       parameter type
     * @param <R>       result type
     * @param ps        SQL statement
     * @param parameter SQL parameter
     * @param action    The action to be performed for each record
     * @throws IOException
     * @throws TsurugiTransactionException
     */
    public <P, R> void executeAndForEach(TsurugiSqlPreparedQuery<P, R> ps, P parameter, TsurugiTransactionConsumer<R> action) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_FOR_EACH;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, parameter));

        TsurugiQueryResult<R> result = null;
        Throwable occurred = null;
        try (var rs = ps.execute(this, parameter)) {
            result = rs;
            rs.whileEach(action);
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, parameter, finalResult, finalOccurred));
        }
    }

    /**
     * execute query.
     *
     * @param <R> result type
     * @param ps  SQL statement
     * @return list of record
     * @throws IOException
     * @throws TsurugiTransactionException
     */
    public <R> List<R> executeAndGetList(TsurugiSqlQuery<R> ps) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_GET_LIST;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, null));

        TsurugiQueryResult<R> result = null;
        Throwable occurred = null;
        try (var rs = ps.execute(this)) {
            result = rs;
            return rs.getRecordList();
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, null, finalResult, finalOccurred));
        }
    }

    /**
     * execute query.
     *
     * @param <P>       parameter type
     * @param <R>       result type
     * @param ps        SQL statement
     * @param parameter SQL parameter
     * @return list of record
     * @throws IOException
     * @throws TsurugiTransactionException
     */
    public <P, R> List<R> executeAndGetList(TsurugiSqlPreparedQuery<P, R> ps, P parameter) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_GET_LIST;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, parameter));

        TsurugiQueryResult<R> result = null;
        Throwable occurred = null;
        try (var rs = ps.execute(this, parameter)) {
            result = rs;
            return rs.getRecordList();
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, parameter, finalResult, finalOccurred));
        }
    }

    /**
     * execute query.
     *
     * @param <R> result type
     * @param ps  SQL statement
     * @return record
     * @throws IOException
     * @throws TsurugiTransactionException
     */
    public <R> Optional<R> executeAndFindRecord(TsurugiSqlQuery<R> ps) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_FIND_RECORD;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, null));

        TsurugiQueryResult<R> result = null;
        Throwable occurred = null;
        try (var rs = ps.execute(this)) {
            result = rs;
            return rs.findRecord();
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, null, finalResult, finalOccurred));
        }
    }

    /**
     * execute query.
     *
     * @param <P>       parameter type
     * @param <R>       result type
     * @param ps        SQL statement
     * @param parameter SQL parameter
     * @return record
     * @throws IOException
     * @throws TsurugiTransactionException
     */
    public <P, R> Optional<R> executeAndFindRecord(TsurugiSqlPreparedQuery<P, R> ps, P parameter) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_FIND_RECORD;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, parameter));

        TsurugiQueryResult<R> result = null;
        Throwable occurred = null;
        try (var rs = ps.execute(this, parameter)) {
            result = rs;
            return rs.findRecord();
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, parameter, finalResult, finalOccurred));
        }
    }

    /**
     * execute statement.
     *
     * @param ps SQL statement
     * @return row count
     * @throws IOException
     * @throws TsurugiTransactionException
     */
    public int executeAndGetCount(TsurugiSqlStatement ps) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_GET_COUNT;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, null));

        TsurugiStatementResult result = null;
        Throwable occurred = null;
        try (var rs = ps.execute(this)) {
            result = rs;
            return rs.getUpdateCount();
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, null, finalResult, finalOccurred));
        }
    }

    /**
     * execute statement.
     *
     * @param <P>       parameter type
     * @param ps        SQL statement
     * @param parameter SQL parameter
     * @return row count
     * @throws IOException
     * @throws TsurugiTransactionException
     */
    public <P> int executeAndGetCount(TsurugiSqlPreparedStatement<P> ps, P parameter) throws IOException, TsurugiTransactionException {
        var method = TgTxMethod.EXECUTE_GET_COUNT;
        int txExecuteId = getNewIceaxeTxExecuteId();
        event(null, listener -> listener.executeStart(this, method, txExecuteId, ps, parameter));

        TsurugiStatementResult result = null;
        Throwable occurred = null;
        try (var rs = ps.execute(this, parameter)) {
            result = rs;
            return rs.getUpdateCount();
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(method, txExecuteId);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalResult = result;
            var finalOccurred = occurred;
            event(occurred, listener -> listener.executeEnd(this, method, txExecuteId, ps, parameter, finalResult, finalOccurred));
        }
    }

    // internal
    @FunctionalInterface
    public interface LowTransactionTask<R> {
        R run(Transaction lowTransaction) throws IOException;
    }

    // internal
    public <R> R executeLow(LowTransactionTask<R> task) throws IOException {
        checkClose();
        return task.run(getLowTransaction());
    }

    // commit, rollback

    /**
     * add before-commit listener
     *
     * @param listener listener
     */
    @Deprecated(forRemoval = true)
    public void addBeforeCommitListener(Consumer<TsurugiTransaction> listener) {
        addEventListener(new TsurugiTransactionEventListener() {
            @Override
            public void commitStart(TsurugiTransaction transaction, TgCommitType commitType) {
                listener.accept(transaction);
            }
        });
    }

    /**
     * add commit listener
     *
     * @param listener listener
     */
    @Deprecated(forRemoval = true)
    public void addCommitListener(Consumer<TsurugiTransaction> listener) {
        addEventListener(new TsurugiTransactionEventListener() {
            @Override
            public void commitEnd(TsurugiTransaction transaction, TgCommitType commitType, Throwable occurred) {
                if (occurred == null) {
                    listener.accept(transaction);
                }
            }
        });
    }

    /**
     * add rollback listener
     *
     * @param listener listener
     */
    @Deprecated(forRemoval = true)
    public void addRollbackListener(Consumer<TsurugiTransaction> listener) {
        addEventListener(new TsurugiTransactionEventListener() {
            @Override
            public void rollbackEnd(TsurugiTransaction transaction, Throwable occurred) {
                if (occurred == null) {
                    listener.accept(transaction);
                }
            }
        });
    }

    /**
     * Whether transaction is available
     *
     * @return true: available
     * @throws IOException
     */
//  @ThreadSafe
    public final synchronized boolean available() throws IOException {
        if (isClosed()) {
            return false;
        }
        if (!this.calledGetLowTransaction) {
            getLowTransaction();
        }
        return this.lowTransaction != null;
    }

    /**
     * do commit
     *
     * @param commitType commit type
     * @throws IOException
     * @throws TsurugiTransactionException
     */
    public synchronized void commit(TgCommitType commitType) throws IOException, TsurugiTransactionException {
        checkClose();
        if (this.committed) {
            return;
        }
        if (this.rollbacked) {
            throw new IllegalStateException("rollback has already been called");
        }

        LOG.trace("transaction commit start. commitType={}", commitType);
        event(null, listener -> listener.commitStart(this, commitType));

        Throwable occurred = null;
        try {
            closeableSet.closeInTransaction();
            var lowCommitStatus = commitType.getLowCommitStatus();
            finish(lowTx -> lowTx.commit(lowCommitStatus), commitTimeout);
            this.committed = true;
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(TgTxMethod.COMMIT, 0);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalOccurred = occurred;
            event(occurred, listener -> listener.commitEnd(this, commitType, finalOccurred));
        }

        LOG.trace("transaction commit end");
    }

    /**
     * do rollback
     *
     * @throws IOException
     * @throws TsurugiTransactionException
     */
    public synchronized void rollback() throws IOException, TsurugiTransactionException {
        checkClose();
        if (this.committed || this.rollbacked) {
            return;
        }

        LOG.trace("transaction rollback start");
        event(null, listener -> listener.rollbackStart(this));

        Throwable occurred = null;
        try {
            closeableSet.closeInTransaction();
            finish(Transaction::rollback, rollbackTimeout);
            this.rollbacked = true;
        } catch (TsurugiTransactionException e) {
            occurred = e;
            e.setTxMethod(TgTxMethod.ROLLBACK, 0);
            throw e;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalOccurred = occurred;
            event(occurred, listener -> listener.rollbackEnd(this, finalOccurred));
        }

        LOG.trace("transaction rollback end");
    }

    protected void finish(IoFunction<Transaction, FutureResponse<Void>> finisher, IceaxeTimeout timeout) throws IOException, TsurugiTransactionException {
        var transaction = getLowTransaction();
        var lowResultFuture = finisher.apply(transaction);
        closeTimeout.apply(lowResultFuture);
        IceaxeIoUtil.getAndCloseFutureInTransaction(lowResultFuture, timeout);
    }

    /**
     * get committed
     *
     * @return true: committed
     */
    public synchronized boolean isCommitted() {
        return this.committed;
    }

    /**
     * get rollbacked
     *
     * @return true: rollbacked
     */
    public synchronized boolean isRollbacked() {
        return this.rollbacked;
    }

    // internal
    public void addChild(AutoCloseable closeable) throws IOException {
        checkClose();
        closeableSet.add(closeable);
    }

    // internal
    public void removeChild(AutoCloseable closeable) {
        closeableSet.remove(closeable);
    }

    @Override
    public void close() throws IOException {
        this.closed = true;

//      if (!(this.committed || this.rollbacked)) {
        // commitやrollbackに失敗してもcloseは呼ばれるので、ここでIllegalStateException等を発生させるのは良くない
//      }

        if (LOG.isTraceEnabled()) {
            LOG.trace("transaction close start. committed={}, rollbacked={}", committed, rollbacked);
        }
        Throwable occurred = null;
        try {
            IceaxeIoUtil.close(closeableSet, () -> {
                // not try-finally
                IceaxeIoUtil.close(lowTransaction, lowTransactionFuture);
                ownerSession.removeChild(this);
            });
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            var finalOccurred = occurred;
            event(occurred, listener -> listener.closeTransaction(this, finalOccurred));
        }
        LOG.trace("transaction close end");
    }

    /**
     * Returns the closed state of the transaction.
     *
     * @return {@code true} if the transaction has been closed
     * @see #close()
     */
    public boolean isClosed() {
        return this.closed;
    }

    protected void checkClose() throws IOException {
        if (isClosed()) {
            throw new TsurugiIOException(IceaxeErrorCode.TX_ALREADY_CLOSED);
        }
    }

    @Override
    public String toString() {
        return "TsurugiTransaction(" + txOption + ", iceaxeTxId=" + iceaxeTxId + ", iceaxeTmExecuteId=" + iceaxeTmExecuteId + ", attempt=" + attempt + ", transactionId=" + transactionId + ")";
    }
}
