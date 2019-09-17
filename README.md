使用方式：
1. 引入依赖
```java
<dependency>
    <groupId>cn.shiyujun</groupId>
    <artifactId>syj-gray</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```
2. 在需要灰度的微服务中添加版本标识
```yaml
eureka:
  instance:
    metadata-map:
      appversion: v2
```
3. 灰度请求时携带一个name为appversion的header，值和上方metadata-map中的值对应即可