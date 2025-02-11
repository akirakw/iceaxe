package com.tsurugidb.iceaxe.util;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import com.tsurugidb.iceaxe.exception.TsurugiIOException;
import com.tsurugidb.iceaxe.transaction.exception.TsurugiTransactionException;
import com.tsurugidb.iceaxe.util.function.IoRunnable;
import com.tsurugidb.tsubakuro.exception.ServerException;
import com.tsurugidb.tsubakuro.util.FutureResponse;

/**
 * Iceaxe I/O utility.
 */
public final class IceaxeIoUtil {

    private IceaxeIoUtil() {
        // don't instantiate
    }

    /**
     * get value from future.
     *
     * @param <V>     the result value type
     * @param future  future
     * @param timeout the maximum time to wait
     * @return result value
     * @throws IOException          if an I/O error occurs while processing the request
     * @throws InterruptedException if interrupted while processing the request
     */
    public static <V> V getAndCloseFuture(FutureResponse<V> future, IceaxeTimeout timeout) throws IOException, InterruptedException {
        return getAndCloseFuture(future, timeout, TsurugiIOException::new);
    }

    /**
     * get value from future in transaction.
     *
     * @param <V>     the result value type
     * @param future  future
     * @param timeout the maximum time to wait
     * @return result value
     * @throws IOException                 if an I/O error occurs while processing the request
     * @throws InterruptedException        if interrupted while processing the request
     * @throws TsurugiTransactionException if server error occurs while processing the request
     */
    public static <V> V getAndCloseFutureInTransaction(FutureResponse<V> future, IceaxeTimeout timeout) throws IOException, InterruptedException, TsurugiTransactionException {
        return getAndCloseFuture(future, timeout, TsurugiTransactionException::new);
    }

    private static <V, E extends Exception> V getAndCloseFuture(FutureResponse<V> future, IceaxeTimeout timeout, Function<ServerException, E> serverExceptionWrapper)
            throws IOException, InterruptedException, E {
        Throwable occurred = null;
        try {
            var time = timeout.get();
            long value = time.value();
            TimeUnit unit = time.unit();
            return future.get(value, unit);
        } catch (ServerException e) {
            E wrapper = serverExceptionWrapper.apply(e);
            occurred = wrapper;
            throw wrapper;
        } catch (TimeoutException e) {
            var ioe = new IOException(e.getMessage(), e);
            occurred = ioe;
            throw ioe;
        } catch (Throwable e) {
            occurred = e;
            throw e;
        } finally {
            try {
                future.close();
            } catch (ServerException e) {
                E wrapper = serverExceptionWrapper.apply(e);
                if (occurred != null) {
                    occurred.addSuppressed(wrapper);
                } else {
                    throw wrapper;
                }
            } catch (Throwable e) {
                if (occurred != null) {
                    occurred.addSuppressed(e);
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Closeable for FutureResponse.
     */
    public interface IceaxeFutureResponseCloseable extends AutoCloseable {
        @Override
        void close() throws IOException, InterruptedException /* , TsurugiIOException */;
    }

    /**
     * wrap with Closeable.
     *
     * @param future future
     * @return Closeable
     */
    public static IceaxeFutureResponseCloseable closeable(FutureResponse<?> future) {
        return () -> {
            // ServerException -> TsurugiIOException
            IceaxeIoUtil.close(future);
        };
    }

    /**
     * close resources.
     *
     * @param closeableSet Closeable set
     * @param runnable     close action
     * @throws IOException          if an I/O error occurs while disposing the resources
     * @throws InterruptedException if interrupted while disposing the resources
     */
    public static void close(IceaxeCloseableSet closeableSet, IoRunnable runnable) throws IOException, InterruptedException {
        List<Throwable> saveList = closeableSet.close();

        try {
            runnable.run();
        } catch (Exception e) {
            for (var save : saveList) {
                var s = (save instanceof ServerException) ? new TsurugiIOException((ServerException) save) : save;
                e.addSuppressed(s);
            }
            throw e;
        }

        IOException e = null;
        for (var save : saveList) {
            var s = (save instanceof ServerException) ? new TsurugiIOException((ServerException) save) : save;
            if (e == null) {
                if (s instanceof IOException) {
                    e = (IOException) s;
                } else {
                    e = new IOException(s.getMessage(), s);
                }
            } else {
                e.addSuppressed(s);
            }
        }
        if (e != null) {
            throw e;
        }
    }

    /**
     * close resources.
     *
     * @param closeables AutoCloseable
     * @throws IOException          if an I/O error occurs while disposing the resources
     * @throws InterruptedException if interrupted while disposing the resources
     */
    public static void close(AutoCloseable... closeables) throws IOException, InterruptedException {
        close(closeables, TsurugiIOException.class, TsurugiIOException::new);
    }

    /**
     * close resources in transaction.
     *
     * @param closeables AutoCloseable
     * @throws IOException                 if an I/O error occurs while disposing the resources
     * @throws InterruptedException        if interrupted while disposing the resources
     * @throws TsurugiTransactionException if server error occurs while disposing the resources
     */
    public static void closeInTransaction(AutoCloseable... closeables) throws IOException, InterruptedException, TsurugiTransactionException {
        close(closeables, TsurugiTransactionException.class, TsurugiTransactionException::new);
    }

    private static <E extends Exception> void close(AutoCloseable[] closeables, Class<E> classE, Function<ServerException, E> serverExceptionWrapper) throws IOException, InterruptedException, E {
        Throwable occurred = null;

        for (var closeable : closeables) {
            if (closeable == null) {
                continue;
            }

            try {
                closeable.close();
            } catch (ServerException e) {
                var wrapper = serverExceptionWrapper.apply(e);
                if (occurred == null) {
                    occurred = wrapper;
                } else {
                    occurred.addSuppressed(wrapper);
                }
            } catch (IOException | InterruptedException | RuntimeException | Error e) {
                if (occurred == null) {
                    occurred = e;
                } else {
                    occurred.addSuppressed(e);
                }
            } catch (Throwable e) {
                if (occurred == null) {
                    occurred = new IOException(e.getMessage(), e);
                } else {
                    occurred.addSuppressed(e);
                }
            }
        }

        if (occurred != null) {
            if (classE.isInstance(occurred)) {
                throw classE.cast(occurred);
            }
            if (occurred instanceof RuntimeException) {
                throw (RuntimeException) occurred;
            }
            if (occurred instanceof Error) {
                throw (Error) occurred;
            }
            if (occurred instanceof InterruptedException) {
                throw (InterruptedException) occurred;
            }
            throw (IOException) occurred;
        }
    }
}
