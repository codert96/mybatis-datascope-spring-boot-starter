# mybatis-datascope-spring-boot-starter


<pre>
    // You Mapper. 
    @Select("select id, name from sys_user")
    List<User> findAll(DataScope... dataScope);
    // You service
     userMapper.findAll(DataScope.of("id", () -> Arrays.asList("1", "2")));
    // sql is select id, name from sys_user where sys_user.in in("1","2")
</pre>
