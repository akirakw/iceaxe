package com.tsurugidb.iceaxe.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tsurugidb.iceaxe.statement.TgVariable.TgVariableBigDecimal;

class TgParameterListTest {

    @Test
    void testOf() {
        var list = TgParameterList.of();

        assertParameterList(List.of(), list);
    }

    @Test
    void testOfTgParameterArray() {
        var foo = TgVariable.ofInt4("foo");
        var list = TgParameterList.of(foo.bind(123));

        assertParameterList(TgParameter.of("foo", 123), list);
    }

    @Test
    void testBoolStringBoolean() {
        var list = TgParameterList.of();
        list.bool("foo", true);

        assertParameterList(TgParameter.of("foo", true), list);
    }

    @Test
    void testBoolStringBooleanWrapper() {
        var list = TgParameterList.of();
        list.bool("foo", Boolean.TRUE);
        list.bool("bar", null);

        assertParameterList(TgParameter.of("foo", Boolean.TRUE), TgParameter.of("bar", (Boolean) null), list);
    }

    @Test
    void testInt4StringInt() {
        var list = TgParameterList.of();
        list.int4("foo", 123);

        assertParameterList(TgParameter.of("foo", 123), list);
    }

    @Test
    void testInt4StringInteger() {
        var list = TgParameterList.of();
        list.int4("foo", Integer.valueOf(123));
        list.int4("bar", null);

        assertParameterList(TgParameter.of("foo", 123), TgParameter.of("bar", (Integer) null), list);
    }

    @Test
    void testInt8StringLong() {
        var list = TgParameterList.of();
        list.int8("foo", 123);

        assertParameterList(TgParameter.of("foo", 123L), list);
    }

    @Test
    void testInt8StringLongWrapper() {
        var list = TgParameterList.of();
        list.int8("foo", Long.valueOf(123));
        list.int8("bar", null);

        assertParameterList(TgParameter.of("foo", 123L), TgParameter.of("bar", (Long) null), list);
    }

    @Test
    void testFloat4StringFloat() {
        var list = TgParameterList.of();
        list.float4("foo", 123);

        assertParameterList(TgParameter.of("foo", 123f), list);
    }

    @Test
    void testFloat4StringFloatWrapper() {
        var list = TgParameterList.of();
        list.float4("foo", Float.valueOf(123));
        list.float4("bar", null);

        assertParameterList(TgParameter.of("foo", 123f), TgParameter.of("bar", (Float) null), list);
    }

    @Test
    void testFloat8StringDouble() {
        var list = TgParameterList.of();
        list.float8("foo", 123);

        assertParameterList(TgParameter.of("foo", 123d), list);
    }

    @Test
    void testFloat8StringDoubleWrapper() {
        var list = TgParameterList.of();
        list.float8("foo", Double.valueOf(123));
        list.float8("bar", null);

        assertParameterList(TgParameter.of("foo", 123d), TgParameter.of("bar", (Double) null), list);
    }

    @Test
    void testDecimal() {
        var list = TgParameterList.of();
        list.decimal("foo", BigDecimal.valueOf(123));
        list.decimal("bar", null);

        assertParameterList(TgParameter.of("foo", BigDecimal.valueOf(123)), TgParameter.of("bar", (BigDecimal) null), list);
    }

    @Test
    void testDecimalScale() {
        var list = TgParameterList.of();
        list.decimal("p", new BigDecimal("1.01"), 1);
        list.decimal("m", new BigDecimal("-1.01"), 1);
        list.decimal("bar", null, 1);

        var mode = TgVariableBigDecimal.DEFAULT_ROUNDING_MODE;
        assertParameterList(TgParameter.of("p", new BigDecimal("1.01").setScale(1, mode)), TgParameter.of("m", new BigDecimal("-1.01").setScale(1, mode)), TgParameter.of("bar", (BigDecimal) null),
                list);
    }

    @ParameterizedTest
    @ValueSource(strings = { "FLOOR", "DOWN", "HALF_UP" })
    void testDecimalRoundingMode(RoundingMode mode) {
        var list = TgParameterList.of();
        list.decimal("p", new BigDecimal("1.05"), 1, mode);
        list.decimal("m", new BigDecimal("-1.05"), 1, mode);
        list.decimal("bar", null, 1, mode);

        assertParameterList(TgParameter.of("p", new BigDecimal("1.05").setScale(1, mode)), TgParameter.of("m", new BigDecimal("-1.05").setScale(1, mode)), TgParameter.of("bar", (BigDecimal) null),
                list);
    }

    @Test
    void testCharacter() {
        var list = TgParameterList.of();
        list.character("foo", "abc");
        list.character("bar", null);

        assertParameterList(TgParameter.of("foo", "abc"), TgParameter.of("bar", (String) null), list);
    }

    @Test
    void testBytes() {
        var list = TgParameterList.of();
        list.bytes("foo", new byte[] { 1, 2, 3 });
        list.bytes("bar", null);

        assertParameterList(TgParameter.of("foo", new byte[] { 1, 2, 3 }), TgParameter.of("bar", (byte[]) null), list);
    }

    @Test
    void testBits() {
        var list = TgParameterList.of();
        list.bits("foo", new boolean[] { true, false, true });
        list.bits("bar", null);

        assertParameterList(TgParameter.of("foo", new boolean[] { true, false, true }), TgParameter.of("bar", (boolean[]) null), list);
    }

    @Test
    void testDate() {
        var list = TgParameterList.of();
        list.date("foo", LocalDate.of(2022, 6, 30));
        list.date("bar", null);

        assertParameterList(TgParameter.of("foo", LocalDate.of(2022, 6, 30)), TgParameter.of("bar", (LocalDate) null), list);
    }

    @Test
    void testTime() {
        var list = TgParameterList.of();
        list.time("foo", LocalTime.of(23, 30, 59));
        list.time("bar", null);

        assertParameterList(TgParameter.of("foo", LocalTime.of(23, 30, 59)), TgParameter.of("bar", (LocalTime) null), list);
    }

    @Test
    void testDateTime() {
        var list = TgParameterList.of();
        list.dateTime("foo", LocalDateTime.of(2022, 9, 22, 23, 30, 59));
        list.dateTime("bar", null);

        assertParameterList(TgParameter.of("foo", LocalDateTime.of(2022, 9, 22, 23, 30, 59)), TgParameter.of("bar", (LocalDateTime) null), list);
    }

    @Test
    void testOffsetTime() {
        var offset = ZoneOffset.ofHours(9);
        var list = TgParameterList.of();
        list.offsetTime("foo", OffsetTime.of(23, 30, 59, 0, offset));
        list.offsetTime("bar", null);

        assertParameterList(TgParameter.of("foo", OffsetTime.of(23, 30, 59, 0, offset)), TgParameter.of("bar", (OffsetTime) null), list);
    }

    @Test
    void testOffsetDateTime() {
        var offset = ZoneOffset.ofHours(9);
        var list = TgParameterList.of();
        list.offsetDateTime("foo", OffsetDateTime.of(2022, 9, 22, 23, 30, 59, 0, offset));
        list.offsetDateTime("bar", null);

        assertParameterList(TgParameter.of("foo", OffsetDateTime.of(2022, 9, 22, 23, 30, 59, 0, offset)), TgParameter.of("bar", (OffsetTime) null), list);
    }

    @Test
    void testZonedDateTime() {
        var zdt = ZonedDateTime.of(2022, 6, 30, 23, 30, 59, 999, ZoneId.of("Asia/Tokyo"));
        var list = TgParameterList.of();
        list.zonedDateTime("foo", zdt);
        list.zonedDateTime("bar", null);

        assertParameterList(TgParameter.of("foo", zdt), TgParameter.of("bar", (ZonedDateTime) null), list);
    }

    @Test
    void testAddStringBoolean() {
        var list = TgParameterList.of();
        list.add("foo", true);

        assertParameterList(TgParameter.of("foo", true), list);
    }

    @Test
    void testAddStringBooleanWrapper() {
        var list = TgParameterList.of();
        list.add("foo", Boolean.TRUE);
        list.add("bar", (Boolean) null);

        assertParameterList(TgParameter.of("foo", Boolean.TRUE), TgParameter.of("bar", (Boolean) null), list);
    }

    @Test
    void testAddStringInt() {
        var list = TgParameterList.of();
        list.add("foo", 123);

        assertParameterList(TgParameter.of("foo", 123), list);
    }

    @Test
    void testAddStringInteger() {
        var list = TgParameterList.of();
        list.add("foo", Integer.valueOf(123));
        list.add("bar", (Integer) null);

        assertParameterList(TgParameter.of("foo", 123), TgParameter.of("bar", (Integer) null), list);
    }

    @Test
    void testAddStringLong() {
        var list = TgParameterList.of();
        list.add("foo", 123L);

        assertParameterList(TgParameter.of("foo", 123L), list);
    }

    @Test
    void testAddStringLongWrapoper() {
        var list = TgParameterList.of();
        list.add("foo", Long.valueOf(123));
        list.add("bar", (Long) null);

        assertParameterList(TgParameter.of("foo", 123L), TgParameter.of("bar", (Long) null), list);
    }

    @Test
    void testAddStringFloat() {
        var list = TgParameterList.of();
        list.add("foo", 123f);

        assertParameterList(TgParameter.of("foo", 123f), list);
    }

    @Test
    void testAddStringFloatWrapper() {
        var list = TgParameterList.of();
        list.add("foo", Float.valueOf(123));
        list.add("bar", (Float) null);

        assertParameterList(TgParameter.of("foo", 123f), TgParameter.of("bar", (Float) null), list);
    }

    @Test
    void testAddStringDouble() {
        var list = TgParameterList.of();
        list.add("foo", 123d);

        assertParameterList(TgParameter.of("foo", 123d), list);
    }

    @Test
    void testAddStringDoubleWrapper() {
        var list = TgParameterList.of();
        list.add("foo", Double.valueOf(123));
        list.add("bar", (Double) null);

        assertParameterList(TgParameter.of("foo", 123d), TgParameter.of("bar", (Double) null), list);
    }

    @Test
    void testAddStringBigDecimal() {
        var list = TgParameterList.of();
        list.add("foo", BigDecimal.valueOf(123));
        list.add("bar", (BigDecimal) null);

        assertParameterList(TgParameter.of("foo", BigDecimal.valueOf(123)), TgParameter.of("bar", (BigDecimal) null), list);
    }

    @Test
    void testAddStringBigDecimalScale() {
        var list = TgParameterList.of();
        list.add("p", new BigDecimal("1.15"), 1);
        list.add("m", new BigDecimal("-1.15"), 1);
        list.add("bar", (BigDecimal) null, 1);

        var mode = TgVariableBigDecimal.DEFAULT_ROUNDING_MODE;
        assertParameterList(TgParameter.of("p", new BigDecimal("1.15").setScale(1, mode)), TgParameter.of("m", new BigDecimal("-1.15").setScale(1, mode)), TgParameter.of("bar", (BigDecimal) null),
                list);
    }

    @ParameterizedTest
    @ValueSource(strings = { "FLOOR", "DOWN", "HALF_UP" })
    void testAddStringBigDecimalRoundingMode(RoundingMode mode) {
        var list = TgParameterList.of();
        list.add("p", new BigDecimal("1.15"), 1, mode);
        list.add("m", new BigDecimal("-1.15"), 1, mode);
        list.add("bar", (BigDecimal) null, 1, mode);

        assertParameterList(TgParameter.of("p", new BigDecimal("1.15").setScale(1, mode)), TgParameter.of("m", new BigDecimal("-1.15").setScale(1, mode)), TgParameter.of("bar", (BigDecimal) null),
                list);
    }

    @Test
    void testAddStringString() {
        var list = TgParameterList.of();
        list.add("foo", "abc");
        list.add("bar", (String) null);

        assertParameterList(TgParameter.of("foo", "abc"), TgParameter.of("bar", (String) null), list);
    }

    @Test
    void testAddStringByteArray() {
        var list = TgParameterList.of();
        list.add("foo", new byte[] { 1, 2, 3 });
        list.add("bar", (byte[]) null);

        assertParameterList(TgParameter.of("foo", new byte[] { 1, 2, 3 }), TgParameter.of("bar", (byte[]) null), list);
    }

    @Test
    void testAddStringBooleanArray() {
        var list = TgParameterList.of();
        list.add("foo", new boolean[] { true, false, true });
        list.add("bar", (boolean[]) null);

        assertParameterList(TgParameter.of("foo", new boolean[] { true, false, true }), TgParameter.of("bar", (boolean[]) null), list);
    }

    @Test
    void testAddStringLocalDate() {
        var list = TgParameterList.of();
        list.add("foo", LocalDate.of(2022, 6, 30));
        list.add("bar", (LocalDate) null);

        assertParameterList(TgParameter.of("foo", LocalDate.of(2022, 6, 30)), TgParameter.of("bar", (LocalDate) null), list);
    }

    @Test
    void testAddStringLocalTime() {
        var list = TgParameterList.of();
        list.add("foo", LocalTime.of(23, 30, 59));
        list.add("bar", (LocalTime) null);

        assertParameterList(TgParameter.of("foo", LocalTime.of(23, 30, 59)), TgParameter.of("bar", (LocalTime) null), list);
    }

    @Test
    void testAddStringLocalDateTime() {
        var list = TgParameterList.of();
        list.add("foo", LocalDateTime.of(2022, 9, 22, 23, 30, 59));
        list.add("bar", (LocalDateTime) null);

        assertParameterList(TgParameter.of("foo", LocalDateTime.of(2022, 9, 22, 23, 30, 59)), TgParameter.of("bar", (LocalDateTime) null), list);
    }

    @Test
    void testAddStringOffsetTime() {
        var offset = ZoneOffset.ofHours(9);
        var list = TgParameterList.of();
        list.add("foo", OffsetTime.of(23, 30, 59, 0, offset));
        list.add("bar", (OffsetTime) null);

        assertParameterList(TgParameter.of("foo", OffsetTime.of(23, 30, 59, 0, offset)), TgParameter.of("bar", (OffsetTime) null), list);
    }

    @Test
    void testAddStringOffsetDateTime() {
        var offset = ZoneOffset.ofHours(9);
        var list = TgParameterList.of();
        list.add("foo", OffsetDateTime.of(2022, 9, 22, 23, 30, 59, 0, offset));
        list.add("bar", (OffsetDateTime) null);

        assertParameterList(TgParameter.of("foo", OffsetDateTime.of(2022, 9, 22, 23, 30, 59, 0, offset)), TgParameter.of("bar", (OffsetDateTime) null), list);
    }

    @Test
    void testAddStringZonedDateTime() {
        var zdt = ZonedDateTime.of(2022, 6, 30, 23, 30, 59, 999, ZoneId.of("Asia/Tokyo"));
        var list = TgParameterList.of();
        list.add("foo", zdt);
        list.add("bar", (ZonedDateTime) null);

        assertParameterList(TgParameter.of("foo", zdt), TgParameter.of("bar", (ZonedDateTime) null), list);
    }

    @Test
    void testAddTgParameter() {
        var list = TgParameterList.of();
        list.add(TgParameter.of("foo", 123));
        list.add(TgParameter.of("bar", "abc"));

        assertParameterList(TgParameter.of("foo", 123), TgParameter.of("bar", "abc"), list);
    }

    @Test
    void testAddTgParameterList() {
        var list = TgParameterList.of();
        list.add("foo", 123);
        list.add("bar", "abc");

        var list2 = TgParameterList.of();
        list2.add("zzz1", 123);
        list2.add("zzz2", 456);

        list.add(list2);

        assertParameterList(List.of(TgParameter.of("foo", 123), TgParameter.of("bar", "abc"), TgParameter.of("zzz1", 123), TgParameter.of("zzz2", 456)), list);
        assertParameterList(TgParameter.of("zzz1", 123), TgParameter.of("zzz2", 456), list2);
    }

    private void assertParameterList(TgParameter expected, TgParameterList actual) {
        assertParameterList(List.of(expected), actual);
    }

    private void assertParameterList(TgParameter expected1, TgParameter expected2, TgParameterList actual) {
        assertParameterList(List.of(expected1, expected2), actual);
    }

    private void assertParameterList(TgParameter expected1, TgParameter expected2, TgParameter expected3, TgParameterList actual) {
        assertParameterList(List.of(expected1, expected2, expected3), actual);
    }

    private void assertParameterList(List<TgParameter> expected, TgParameterList actual) {
        var expectedLow = expected.stream().map(TgParameter::toLowParameter).collect(Collectors.toList());
        var actualLow = actual.toLowParameterList();
        assertEquals(expectedLow, actualLow);
    }

    @Test
    void testToString() {
        var empty = TgParameterList.of();
        assertEquals("TgParameterList[]", empty.toString());
    }
}
