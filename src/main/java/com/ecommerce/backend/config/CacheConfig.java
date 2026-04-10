package com.ecommerce.backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Collection;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${spring.cache.redis.time-to-live}") Duration ttl
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(objectMapper)
                ))
                .disableCachingNullValues();

        RedisCacheManager delegate = RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfiguration)
                .transactionAware()
                .build();

        return new LoggingCacheManager(delegate);
    }

    static class LoggingCacheManager implements CacheManager {
        private final CacheManager delegate;

        LoggingCacheManager(CacheManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public Cache getCache(String name) {
            Cache cache = delegate.getCache(name);
            return cache == null ? null : new LoggingCache(cache);
        }

        @Override
        public Collection<String> getCacheNames() {
            return delegate.getCacheNames();
        }
    }

    static class LoggingCache implements Cache {
        private static final Logger logger = LoggerFactory.getLogger(LoggingCache.class);

        private final Cache delegate;

        LoggingCache(Cache delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            ValueWrapper value = delegate.get(key);
            if (value == null) {
                logger.info("Redis cache MISS: cache={}, key={}", getName(), key);
            } else {
                logger.info("Redis cache HIT: cache={}, key={}", getName(), key);
            }
            return value;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            T value = delegate.get(key, type);
            if (value == null) {
                logger.info("Redis cache MISS: cache={}, key={}", getName(), key);
            } else {
                logger.info("Redis cache HIT: cache={}, key={}", getName(), key);
            }
            return value;
        }

        @Override
        public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
            ValueWrapper cachedValue = delegate.get(key);
            if (cachedValue != null) {
                logger.info("Redis cache HIT: cache={}, key={}", getName(), key);
                Object value = cachedValue.get();
                @SuppressWarnings("unchecked")
                T castValue = (T) value;
                return castValue;
            }

            logger.info("Redis cache MISS: cache={}, key={}", getName(), key);
            try {
                T value = valueLoader.call();
                put(key, value);
                return value;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }

        @Override
        public void put(Object key, Object value) {
            logger.info("Redis cache PUT: cache={}, key={}", getName(), key);
            delegate.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            logger.info("Redis cache PUT_IF_ABSENT: cache={}, key={}", getName(), key);
            return delegate.putIfAbsent(key, value);
        }

        @Override
        public void evict(Object key) {
            logger.info("Redis cache EVICT: cache={}, key={}", getName(), key);
            delegate.evict(key);
        }

        @Override
        public boolean evictIfPresent(Object key) {
            logger.info("Redis cache EVICT_IF_PRESENT: cache={}, key={}", getName(), key);
            return delegate.evictIfPresent(key);
        }

        @Override
        public void clear() {
            logger.info("Redis cache CLEAR: cache={}", getName());
            delegate.clear();
        }

        @Override
        public boolean invalidate() {
            logger.info("Redis cache INVALIDATE: cache={}", getName());
            return delegate.invalidate();
        }
    }
}
