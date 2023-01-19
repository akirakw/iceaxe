package com.tsurugidb.iceaxe.test.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.tsurugidb.iceaxe.test.util.DbTestTableTester;

/**
 * system reserved word test
 *
 * <ul>
 * <li>アンダースコア2個で始まるテーブル名やカラム名はシステムで予約されており、ユーザーは使用することが出来ない。</li>
 * </ul>
 */
class DbSystemReservedWordTest extends DbTestTableTester {

    @BeforeEach
    void beforeEach(TestInfo info) throws IOException {
        LOG.debug("{} init start", info.getDisplayName());

        dropTestTable();

        LOG.debug("{} init end", info.getDisplayName());
    }

    @Test
    void createTable() throws IOException {
        // TODO アンダースコア2個で始まるテーブル名はシステム予約でありユーザーが使用できないので、使ったらエラーになるべき
        String tableName = "__test";
        dropTable(tableName);
        var sql = "create table " + tableName //
                + "(" //
                + "  foo int," //
                + "  bar bigint," //
                + "  zzz varchar(10)" //
                + ")";
        executeDdl(getSession(), sql);
    }

    @Test
    void createTableColumn() throws IOException {
        // TODO アンダースコア2個で始まるカラム名はシステム予約でありユーザーが使用できないので、使ったらエラーになるべき
        var sql = "create table " + TEST //
                + "(" //
                + "  __foo int," //
                + "  __bar bigint," //
                + "  __zzz varchar(10)" //
                + ")";
        executeDdl(getSession(), sql);
    }

    @Test
    void selectAs() throws IOException {
        int size = 4;
        createTestTable();
        insertTestTable(size);

        // TODO アンダースコア2個で始まるカラム名はシステム予約でありユーザーが使用できないので、使ったらエラーになるべき
        var sql = "select foo as __foo from " + TEST + " order by foo";
        var session = getSession();
        var tm = createTransactionManagerOcc(session);
        try (var ps = session.createPreparedQuery(sql)) {
            var list = ps.executeAndGetList(tm);
            assertEquals(size, list.size());
            int i = 0;
            for (var entity : list) {
                assertEquals(i++, entity.getInt4("__foo"));
            }
        }
    }
}
