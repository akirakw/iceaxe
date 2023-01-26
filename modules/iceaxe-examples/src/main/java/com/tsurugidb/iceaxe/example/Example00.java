package com.tsurugidb.iceaxe.example;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import com.tsurugidb.iceaxe.TsurugiConnector;
import com.tsurugidb.iceaxe.metadata.TgTableMetadata;
import com.tsurugidb.iceaxe.result.TgResultMapping;
import com.tsurugidb.iceaxe.session.TgSessionInfo;
import com.tsurugidb.iceaxe.session.TsurugiSession;
import com.tsurugidb.iceaxe.statement.TgParameterList;
import com.tsurugidb.iceaxe.statement.TgParameterMapping;
import com.tsurugidb.iceaxe.statement.TgVariable;
import com.tsurugidb.iceaxe.transaction.function.TsurugiTransactionAction;
import com.tsurugidb.iceaxe.transaction.manager.TgTmSetting;
import com.tsurugidb.iceaxe.transaction.option.TgTxOption;
import com.tsurugidb.tsubakuro.channel.common.connection.UsernamePasswordCredential;

/**
 * Iceaxe example (for Java11)
 */
public class Example00 {

    private static final String TABLE_NAME = "TEST";

    public static void main(String... args) throws IOException {
        // @see Example01Connector
        var endpoint = URI.create("tcp://localhost:12345");
        var connector = TsurugiConnector.createConnector(endpoint);

        // @see Example02Session
        var credential = new UsernamePasswordCredential("user", "password");
        var info = TgSessionInfo.of(credential);
        try (var session = connector.createSession(info)) {
            executeCreateTable(session);
            executeInsert(session);
            executeUpdate(session);
            executeSelect(session);
        }
    }

    /**
     * @see Example11Ddl
     */
    private static void executeCreateTable(TsurugiSession session) throws IOException {
        // DDLはトランザクションの影響を受けない（ロールバックできない）が、トランザクション内で実行する必要がある。
        // DDLの場合は、LTXであってもwritePreserveを指定する必要は無い。
        var setting = TgTmSetting.of(TgTxOption.ofLTX());
        var tm = session.createTransactionManager(setting);

        Optional<TgTableMetadata> metadata = session.findTableMetadata(TABLE_NAME);
        if (metadata.isPresent()) {
            var sql = "drop table " + TABLE_NAME;
            try (var ps = session.createPreparedStatement(sql)) {
                ps.executeAndGetCount(tm);
            }
        }

        var sql = "create table " + TABLE_NAME + " (\n" //
                + "  foo int primary key,\n" //
                + "  bar bigint,\n" //
                + "  zzz varchar(10)\n" //
                + ")";
        try (var ps = session.createPreparedStatement(sql)) {
            ps.executeAndGetCount(tm);
        }
    }

    /**
     * @see Example21Insert
     */
    private static void executeInsert(TsurugiSession session) throws IOException {
        // 更新系のSQLをLTXで実行する場合は、更新対象のテーブル名をwritePreserveに指定する必要がある。
        var setting = TgTmSetting.ofAlways(TgTxOption.ofLTX(TABLE_NAME));
        var tm = session.createTransactionManager(setting);

        var sql = "insert into " + TABLE_NAME + "(foo, bar, zzz) values(:foo, :bar, :zzz)";
        var parameterMapping = TgParameterMapping.of(TestEntity.class) //
                .int4("foo", TestEntity::getFoo) //
                .int8("bar", TestEntity::getBar) //
                .character("zzz", TestEntity::getZzz);
        try (var ps = session.createPreparedStatement(sql, parameterMapping)) {
            tm.execute((TsurugiTransactionAction) transaction -> {
                for (int i = 0; i < 10; i++) {
                    var entity = new TestEntity(i, Long.valueOf(i), "z" + i);
                    ps.executeAndGetCount(transaction, entity);
                }
            });
        }
    }

    /**
     * @see Example41Update
     */
    private static void executeUpdate(TsurugiSession session) throws IOException {
        // 更新系のSQLをLTXで実行する場合は、更新対象のテーブル名をwritePreserveに指定する必要がある。
        var setting = TgTmSetting.ofAlways(TgTxOption.ofLTX(TABLE_NAME));
        var tm = session.createTransactionManager(setting);

        var foo = TgVariable.ofInt4("foo");
        var bar = TgVariable.ofInt8("bar");
        var sql = "update " + TABLE_NAME + " set bar=" + bar + " where foo=" + foo;
        var parameterMapping = TgParameterMapping.of(foo, bar);
        try (var ps = session.createPreparedStatement(sql, parameterMapping)) {
            tm.execute((TsurugiTransactionAction) transaction -> {
                for (int i = 0; i < 10; i += 2) {
                    var parameter = TgParameterList.of(foo.bind(i), bar.bind(i + 1));
                    ps.executeAndGetCount(transaction, parameter);
                }
            });
        }
    }

    /**
     * @see Example31Select
     */
    private static void executeSelect(TsurugiSession session) throws IOException {
        var setting = TgTmSetting.ofAlways(TgTxOption.ofOCC());
        var tm = session.createTransactionManager(setting);

        var sql = "select foo, bar, zzz from " + TABLE_NAME;
        var resultMapping = TgResultMapping.of(TestEntity::new) //
                .int4("foo", TestEntity::setFoo) //
                .int8("bar", TestEntity::setBar) //
                .character("zzz", TestEntity::setZzz);
        try (var ps = session.createPreparedQuery(sql, resultMapping)) {
            tm.execute(transaction -> {
                List<TestEntity> list = ps.executeAndGetList(transaction);
                for (var entity : list) {
                    System.out.println(entity);
                }
            });
        }
    }
}
