package com.tsurugi.iceaxe.result;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Function;

import com.nautilus_technologies.tsubakuro.low.sql.ResultSet;
import com.nautilus_technologies.tsubakuro.protos.ResponseProtos.ResultOnly;
import com.tsurugi.iceaxe.statement.TsurugiPreparedStatement;
import com.tsurugi.iceaxe.util.IceaxeIoUtil;

/**
 * Tsurugi Result Set for PreparedStatement
 * <p>
 * MT unsafe
 * </p>
 * 
 * @param <R> record type
 */
public class TsurugiResultSet<R> extends TsurugiResult implements Iterable<R> {

    private Future<ResultSet> lowResultSetFuture;
    private ResultSet lowResultSet;
    private final Function<TsurugiResultRecord, R> recordConverter;
    private TsurugiResultRecord record;

    // internal
    public TsurugiResultSet(TsurugiPreparedStatement preparedStatement, Future<ResultSet> lowResultSetFuture, Future<ResultOnly> lowResultOnlyFuture,
            Function<TsurugiResultRecord, R> recordConverter) {
        super(preparedStatement, lowResultOnlyFuture);
        this.lowResultSetFuture = lowResultSetFuture;
        this.recordConverter = recordConverter;
    }

    protected synchronized final ResultSet getLowResultSet() throws IOException {
        if (this.lowResultSet == null) {
            var info = getSessionInfo();
            this.lowResultSet = IceaxeIoUtil.getFromFuture(lowResultSetFuture, info);
            this.lowResultSetFuture = null;
        }
        return this.lowResultSet;
    }

    /**
     * get name list
     * 
     * @return list of column name
     * @throws IOException
     */
    public List<String> getNameList() throws IOException {
        return getNameList(getLowResultSet());
    }

    static List<String> getNameList(ResultSet lowResultSet) throws IOException {
        var lowMeta = lowResultSet.getRecordMeta();
        var size = lowMeta.fieldCount();
        var list = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) {
            var name = lowMeta.name(i);
            list.add(name);
        }
        return list;
    }

    /**
     * get one record
     * 
     * @return record
     * @throws IOException
     */
    public Optional<R> findRecord() throws IOException {
        var lowResultSet = getLowResultSet();
        try {
            if (lowResultSet.nextRecord()) {
                var record = getRecord();
                record.reset();
                var r = recordConverter.apply(record);
                return Optional.of(r);
            } else {
                return Optional.empty();
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    protected TsurugiResultRecord getRecord() throws IOException {
        if (this.record == null) {
            var lowResultSet = getLowResultSet();
            this.record = new TsurugiResultRecord(lowResultSet);
        }
        return this.record;
    }

    /**
     * @throws UncheckedIOException
     */
    @Override
    public Iterator<R> iterator() {
        try {
            var record = getRecord();
            return new TsurugiResultSetIterator(record);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected class TsurugiResultSetIterator implements Iterator<R> {
        private final TsurugiResultRecord record;
        private boolean moveNext = true;
        private boolean hasNext;

        public TsurugiResultSetIterator(TsurugiResultRecord record) {
            this.record = record;
        }

        protected void moveNext() {
            if (this.moveNext) {
                try {
                    this.hasNext = lowResultSet.nextRecord();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (InterruptedException e) {
                    throw new UncheckedIOException(new IOException(e));
                } finally {
                    record.reset();
                }
                this.moveNext = false;
            }
        }

        @Override
        public boolean hasNext() {
            moveNext();
            return this.hasNext;
        }

        @Override
        public R next() {
            moveNext();
            if (!this.hasNext) {
                throw new NoSuchElementException();
            }
            var r = recordConverter.apply(record);
            this.moveNext = true;
            return r;
        }
    }

    @Override
    public void close() throws IOException {
        // not try-finally
        getLowResultSet().close();
        super.close();
    }
}
