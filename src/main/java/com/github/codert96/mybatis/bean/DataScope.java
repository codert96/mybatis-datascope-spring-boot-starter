package com.github.codert96.mybatis.bean;


import lombok.*;
import lombok.experimental.Accessors;

import java.util.*;
import java.util.function.Supplier;

@Getter
@SuppressWarnings("unused")
@Setter(AccessLevel.PRIVATE)
@Accessors(chain = true, fluent = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DataScope {

    private String uuid;

    private List<String> scopes;

    private String columnName;

    private List<Condition> conditions = new ArrayList<>();

    /**
     * 如果 scopes 是空的 则查询条件加 1!=1 默认：true
     */
    private boolean emptyScopesReturnEmptyValue = true;

    @Setter(AccessLevel.PUBLIC)
    private int usingTemporaryTableLimit = 100;

    @Setter(AccessLevel.PUBLIC)
    private Operator operator = Operator.EQUALS_TO;
    private boolean any = true;

    @Setter(AccessLevel.PUBLIC)
    private String escape;

    public static DataScope of(String columnName, Supplier<List<String>> supplier) {
        return of(true, columnName, supplier);
    }

    public static DataScope of(String columnName, String singleScope) {
        return of(columnName, () -> Collections.singletonList(singleScope));
    }

    public static DataScope of(String columnName, List<String> scopes) {
        return of(columnName, () -> scopes);
    }

    public static DataScope of(boolean emptyScopesReturnEmptyValue, String columnName, Supplier<List<String>> supplier) {
        DataScope dataScope = new DataScope()
                .uuid(UUID.randomUUID().toString().replace("-", ""))
                .columnName(columnName)
                .emptyScopesReturnEmptyValue(emptyScopesReturnEmptyValue);
        List<String> strings = Optional.ofNullable(supplier.get()).orElseGet(Collections::emptyList);
        return dataScope.scopes(strings);
    }

    public boolean usingTemporaryTable() {
        if (Objects.isNull(scopes) || scopes.isEmpty()) {
            return false;
        }
        return scopes.size() >= usingTemporaryTableLimit;
    }

    public DataScope or(DataScope or) {
        this.conditions.add(new Condition(true, or));
        return this;
    }

    public DataScope and(DataScope and) {
        this.conditions.add(new Condition(false, and));
        return this;
    }

    public enum Operator {
        EQUALS_TO,
        NOT_EQUALS_TO,
        LIKE,
        NOT_LIKE,
        GREATER_THAN,
        GREATER_THAN_EQUALS,
        MINOR_THAN,
        MINOR_THAN_EQUALS,
        EXISTS,
        NOT_EXISTS,
        REGEX,
        NOT_REGEX,

        MATCH_CASESENSITIVE,
        MATCH_CASEINSENSITIVE,
        NOT_MATCH_CASESENSITIVE,
        NOT_MATCH_CASEINSENSITIVE
    }

    @Data
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Condition {
        private final boolean or;
        private final DataScope dataScope;
    }
}
