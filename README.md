<pre>
  <dependency>
      <groupId>io.github.codert96</groupId>
      <artifactId>mybatis-datascope-spring-boot-starter</artifactId>
      <version>1.0.0</version>
  </dependency>  
</pre>


<pre>
    // You Mapper. 
    @Select("select id, name from sys_user")
    List<User> findAll(DataScope... dataScope);
    // You service
     userMapper.findAll(DataScope.of("id", () -> Arrays.asList("1", "2")));
    // sql is select id, name from sys_user where sys_user.in in("1","2")
</pre>
