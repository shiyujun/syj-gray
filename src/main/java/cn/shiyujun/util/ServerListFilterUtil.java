package cn.shiyujun.util;

import cn.shiyujun.GrayAutoConfiguration;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * d
 *
 * @author syj
 * CreateTime 2019/09/17
 * describe:
 */
public   class ServerListFilterUtil {
    public static final String APP_VERSION = "appversion";
    public static List<Server> grayFilter(List<Server> servers){
        List<Server> list =   new ArrayList<>();
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        //当前不是web请求
        if (requestAttributes != null) {
            HttpServletRequest request = requestAttributes.getRequest();
            Enumeration<String> headerNames = request.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    String name = headerNames.nextElement();
                    String value = request.getHeader(name);
                    //存在灰度header
                    if (Objects.equals(APP_VERSION, name)) {
                        for (Server server : servers) {
                            Map<String, String> metadata = ((DiscoveryEnabledServer) server).getInstanceInfo().getMetadata();
                            //当前存在服务版本与灰度需求版本一致的场景
                            if (metadata.containsKey(APP_VERSION) && Objects.equals(value, metadata.get(APP_VERSION))) {
                                list.add(server);
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

}
