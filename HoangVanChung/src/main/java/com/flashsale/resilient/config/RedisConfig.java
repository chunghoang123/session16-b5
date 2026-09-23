package com.flashsale.resilient.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Cấu hình Redis Cache chuyên sâu cho hệ thống Flash Sale chịu tải cao:
 * 1. Sử dụng Jackson2JsonRedisSerializer tối ưu serialization cho JSON thay vì JDK Serializer.
 * 2. Tích hợp JavaTimeModule cho các trường LocalDateTime/LocalDate.
 * 3. Thiết lập TTL hợp lý và hỗ trợ cấu hình TTL riêng biệt cho từng cache name.
 */
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    @Value("${flash-sale.cache.base-ttl-seconds:3600}")
    private long baseTtlSeconds;

    /**
     * Cấu hình ObjectMapper chuẩn cho Redis Serializer hỗ trợ Java 8 Time và đa hình.
     */
    private ObjectMapper createRedisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        
        // Kích hoạt default typing để deserialize đúng kiểu đối tượng
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return objectMapper;
    }

    /**
     * Cấu hình Jackson2JsonRedisSerializer tối ưu.
     */
    @Bean
    public Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer() {
        return new Jackson2JsonRedisSerializer<>(createRedisObjectMapper(), Object.class);
    }

    /**
     * Cấu hình RedisTemplate dùng chung cho các tác vụ tương tác Redis thủ công (Warm-up, metrics).
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            Jackson2JsonRedisSerializer<Object> serializer) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key & HashKey dùng chuỗi String
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value & HashValue dùng Jackson2JsonRedisSerializer
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Cấu hình RedisCacheManager cho @Cacheable.
     */
    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            Jackson2JsonRedisSerializer<Object> serializer) {

        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(baseTtlSeconds))
                .disableCachingNullValues() // Không cache null để tiết kiệm RAM, hoặc có thể bật với TTL ngắn
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        // Cấu hình TTL riêng cho từng nhóm dữ liệu
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Cache sản phẩm Flash Sale: TTL mặc định 1 giờ
        cacheConfigurations.put("flash_sale_products", defaultCacheConfig.entryTtl(Duration.ofSeconds(baseTtlSeconds)));
        
        // Cache danh sách tổng hợp: TTL 5 phút
        cacheConfigurations.put("flash_sale_active_list", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
