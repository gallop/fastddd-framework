package com.gallop.fastddd.cache.spring;

import com.gallop.fastddd.cache.MultiLevelCache;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

/**
 * Spring Cache 接口适配，包装 MultiLevelCache
 *
 * @author gallop
 */
public class MultiLevelSpringCache implements Cache {

    private final MultiLevelCache delegate;

    public MultiLevelSpringCache(MultiLevelCache delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getCacheName();
    }

    @Override
    public Object getNativeCache() {
        return delegate;
    }

    @Override
    public ValueWrapper get(Object key) {
        MultiLevelCache.CacheEntry<Object> entry = delegate.getEntryAutoType(stringify(key));
        if (!entry.hit) {
            return null;
        }
        return () -> entry.value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        return delegate.get(stringify(key), type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        return (T) delegate.getOrLoad(stringify(key), Object.class, () -> {
            try {
                return valueLoader.call();
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        });
    }

    @Override
    public void put(Object key, Object value) {
        delegate.put(stringify(key), value);
    }

    @Override
    public void evict(Object key) {
        delegate.evict(stringify(key));
    }

    @Override
    public boolean evictIfPresent(Object key) {
        delegate.evict(stringify(key));
        return true;
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean invalidate() {
        delegate.clear();
        return true;
    }

    private String stringify(Object key) {
        if (key == null) {
            return "null";
        }
        return key.toString();
    }
}
