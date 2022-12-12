package com.tsurugidb.iceaxe.test.timeout;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.tsurugidb.iceaxe.explain.TsurugiExplainHelper;
import com.tsurugidb.iceaxe.session.TgSessionInfo;
import com.tsurugidb.iceaxe.session.TgSessionInfo.TgTimeoutKey;
import com.tsurugidb.iceaxe.session.TsurugiSession;
import com.tsurugidb.iceaxe.statement.TgParameterList;
import com.tsurugidb.iceaxe.statement.TgParameterMapping;
import com.tsurugidb.iceaxe.statement.TsurugiPreparedStatementWithLowPs;
import com.tsurugidb.sql.proto.SqlRequest.Parameter;
import com.tsurugidb.tsubakuro.exception.ServerException;
import com.tsurugidb.tsubakuro.sql.PreparedStatement;
import com.tsurugidb.tsubakuro.sql.SqlClient;
import com.tsurugidb.tsubakuro.sql.StatementMetadata;
import com.tsurugidb.tsubakuro.util.FutureResponse;

/**
 * explain close timeout test
 */
public class DbTimeoutExplainCloseTest extends DbTimetoutTest {

    @BeforeAll
    static void beforeAll() throws IOException {
        var LOG = LoggerFactory.getLogger(DbTimeoutExplainCloseTest.class);
        LOG.debug("init start");

        dropTestTable();
        createTestTable();

        LOG.debug("init end");
    }

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
                info.timeout(TgTimeoutKey.EXPLAIN_CLOSE, 1, TimeUnit.SECONDS);
            }
        });
    }

    @Test
    void timeoutSet() throws IOException {
        testTimeout(new TimeoutModifier() {
            @Override
            public void modifyPs(TsurugiPreparedStatementWithLowPs<?> ps) {
                ps.setExplainCloseTimeout(1, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    protected void clientTask(PipeServerThtread pipeServer, TsurugiSession session, TimeoutModifier modifier) throws Exception {
        var helper = new TsurugiExplainHelper() {
            @Override
            protected FutureResponse<StatementMetadata> explainLow(SqlClient lowSqlClient, PreparedStatement lowPs, List<Parameter> lowParameter) throws IOException {
                var future = super.explainLow(lowSqlClient, lowPs, lowParameter);
                return new FutureResponse<StatementMetadata>() {
                    @Override
                    public boolean isDone() {
                        return future.isDone();
                    }

                    @Override
                    public StatementMetadata get() throws IOException, ServerException, InterruptedException {
                        throw new UnsupportedOperationException("do not use");
                    }

                    @Override
                    public StatementMetadata get(long timeout, TimeUnit unit) throws IOException, ServerException, InterruptedException, TimeoutException {
                        return future.get(timeout, unit);
                    }

                    @Override
                    public void close() throws IOException, ServerException, InterruptedException {
                        pipeServer.setPipeWrite(false);
                        try {
                            future.close();
                        } finally {
                            pipeServer.setPipeWrite(true);
                        }
                    }
                };
            }
        };
        session.setExplainHelper(helper);

        var sql = "select * from " + TEST;
        var parameterMapping = TgParameterMapping.of();
        try (var ps = session.createPreparedQuery(sql, parameterMapping)) {
            session.setExplainHelper(helper);

            var parameter = TgParameterList.of();
            try {
                ps.explain(parameter);
            } catch (IOException e) {
                // EXPLAIN_CLOSEはタイムアウトするような通信処理が無い
//              assertInstanceOf(TimeoutException.class, e.getCause());
//              LOG.trace("timeout success");
//              return;
                throw e;
            }
        }
//      fail("didn't time out");
    }
}
