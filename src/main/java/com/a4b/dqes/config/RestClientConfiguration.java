/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;


@Configuration
public class RestClientConfiguration {

	public static final String EXTERNAL_REST_TEMPLATE_BEAN_NAME = "externalRestTemplate";
	public static final String EXTERNAL_WEB_CLIENT_BEAN_NAME = "externalWebClient";

    private static final int MAX_IDLE_TIME_IN_SECONDS = 10;
    private static final int EVIC_TIME_IN_SECONDS = 10;
    private static final int PENDING_ACQUIRE_TIMEOUT_IN_SECOND = 60;

    @Configuration
	@ConditionalOnProperty(value = "spring.cloud.consul.enabled", matchIfMissing = true)
	public static class LoadBalancedRestTemplateConfiguration {
		@LoadBalanced
		@Bean
		@Primary
		public RestTemplate loadBalancedRestTemplate(RestTemplateBuilder builder) {
            return builder.build();
		}
	}

	@Configuration
	@ConditionalOnProperty(value = "spring.cloud.consul.enabled", matchIfMissing = false, havingValue = "false")
	public static class RestTemplateConfiguration {

		@Bean
		@Primary
		public RestTemplate restTemplate(RestTemplateBuilder builder) {
            return builder.build();
		}
	}

	@Configuration
	@ConditionalOnProperty(value = "spring.cloud.consul.enabled", matchIfMissing = true)
	public static class LoadBalancedWebClientConfiguration {

		@Bean
		@LoadBalanced
		@Primary
        public WebClient.Builder loadBalancedWebClientBuilder() {
            return WebClient.builder();
        }
	}

	@Configuration
	@ConditionalOnProperty(value = "spring.cloud.consul.enabled", matchIfMissing = false, havingValue = "false")
	public static class WebClientConfiguration {

		@Bean
		@Primary
        public WebClient.Builder loadBalancedWebClientBuilder() {
            return WebClient.builder();
        }
	}

    @Bean
    @Primary
    public WebClient webClient(WebClient.Builder builder) {
        ConnectionConfigs connectionConfigs = new ConnectionConfigs(5, 5, 120, 1000);
        return builder
            .codecs(codec -> codec.defaultCodecs().maxInMemorySize(-1))// -1 to unlimited
            .clientConnector(new ReactorClientHttpConnector(getHttpClient(false, null, connectionConfigs))).build();
    }

	@Configuration
	public static class ExternalRestTemplateConfiguration {

		@Bean(name = EXTERNAL_REST_TEMPLATE_BEAN_NAME)
		public RestTemplate externalRestTemplate(RestTemplateBuilder builder) {
            return builder.build();
		}

		@Bean(name = EXTERNAL_WEB_CLIENT_BEAN_NAME)
		public WebClient externalWebClient(WebClient.Builder builder) {
            ConnectionConfigs connectionConfigs = new ConnectionConfigs(5, 5, 120, 1000);
            return /*WebClient.builder()*/builder
                .codecs(codec -> codec.defaultCodecs().maxInMemorySize(-1))// -1 to unlimited
                .clientConnector(new ReactorClientHttpConnector(getHttpClient(false, null, connectionConfigs)))
                .build();
		}
	}

    public record ConnectionConfigs(int connTimeoutInSeconds,
                                    int sendTimeoutInSeconds,
                                    int receiveTimeoutInSeconds,
                                    int maxConnPerConnPool) {
    }

    public static ConnectionProvider.Builder getConnectionProviders(ConnectionConfigs connConfigs, int responseTimeoutInSeconds) {
        ConnectionProvider.Builder connectionProviders = ConnectionProvider.builder("max_conn");
        connectionProviders.maxConnections(connConfigs.maxConnPerConnPool())// number connection / connection pool
            .maxIdleTime(Duration.ofSeconds(MAX_IDLE_TIME_IN_SECONDS))// after 10s, if connection it is not used, it will be closed.
            .maxLifeTime(Duration.ofSeconds(responseTimeoutInSeconds))//Đây là thời gian tối đa mà một kết nối có thể tồn tại trước khi bị đóng ngay cả khi nó đang được sử dụng. Trong trường hợp này, một kết nối sẽ không được sử dụng lâu hơn 60 giây trước khi được đóng và thay thế bằng một kết nối mới.
            .pendingAcquireTimeout(Duration.ofSeconds(PENDING_ACQUIRE_TIMEOUT_IN_SECOND))//Đây là thời gian tối đa mà một yêu cầu tạo kết nối mới có thể chờ đợi trước khi timeout. Nếu không có kết nối sẵn có trong pool và số lượng kết nối tối đa đã được sử dụng, yêu cầu tạo kết nối mới sẽ chờ đợi cho đến khi thời gian này hết hoặc có một kết nối trống được phát hiện.
            .evictInBackground(Duration.ofSeconds(EVIC_TIME_IN_SECONDS));// scan connections every 120s.
        return connectionProviders;
    }

    private static SslContext buildSslContextForReactorClientHttpConnector(boolean isSsl, String certPath) {
        try {
            SslContextBuilder contextBuilder = SslContextBuilder.forClient().protocols("TLSv1.2");
            if (isSsl) {
                // return contextBuilder.trustManager(new File(certPath)).build();
                Path path = Path.of(certPath);
                try (var certStream = Files.newInputStream(path)) {
                    return contextBuilder.trustManager(certStream).build();
                }
            } else {
                return contextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            }
        } catch (Exception exception) {
            throw new RuntimeException("Cannot process ssl for spectrum.");
        }
    }

    public static HttpClient getHttpClient(boolean isSsl, String certPath, ConnectionConfigs connConfigs) {
        int responseTimeoutInSeconds = connConfigs.connTimeoutInSeconds() + connConfigs.sendTimeoutInSeconds() + connConfigs.receiveTimeoutInSeconds();
        ConnectionProvider.Builder connectionProviders = getConnectionProviders(connConfigs, responseTimeoutInSeconds);

        return HttpClient
            .create(connectionProviders.build())
            .secure(t -> t.sslContext(buildSslContextForReactorClientHttpConnector(isSsl, certPath)))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connConfigs.connTimeoutInSeconds() * 1000)
            .responseTimeout(Duration.ofSeconds(responseTimeoutInSeconds))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(connConfigs.receiveTimeoutInSeconds()))//Đây là thời gian tối đa mà một yêu cầu có thể chờ đợi để đọc dữ liệu từ máy chủ sau khi kết nối đã được thiết lập.
                .addHandlerLast(new WriteTimeoutHandler(connConfigs.sendTimeoutInSeconds())))//Đây là thời gian tối đa mà một yêu cầu có thể chờ đợi để ghi dữ liệu lên máy chủ sau khi kết nối đã được thiết lập.
            ;
    }

}
