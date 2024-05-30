# <a href='https://central.sonatype.com/artifact/io.github.codert96/mybatis-datascope-spring-boot-starter'>mybatis数据权限插件-mybatis-datascope-spring-boot-starter</a>

<pre>
    // You Mapper. 
    @Select("select id, name from sys_user")
    List<User> findAll(DataScope... dataScope);
    // You service
      userMapper.findAll(
                DataScope.of("dept_id", () -> Arrays.asList("6", "6")),
                DataScope.of("post_id", () -> Arrays.asList("1", "1")).operator(DataScope.Operator.NOT_EQUALS_TO)
                        .and(
                                DataScope.of("user_id", () -> Arrays.asList("2", "2")).operator(DataScope.Operator.GREATER_THAN_EQUALS)
                                        .or(
                                                DataScope.of("user_id", () -> Arrays.asList("21", "21"))
                                                        .and(
                                                                DataScope.of("user_id", () -> Arrays.asList("22", "22"))
                                                        )
                                        )
                                        .and(DataScope.of("user_id", () -> Arrays.asList("3", "3")))
                        ).and(
                                DataScope.of("user_id", () -> Arrays.asList("4", "4"))
                                        .or(
                                                DataScope.of("user_id", "select 1 from sys_user a where a.user_id=1 and sys_user.user_id = a.user_id")
                                                        .operator(DataScope.Operator.EXISTS)
                                        )
                        )
        );
    // sql is 

SELECT
    user_id 
FROM
	sys_user 
WHERE
	sys_user.dept_id IN ( '6', '6' ) 
	AND (
		sys_user.post_id NOT IN ( '1', '1' ) 
		AND (
			( sys_user.user_id >= '2' OR sys_user.user_id >= '2' ) 
			OR ( sys_user.user_id IN ( '21', '21' ) AND sys_user.user_id IN ( '22', '22' ) ) 
		) 
		AND sys_user.user_id IN ( '3', '3' ) 
		AND (
			sys_user.user_id IN ( '4', '4' ) 
			OR EXISTS ( SELECT 1 FROM sys_user a WHERE a.user_id = 1 AND sys_user.user_id = a.user_id ) 
		) 
	)
</pre>
