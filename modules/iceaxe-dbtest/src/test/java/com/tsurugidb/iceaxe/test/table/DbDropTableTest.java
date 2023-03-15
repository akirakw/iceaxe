package com.tsurugidb.iceaxe.test.table;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.tsurugidb.iceaxe.test.util.DbTestTableTester;
import com.tsurugidb.iceaxe.transaction.exception.TsurugiTransactionIOException;
import com.tsurugidb.tsubakuro.sql.SqlServiceCode;

/**
 * drop table test
 */
class DbDropTableTest extends DbTestTableTester {

    @BeforeEach
    void beforeEach(TestInfo info) throws IOException {
        LOG.debug("{} init start", info.getDisplayName());

        dropTestTable();

        LOG.debug("{} init end", info.getDisplayName());
    }

    private static final String SQL = "drop table " + TEST;

    @Test
    void drop() throws IOException {
        createTestTable();

        var session = getSession();
        var tm = createTransactionManagerOcc(session);
        try (var ps = session.createStatement(SQL)) {
            tm.executeAndGetCount(ps);
        }
    }

    @Test
    void dropNotFound() throws IOException {
        var session = getSession();
        var tm = createTransactionManagerOcc(session);
        try (var ps = session.createStatement(SQL)) {
            var e = assertThrowsExactly(TsurugiTransactionIOException.class, () -> {
                tm.executeAndGetCount(ps);
            });
            assertEqualsCode(SqlServiceCode.ERR_COMPILER_ERROR, e);
            assertContains("table_not_found table `test' is not found", e.getMessage());
        }
    }

    @Test
    void rollback() throws IOException {
        createTestTable();

        var session = getSession();
        assertTrue(session.findTableMetadata(TEST).isPresent());

        var tm = createTransactionManagerOcc(session);
        try (var ps = session.createStatement(SQL)) {
            tm.execute(transaction -> {
                transaction.executeAndGetCount(ps);
                assertTrue(session.findTableMetadata(TEST).isEmpty());
                transaction.rollback();
                assertTrue(session.findTableMetadata(TEST).isEmpty());
            });
        }

        assertTrue(session.findTableMetadata(TEST).isEmpty());
    }
}
