package cn.shiyujun;

import cn.shiyujun.config.*;
import cn.shiyujun.util.ServerListFilterUtil;
import com.netflix.client.config.IClientConfig;
import com.netflix.discovery.EurekaClient;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableLifecycle;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.loadbalancer.*;
import com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.netflix.ribbon.*;
import org.springframework.cloud.netflix.ribbon.eureka.DomainExtractingServerList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * d
 *
 * @author syj
 * CreateTime 2019/09/12
 * describe:
 */
@Slf4j
@Configuration
public class GrayAutoConfiguration {


    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate requestTemplate) {
                ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                //当前不是web请求
                if (requestAttributes == null) {
                    return;
                }
                HttpServletRequest request = requestAttributes.getRequest();
                Enumeration<String> headerNames = request.getHeaderNames();
                if (headerNames != null) {
                    while (headerNames.hasMoreElements()) {
                        String name = headerNames.nextElement();
                        if (Objects.equals(ServerListFilterUtil.APP_VERSION, name)) {
                            String values = request.getHeader(name);
                            requestTemplate.header(name, values);
                            return;
                        }
                    }
                }
            }
        };
    }

    @Bean
    public HystrixConcurrencyStrategy hystrixConcurrencyStrategy() {
        return new RequestAttributeHystrixConcurrencyStrategy();
    }
    @Bean
    public IRule ribbonRule(
            @Autowired(required = false) IClientConfig config) {
        ZoneAvoidanceRule rule = new GrayLoadBalanceRule();
        rule.initWithNiwsConfig(config);
        return rule;
    }

    private class RequestAttributeHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {

        private HystrixConcurrencyStrategy delegate;

        public RequestAttributeHystrixConcurrencyStrategy() {
            try {
                this.delegate = HystrixPlugins.getInstance().getConcurrencyStrategy();
                if (this.delegate instanceof RequestAttributeHystrixConcurrencyStrategy) {
                    // Welcome to singleton hell...
                    return;
                }
                HystrixCommandExecutionHook commandExecutionHook = HystrixPlugins
                        .getInstance().getCommandExecutionHook();
                HystrixEventNotifier eventNotifier = HystrixPlugins.getInstance()
                        .getEventNotifier();
                HystrixMetricsPublisher metricsPublisher = HystrixPlugins.getInstance()
                        .getMetricsPublisher();
                HystrixPropertiesStrategy propertiesStrategy = HystrixPlugins.getInstance()
                        .getPropertiesStrategy();
                this.logCurrentStateOfHystrixPlugins(eventNotifier, metricsPublisher,
                        propertiesStrategy);
                HystrixPlugins.reset();
                HystrixPlugins.getInstance().registerConcurrencyStrategy(this);
                HystrixPlugins.getInstance()
                        .registerCommandExecutionHook(commandExecutionHook);
                HystrixPlugins.getInstance().registerEventNotifier(eventNotifier);
                HystrixPlugins.getInstance().registerMetricsPublisher(metricsPublisher);
                HystrixPlugins.getInstance().registerPropertiesStrategy(propertiesStrategy);
            } catch (Exception e) {
                log.error("Failed to register Sleuth Hystrix Concurrency Strategy", e);
            }
        }

        private void logCurrentStateOfHystrixPlugins(HystrixEventNotifier eventNotifier,
                                                     HystrixMetricsPublisher metricsPublisher,
                                                     HystrixPropertiesStrategy propertiesStrategy) {
            if (log.isDebugEnabled()) {
                log.debug("Current Hystrix plugins configuration is ["
                        + "concurrencyStrategy [" + this.delegate + "]," + "eventNotifier ["
                        + eventNotifier + "]," + "metricPublisher [" + metricsPublisher + "],"
                        + "propertiesStrategy [" + propertiesStrategy + "]," + "]");
                log.debug("Registering Sleuth Hystrix Concurrency Strategy.");
            }
        }

        @Override
        public <T> Callable<T> wrapCallable(Callable<T> callable) {
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            return new WrappedCallable<>(callable, requestAttributes);
        }

        @Override
        public ThreadPoolExecutor getThreadPool(HystrixThreadPoolKey threadPoolKey,
                                                HystrixProperty<Integer> corePoolSize,
                                                HystrixProperty<Integer> maximumPoolSize,
                                                HystrixProperty<Integer> keepAliveTime, TimeUnit unit,
                                                BlockingQueue<Runnable> workQueue) {
            return this.delegate.getThreadPool(threadPoolKey, corePoolSize, maximumPoolSize,
                    keepAliveTime, unit, workQueue);
        }

        @Override
        public ThreadPoolExecutor getThreadPool(HystrixThreadPoolKey threadPoolKey,
                                                HystrixThreadPoolProperties threadPoolProperties) {
            return this.delegate.getThreadPool(threadPoolKey, threadPoolProperties);
        }

        @Override
        public BlockingQueue<Runnable> getBlockingQueue(int maxQueueSize) {
            return this.delegate.getBlockingQueue(maxQueueSize);
        }

        @Override
        public <T> HystrixRequestVariable<T> getRequestVariable(
                HystrixRequestVariableLifecycle<T> rv) {
            return this.delegate.getRequestVariable(rv);
        }


    }

    private class WrappedCallable<T> implements Callable<T> {

        private final Callable<T> target;
        private final RequestAttributes requestAttributes;

        public WrappedCallable(Callable<T> target, RequestAttributes requestAttributes) {
            this.target = target;
            this.requestAttributes = requestAttributes;
        }

        @Override
        public T call() throws Exception {
            try {
                RequestContextHolder.setRequestAttributes(requestAttributes);
                return target.call();
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }
    }
}
