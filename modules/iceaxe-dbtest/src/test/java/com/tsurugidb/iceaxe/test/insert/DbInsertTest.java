package com.tsurugidb.iceaxe.test.insert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.tsurugidb.iceaxe.statement.TgParameterList;
import com.tsurugidb.iceaxe.statement.TgParameterMapping;
import com.tsurugidb.iceaxe.statement.TgVariable;
import com.tsurugidb.iceaxe.statement.TgVariableList;
import com.tsurugidb.iceaxe.test.util.DbTestTableTester;
import com.tsurugidb.iceaxe.test.util.TestEntity;

/**
 * insert test
 */
class DbInsertTest extends DbTestTableTester {

    @BeforeEach
    void beforeEach(TestInfo info) throws IOException {
        LOG.debug("{} init start", info.getDisplayName());

        dropTestTable();
        createTestTable();

        LOG.debug("{} init end", info.getDisplayName());
    }

    @Test
    void insertConstant() throws IOException {
        var entity = new TestEntity(123, 456, "abc");

        var sql = "insert into " + TEST //
                + "(" + TEST_COLUMNS + ")" //
                + "values(" + entity.getFoo() + ", " + entity.getBar() + ", '" + entity.getZzz() + "')";

        var session = getSession();
        var tm = createTransactionManagerOcc(session);
        try (var ps = session.createPreparedStatement(sql)) {
            int count = ps.executeAndGetCount(tm);
            assertEquals(-1, count); // TODO 1
        }

        assertEqualsTestTable(entity);
    }

    @Test
    void insertByVariableList() throws IOException {
        var entity = new TestEntity(123, 456, "abc");

        var sql = "insert into " + TEST //
                + "(" + TEST_COLUMNS + ")" //
                + "values(:foo, :bar, :zzz)";
        var vlist = TgVariableList.of() //
                .int4("foo") //
                .int8("bar") //
                .character("zzz");
        var parameterMapping = TgParameterMapping.of(vlist);

        var session = getSession();
        var tm = createTransactionManagerOcc(session);
        try (var ps = session.createPreparedStatement(sql, parameterMapping)) {
            var plist = TgParameterList.of() //
                    .int4("foo", entity.getFoo()) //
                    .int8("bar", entity.getBar()) //
                    .character("zzz", entity.getZzz());
            int count = ps.executeAndGetCount(tm, plist);
            assertEquals(-1, count); // TODO 1
        }

        assertEqualsTestTable(entity);
    }

    @Test
    void insertByBind() throws IOException {
        var entity = new TestEntity(123, 456, "abc");

        var foo = TgVariable.ofInt4("foo");
        var bar = TgVariable.ofInt8("bar");
        var zzz = TgVariable.ofCharacter("zzz");

        var sql = "insert into " + TEST //
                + "(" + TEST_COLUMNS + ")" //
                + "values(" + foo + ", " + bar + ", " + zzz + ")";
        var parameterMapping = TgParameterMapping.of(foo, bar, zzz);

        var session = getSession();
        var tm = createTransactionManagerOcc(session);
        try (var ps = session.createPreparedStatement(sql, parameterMapping)) {
            var plist = TgParameterList.of( //
                    foo.bind(entity.getFoo()), //
                    bar.bind(entity.getBar()), //
                    zzz.bind(entity.getZzz()));
            int count = ps.executeAndGetCount(tm, plist);
            assertEquals(-1, count); // TODO 1
        }

        assertEqualsTestTable(entity);
    }

    @Test
    void insertByEntity() throws IOException {
        var entity = new TestEntity(123, 456, "abc");

        var sql = "insert into " + TEST //
                + "(" + TEST_COLUMNS + ")" //
                + "values(:foo, :bar, :zzz)";
        var parameterMapping = TgParameterMapping.of(TestEntity.class) //
                .int4("foo", TestEntity::getFoo) //
                .int8("bar", TestEntity::getBar) //
                .character("zzz", TestEntity::getZzz);

        var session = getSession();
        var tm = createTransactionManagerOcc(session);
        try (var ps = session.createPreparedStatement(sql, parameterMapping)) {
            int count = ps.executeAndGetCount(tm, entity);
            assertEquals(-1, count); // TODO 1
        }

        assertEqualsTestTable(entity);
    }

    @Test
    void insertMany() throws IOException {
        var entityList = List.of( //
                new TestEntity(123, 456, "abc"), //
                new TestEntity(234, 789, "def"));

        var sql = "insert into " + TEST //
                + "(" + TEST_COLUMNS + ")" //
                + "values(:foo, :bar, :zzz)";
        var parameterMapping = TgParameterMapping.of(TestEntity.class) //
                .int4("foo", TestEntity::getFoo) //
                .int8("bar", TestEntity::getBar) //
                .character("zzz", TestEntity::getZzz);

        var session = getSession();
        var tm = createTransactionManagerOcc(session);
        try (var ps = session.createPreparedStatement(sql, parameterMapping)) {
            tm.execute(transaction -> {
                for (var entity : entityList) {
                    int count = ps.executeAndGetCount(tm, entity);
                    assertEquals(-1, count); // TODO 1
                }
            });
        }

        assertEqualsTestTable(entityList);
    }
}
