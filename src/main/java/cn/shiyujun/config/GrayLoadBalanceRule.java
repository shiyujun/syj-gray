package cn.shiyujun.config;

import cn.shiyujun.GrayAutoConfiguration;
import cn.shiyujun.util.ServerListFilterUtil;
import com.google.common.base.Optional;
import com.netflix.loadbalancer.*;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 灰度发布的负载规则
 */
@Slf4j
public class GrayLoadBalanceRule extends ZoneAvoidanceRule {


    @Override
    public Server choose(Object key) {
        ILoadBalancer lb = getLoadBalancer();
        List<Server> servers =   lb.getAllServers();
        List<Server> list= ServerListFilterUtil.grayFilter((List<Server>)servers);
        if(list.size()>0){
            servers=list;
        }
        Optional<Server> server = super.getPredicate().chooseRoundRobinAfterFiltering(servers, key);
        if (server.isPresent()) {
            return server.get();
        } else {
            return null;
        }
    }




}

