package com.tsurugidb.iceaxe.test.insert;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.tsurugidb.iceaxe.exception.IceaxeErrorCode;
import com.tsurugidb.iceaxe.exception.TsurugiIOException;
import com.tsurugidb.iceaxe.test.util.DbTestTableTester;
import com.tsurugidb.iceaxe.test.util.TestEntity;
import com.tsurugidb.iceaxe.transaction.TgTxOption;
import com.tsurugidb.iceaxe.transaction.exception.TsurugiTransactionException;
import com.tsurugidb.tsubakuro.sql.SqlServiceCode;

/**
 * insert error test
 */
class DbInsertErrorTest extends DbTestTableTester {

    @BeforeEach
    void beforeEach(TestInfo info) throws IOException {
        LOG.debug("{} init start", info.getDisplayName());

        dropTestTable();
        if (!info.getDisplayName().equals("insertNullToNotNull()")) {
            createTestTable();
        }

        LOG.debug("{} init end", info.getDisplayName());
    }

    @Test
    void insertNullToPK() throws IOException {
        var sql = "insert into " + TEST //
                + "(" + TEST_COLUMNS + ")" //
                + "values(null, 456, 'abc')";

        var session = getSession();
        var tm = createTransactionManagerOcc(session);
        try (var ps = session.createPreparedStatement(sql)) {
            var e = assertThrows(TsurugiIOException.class, () -> ps.executeAndGetCount(tm));
            assertEqualsCode(SqlServiceCode.ERR_INTEGRITY_CONSTRAINT_VIOLATION, e);
            assertTrue(e.getMessage().contains("TODO"), () -> "actual=" + e.getMessage()); // TODO エラー詳細情報の確認
        }

        assertEqualsTestTable(0);
    }

    @Test
    void insertNullToNotNull() throws IOException {
        var session = getSession();

        var createSql = "create table " + TEST //
                + "(" //
                + "  foo int not null," //
                + "  bar bigint not null," //
                + "  zzz varchar(10) not null," //
                + "  primary key(foo)" //
                + ")";
        executeDdl(session, createSql);

        var tm = createTransactionManagerOcc(session);
        try (var ps = session.createPreparedStatement(INSERT_SQL, INSERT_MAPPING)) {
            var e = assertThrows(TsurugiIOException.class, () -> {
                var entity = new TestEntity(123, null, null);
                ps.executeAndGetCount(tm, entity);
            });
            assertEqualsCode(SqlServiceCode.ERR_INTEGRITY_CONSTRAINT_VIOLATION, e);
            assertTrue(e.getMessage().contains("TODO"), () -> "actual=" + e.getMessage()); // TODO エラー詳細情報の確認
        }

        assertEqualsTestTable(0);
    }

    @Test
    void ps0ExecuteAfterClose() throws IOException {
        var sql = "insert into " + TEST //
                + "(" + TEST_COLUMNS + ")" //
                + "values(1, 1, '1')";

        var session = getSession();
        var tm = createTransactionManagerOcc(session);
        var ps = session.createPreparedStatement(sql);
        ps.close();
        var e = assertThrows(TsurugiIOException.class, () -> {
            ps.executeAndGetCount(tm);
        });
        assertEqualsCode(IceaxeErrorCode.PS_ALREADY_CLOSED, e);
    }

    @Test
    void ps1ExecuteAfterClose() throws IOException {
        var session = getSession();
        var tm = createTransactionManagerOcc(session);
        var ps = session.createPreparedStatement(INSERT_SQL, INSERT_MAPPING);
        ps.close();
        var entity = createTestEntity(1);
        var e = assertThrows(TsurugiIOException.class, () -> {
            ps.executeAndGetCount(tm, entity);
        });
        assertEqualsCode(IceaxeErrorCode.PS_ALREADY_CLOSED, e);
    }

    @Test
    void ps0ExecuteAfterTxClose() throws IOException {
        var sql = "insert into " + TEST //
                + "(" + TEST_COLUMNS + ")" //
                + "values(1, 1, '1')";

        var session = getSession();
        try (var ps = session.createPreparedStatement(sql)) {
            var transaction = session.createTransaction(TgTxOption.ofOCC());
            transaction.close();
            var e = assertThrows(TsurugiTransactionException.class, () -> {
                ps.executeAndGetCount(transaction);
            });
            assertEqualsCode(null, e); // TODO エラーコード
        }
    }
}
