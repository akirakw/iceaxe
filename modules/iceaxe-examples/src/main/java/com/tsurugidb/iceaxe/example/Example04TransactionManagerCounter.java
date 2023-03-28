package com.tsurugidb.iceaxe.example;

import java.io.IOException;
import java.util.Optional;

import com.tsurugidb.iceaxe.session.TsurugiSession;
import com.tsurugidb.iceaxe.transaction.manager.TgTmSetting;
import com.tsurugidb.iceaxe.transaction.manager.event.counter.TgTmCount;
import com.tsurugidb.iceaxe.transaction.manager.event.counter.TgTmLabelCounter;
import com.tsurugidb.iceaxe.transaction.manager.event.counter.TgTmSimpleCounter;
import com.tsurugidb.iceaxe.transaction.option.TgTxOption;

/**
 * TsurugiTransactionManager counter example
 */
public class Example04TransactionManagerCounter {

    private static final TgTxOption OCC = TgTxOption.ofOCC().label("label1");
    private static final TgTxOption LTX = TgTxOption.ofLTX("table1", "table2").label("label2");

    /**
     * @see Example04TmSetting
     */
    private static final TgTmSetting SETTING = TgTmSetting.of(OCC, LTX);

    void simpleCounter(TsurugiSession session) throws IOException {
        var counter = new TgTmSimpleCounter();

        var tm = session.createTransactionManager(SETTING);
        tm.addEventListener(counter);

        tm.execute(transaction -> {
            // execute sql
        });

        TgTmCount count = counter.getCount();
        System.out.println(count);
    }

    void labelCounter(TsurugiSession session) throws IOException {
        var counter = new TgTmLabelCounter();

        var tm = session.createTransactionManager(SETTING);
        tm.addEventListener(counter);

        tm.execute(transaction -> {
            // execute sql
        });

        Optional<TgTmCount> count1 = counter.findCount("label1");
        System.out.println(count1);
    }

    void useSetting(TsurugiSession session) throws IOException {
        var counter = new TgTmSimpleCounter();

        var tm = session.createTransactionManager();
        var setting = TgTmSetting.ofAlways(OCC);
        setting.addEventListener(counter);

        tm.execute(setting, transaction -> {
            // execute sql
        });

        TgTmCount count = counter.getCount();
        System.out.println(count);
    }
}
