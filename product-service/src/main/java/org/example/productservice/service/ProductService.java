package org.example.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.productservice.entity.Product;
import org.example.productservice.exception.ProductNotFoundException;
import org.example.productservice.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class ProductService {

    private static final String PRODUCT_CACHE_KEY_PREFIX = "cache:product:";

    private static final String PRODUCT_LOCK_KEY_PREFIX = "lock:product:";

    private static final String NULL_VALUE = "_NULL_";
    //正常商品缓存10分钟
    private static final long PRODUCT_CACHE_TTL_MINUTES = 10;
    //空值缓存时间2分钟
    private static final long NULL_CACHE_TTL_MINUTES = 2;
    //Redis互斥锁过期时间：10秒
    private static final long LOCK_TTL_SECONDS = 10;
    //获取锁失败后的重试次数
    private static final int RETRY_TIMES = 20;
    //每次重试等待时间
    private static final long RETRY_INTERVAL_MILLIS = 50;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    public Product getProduct(Long id) {
        if (id ==null|| id <= 0){
            throw new IllegalArgumentException("商品ID不合法");
        }
        String cacheKey = buildProductCacheKey(id);
        String lockKey = buildProductLockKey(id);
        Product cachedProduct = readProductFromCache(cacheKey,id);
        if (cachedProduct != null) {
            return cachedProduct;
        }
        return queryProductWithMutex(id,cacheKey,lockKey);
    }
    // 购买库存
    public boolean deductStock(Long id, Integer quantity) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("商品ID不合法");
        }
            if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("扣减数量必须大于0");
        }
        int affectedRows = productMapper.deductStock(id, quantity);
        boolean success =  affectedRows > 0;
        if(success){
            stringRedisTemplate.delete(buildProductCacheKey(id));
        }
        return success;
    }

    // 从缓存中读取商品信息
    private Product readProductFromCache(String cacheKey, Long id) {
        String value = stringRedisTemplate.opsForValue().get(cacheKey);
        if(value == null){
            return null;
        }
        if(NULL_VALUE.equals(value)){
            throw new ProductNotFoundException(id);
        }
        try {
            return objectMapper.readValue(value, Product.class);
        } catch (JsonProcessingException e) {
            // 缓存中的JSON异常时，删除脏缓存，后续重新查数据库
            stringRedisTemplate.delete(cacheKey);
            return null;
        }
    }

    // 查询商品信息并使用互斥锁保证线程安全
    private Product queryProductWithMutex(Long id, String cacheKey, String lockKey) {
        for(int i=0;i<RETRY_TIMES;i++){
            String token = UUID.randomUUID().toString();
            boolean locked = tryLock(lockKey, token);
            if(locked){
                try{
                    Product cacheProduct = readProductFromCache(cacheKey,id);
                    if(cacheProduct !=null){
                        return cacheProduct;
                    }
                    Product product = productMapper.selectById(id);
                    if(product == null){
                        System.out.println("准备写入空值缓存，key = " + cacheKey);
                        writeNullCache(cacheKey);
                        throw new ProductNotFoundException(id);
                    }
                    writeProductCache(cacheKey,product);
                    return product;
                }finally {
                    unlock(lockKey,token);
                }
            }
            sleepQuietly(RETRY_INTERVAL_MILLIS);
            Product cachedProduct = readProductFromCache(cacheKey,id);
            if (cachedProduct!= null) {
                return cachedProduct;
            }
        }
        throw new IllegalStateException("系统繁忙，请稍后重试");
    }
    // 尝试获取锁
    private boolean tryLock(String lockKey, String token) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                token,
                Duration.ofSeconds(LOCK_TTL_SECONDS));
        return Boolean.TRUE.equals(success);
    }
    //缓存null值
    private void writeNullCache(String cacheKey) {
        stringRedisTemplate.opsForValue().set(
                cacheKey,
                NULL_VALUE,
                NULL_CACHE_TTL_MINUTES,
                TimeUnit.MINUTES
        );
    }
    // 写入商品缓存
    private void writeProductCache(String cacheKey,Product product){
        try {
            String json = objectMapper.writeValueAsString(product);
            long ttl = PRODUCT_CACHE_TTL_MINUTES
                    + ThreadLocalRandom.current().nextLong(0,10);
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    json,
                    ttl,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("商品缓存序列化失败",e);
        }
    }
    // 释放锁
    private void unlock(String lockKey, String token) {
        String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then" +
                " return redis.call('del', KEYS[1]) " +
                "else " +
                "return 0 " +
                "end";
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
        stringRedisTemplate.execute(script,
                Collections.singletonList(lockKey),
                token);
    }
    // 安静地休眠
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    // 构建商品缓存的key
    private String buildProductCacheKey(Long id) {
        return PRODUCT_CACHE_KEY_PREFIX + id;
    }

    // 构建商品锁的key
    private String buildProductLockKey(Long id) {
        return PRODUCT_LOCK_KEY_PREFIX + id;
    }
}
