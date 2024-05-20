package com.github.codert96.mybatis;

import com.github.codert96.mybatis.bean.DataScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Intercepts(
        {
                @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
                @Signature(type = StatementHandler.class, method = "getBoundSql", args = {}),
                @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
                @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
                @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        }
)
@Slf4j
@RequiredArgsConstructor
public class DataScopeInterceptor implements Interceptor, InitializingBean {
    private final static String BOUND_SQL_FIELD = "sql";
    private final static String TEMP_SCOPE_TABLE = "tmp_scopes";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();
        if (target instanceof StatementHandler) {
            StatementHandler statementHandler = (StatementHandler) target;
            ParameterHandler parameterHandler = statementHandler.getParameterHandler();

            Object parameterObject = parameterHandler.getParameterObject();

            List<DataScope> dataScopes = findDataScope(parameterObject).stream().distinct().collect(Collectors.toList());
            if (!dataScopes.isEmpty()) {
                BoundSql boundSql = statementHandler.getBoundSql();
                Statement statement = CCJSqlParserUtil.parse(boundSql.getSql());
                AtomicReference<Table> reference = new AtomicReference<>();

                Expression where = null;
                if (statement instanceof Select) {
                    Select select = (Select) statement;
                    PlainSelect selectBody = select.getPlainSelect();
                    reference.set(selectBody.getFromItem(Table.class));
                    where = selectBody.getWhere();
                } else if (statement instanceof Update) {
                    Update update = (Update) statement;
                    reference.set(update.getTable());
                    where = update.getWhere();
                } else if (statement instanceof Delete) {
                    Delete delete = (Delete) statement;
                    reference.set(delete.getTable());
                    where = delete.getWhere();
                }
                Table table = reference.get();
                if (Objects.nonNull(table)) {
                    Expression extraWhere = apply(table, dataScopes.get(0));
                    if (dataScopes.size() > 1) {
                        extraWhere = dataScopes.stream()
                                .skip(1)
                                .map(dataScope -> apply(table, dataScope))
                                .reduce(extraWhere, (acc, expression) ->
                                        new AndExpression(acc,
                                                expression instanceof AndExpression || expression instanceof OrExpression
                                                        ? new Parenthesis(expression) : expression
                                        )
                                );
                    }
                    Expression expression = expression(where, extraWhere);
                    Method method = ReflectionUtils.findMethod(statement.getClass(), "setWhere", Expression.class);
                    if (Objects.nonNull(method)) {
                        ReflectionUtils.invokeMethod(method, statement, expression);
                        SystemMetaObject.forObject(boundSql).setValue(BOUND_SQL_FIELD, statement.toString());
                    }
                }
            }
        }
        try {
            return invocation.proceed();
        } finally {
            if (target instanceof Executor) {
                Object[] args = invocation.getArgs();
                MapSqlParameterSource[] parameterSources = Stream.of(args)
                        .map(this::findDataScope)
                        .flatMap(Collection::stream)
                        .distinct()
                        .filter(DataScope::usingTemporaryTable)
                        .map(DataScope::uuid)
                        .map(s -> new MapSqlParameterSource(Collections.singletonMap("uuid", s)))
                        .toArray(MapSqlParameterSource[]::new);
                if (parameterSources.length != 0) {
                    final String sql = String.format("DELETE FROM %s WHERE uuid = :uuid", TEMP_SCOPE_TABLE);
                    namedParameterJdbcTemplate.batchUpdate(sql, parameterSources);
                }
            }
        }
    }

    private Expression apply(Table table, DataScope dataScope) {
        Expression expression = expression(table, dataScope);
        if (expression instanceof OrExpression) {
            expression = new Parenthesis(expression);
        }
        List<DataScope.Condition> conditions = dataScope.conditions();
        if (!CollectionUtils.isEmpty(conditions)) {
            for (DataScope.Condition condition : conditions) {
                DataScope scope = condition.dataScope();
                Expression apply = apply(table, scope);
                expression = condition.or() ?
                        new Parenthesis(
                                new OrExpression(
                                        expression instanceof AndExpression ? new Parenthesis(expression) : expression,
                                        apply instanceof AndExpression ? new Parenthesis(apply) : apply
                                )
                        )
                        :
                        new AndExpression(expression, apply);
            }
        }
        return expression;
    }


    private Expression expression(Table table, DataScope dataScope) {
        List<String> scopes = dataScope.scopes();
        Column tableName = tableName(table, dataScope.columnName());
        if (CollectionUtils.isEmpty(scopes)) {
            return dataScope.emptyScopesReturnEmptyValue() ? new NotEqualsTo(new LongValue(1), new LongValue(1)) : new EqualsTo(new LongValue(1), new LongValue(1));
        }
        List<Expression> expressions = scopes.stream().<Expression>map(StringValue::new).collect(Collectors.toList());
        BinaryOperator<Expression> binaryOperator = (a, b) -> Objects.isNull(a) ? b : dataScope.any() ? new OrExpression(a, b) : new AndExpression(a, b);
        DataScope.Operator operator = dataScope.operator();
        switch (operator) {
            case LIKE:
                return expressions.stream()
                        .<Expression>map(expression -> new LikeExpression().withLeftExpression(tableName).withRightExpression(expression))
                        .reduce(null, binaryOperator);
            case NOT_LIKE:
                return expressions.stream()
                        .<Expression>map(expression -> new LikeExpression().withLeftExpression(tableName).withRightExpression(expression).withNot(true))
                        .reduce(null, binaryOperator);
            case GREATER_THAN:
                return expressions.stream()
                        .<Expression>map(expression -> new GreaterThan().withLeftExpression(tableName).withRightExpression(expression))
                        .reduce(null, binaryOperator);
            case GREATER_THAN_EQUALS:
                return expressions.stream()
                        .<Expression>map(expression -> new GreaterThanEquals().withLeftExpression(tableName).withRightExpression(expression))
                        .reduce(null, binaryOperator);
            case MINOR_THAN:
                return expressions.stream()
                        .<Expression>map(expression -> new MinorThan().withLeftExpression(tableName).withRightExpression(expression))
                        .reduce(null, binaryOperator);
            case MINOR_THAN_EQUALS:
                return expressions.stream()
                        .<Expression>map(expression -> new MinorThanEquals().withLeftExpression(tableName).withRightExpression(expression))
                        .reduce(null, binaryOperator);
            case EXISTS:
                return scopes.stream()
                        .map(scope -> {
                            try {
                                return CCJSqlParserUtil.parseCondExpression(String.format("EXISTS(%s)", scope));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .reduce(null, binaryOperator);
            case NOT_EXISTS:
                return scopes.stream()
                        .map(scope -> {
                            try {
                                return CCJSqlParserUtil.parseCondExpression(String.format("NOT EXISTS(%s)", scope));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .reduce(null, binaryOperator);
            default:
                boolean equals = DataScope.Operator.NOT_EQUALS_TO.equals(operator);
                if (expressions.size() == 1) {
                    Expression expression = expressions.get(0);
                    return equals ? new NotEqualsTo(tableName, expression) : new EqualsTo(tableName, expression);
                }
                if (dataScope.usingTemporaryTable()) {
                    try {
                        insert(dataScope, scopes);
                        return CCJSqlParserUtil.parseCondExpression(
                                String.format(
                                        "%sEXISTS(SELECT 1 FROM %s WHERE %s.uuid = '%s' AND %s.scope = %s)",
                                        equals ? "NOT " : "",
                                        TEMP_SCOPE_TABLE,
                                        TEMP_SCOPE_TABLE,
                                        dataScope.uuid(),
                                        TEMP_SCOPE_TABLE,
                                        tableName
                                )
                        );
                    } catch (Exception e) {
                        log.error("{}", e.getMessage());
                    }
                }
                InExpression inExpression = new InExpression(tableName, new Parenthesis(new ExpressionList<>(expressions)));
                inExpression.setNot(equals);
                return inExpression;
        }

    }

    private void insert(DataScope dataScope, List<String> scopes) {
        final String sql = String.format("INSERT INTO %s (uuid,scope) VALUES (:uuid,:scope)", TEMP_SCOPE_TABLE);
        namedParameterJdbcTemplate.batchUpdate(sql,
                scopes.stream()
                        .distinct()
                        .map(s -> {
                                    HashMap<String, Object> hashMap = new HashMap<>();
                                    hashMap.put("uuid", dataScope.uuid());
                                    hashMap.put("scope", s);
                                    return new MapSqlParameterSource(hashMap);
                                }
                        ).toArray(MapSqlParameterSource[]::new)
        );
    }

    private Expression expression(Expression where, Expression newCond) {
        if (Objects.isNull(where)) {
            return newCond;
        }
        return new AndExpression(
                new Parenthesis(newCond),
                new Parenthesis(where)
        );
    }

    private Column tableName(Table table, String column) {
        if (column.contains(".")) {
            return new Column(column);
        }
        String optional = Optional.of(table)
                .map(Table::getAlias)
                .map(Alias::getName)
                .map(s -> String.format("%s.%s", s, column))
                .orElseGet(() -> String.format("%s.%s", table.getFullyQualifiedName(), column));
        return new Column(optional);
    }

    private List<DataScope> findDataScope(Object parameterObject) {
        List<DataScope> list = toList(parameterObject);
        if (!list.isEmpty()) {
            return list;
        }
        if (parameterObject instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) parameterObject;
            Collection<?> values = map.values();
            List<DataScope> dataScopes = new ArrayList<>();
            for (Object value : values) {
                List<DataScope> tmp = toList(value);
                if (!tmp.isEmpty()) {
                    dataScopes.addAll(tmp);
                }
            }
            return dataScopes;
        }
        return Collections.emptyList();
    }

    private List<DataScope> toList(Object parameterObject) {
        if (parameterObject instanceof DataScope) {
            DataScope dataScope = (DataScope) parameterObject;
            return Collections.singletonList(dataScope);
        }
        if (parameterObject instanceof DataScope[]) {
            DataScope[] dataScope = (DataScope[]) parameterObject;
            return Stream.of(dataScope).distinct().collect(Collectors.toList());
        }
        if (parameterObject instanceof List<?>) {
            List<?> dataScopes = (List<?>) parameterObject;
            return dataScopes.stream().filter(tmp -> tmp instanceof DataScope).map(DataScope.class::cast).distinct().collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public void afterPropertiesSet() {
        final String sql = String.format("CREATE TABLE IF NOT EXISTS %s (uuid CHAR (32),scope CHAR (64),CONSTRAINT idx_scope PRIMARY KEY (uuid,scope))", TEMP_SCOPE_TABLE);
        namedParameterJdbcTemplate.getJdbcTemplate().execute(sql);
    }
}
