package com.github.codert96.mybatis.bean;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    @Setter(AccessLevel.PUBLIC)
    private boolean or = false;

    /**
     * 如果 scopes 是空的 则查询条件加 1!=1 默认：true
     */
    private boolean emptyScopesReturnEmptyValue = true;

    @Setter(AccessLevel.PUBLIC)
    private int usingTemporaryTableLimit = 100;

    public static DataScope of(String columnName, Supplier<List<String>> supplier) {
        return of(true, columnName, supplier);
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
}
