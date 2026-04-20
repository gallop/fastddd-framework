package com.gallop.fastddd.common.util;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 集合工具类，提供常用的集合操作方法
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
public final class CollectionUtils {
    
    private CollectionUtils() {
        // 工具类不允许实例化
    }
    
    /**
     * 判断集合是否为空（null或空集合）
     * 
     * @param collection 集合
     * @return 是否为空
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }
    
    /**
     * 判断集合是否不为空
     * 
     * @param collection 集合
     * @return 是否不为空
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }
    
    /**
     * 判断Map是否为空（null或空Map）
     * 
     * @param map Map
     * @return 是否为空
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }
    
    /**
     * 判断Map是否不为空
     * 
     * @param map Map
     * @return 是否不为空
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }
    
    /**
     * 获取集合的大小，如果为null则返回0
     * 
     * @param collection 集合
     * @return 集合大小
     */
    public static int size(Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }
    
    /**
     * 获取Map的大小，如果为null则返回0
     * 
     * @param map Map
     * @return Map大小
     */
    public static int size(Map<?, ?> map) {
        return map == null ? 0 : map.size();
    }
    
    /**
     * 创建不可变的List
     * 
     * @param elements 元素
     * @param <T> 元素类型
     * @return 不可变的List
     */
    @SafeVarargs
    public static <T> List<T> listOf(T... elements) {
        if (elements == null || elements.length == 0) {
            return List.of();
        }
        return List.of(elements);
    }
    
    /**
     * 创建不可变的Set
     * 
     * @param elements 元素
     * @param <T> 元素类型
     * @return 不可变的Set
     */
    @SafeVarargs
    public static <T> Set<T> setOf(T... elements) {
        if (elements == null || elements.length == 0) {
            return Set.of();
        }
        return Set.of(elements);
    }
    
    /**
     * 创建ArrayList
     * 
     * @param elements 元素
     * @param <T> 元素类型
     * @return ArrayList
     */
    @SafeVarargs
    public static <T> List<T> newArrayList(T... elements) {
        List<T> list = new ArrayList<>();
        if (elements != null) {
            Collections.addAll(list, elements);
        }
        return list;
    }
    
    /**
     * 创建HashSet
     * 
     * @param elements 元素
     * @param <T> 元素类型
     * @return HashSet
     */
    @SafeVarargs
    public static <T> Set<T> newHashSet(T... elements) {
        Set<T> set = new HashSet<>();
        if (elements != null) {
            Collections.addAll(set, elements);
        }
        return set;
    }
    
    /**
     * 创建HashMap
     * 
     * @param <K> 键类型
     * @param <V> 值类型
     * @return HashMap
     */
    public static <K, V> Map<K, V> newHashMap() {
        return new HashMap<>();
    }
    
    /**
     * 将集合转换为Map，使用指定的键提取器
     * 
     * @param collection 集合
     * @param keyMapper 键提取器
     * @param <T> 元素类型
     * @param <K> 键类型
     * @return Map
     */
    public static <T, K> Map<K, T> toMap(Collection<T> collection, Function<T, K> keyMapper) {
        if (isEmpty(collection)) {
            return new HashMap<>();
        }
        return collection.stream().collect(Collectors.toMap(keyMapper, Function.identity()));
    }
    
    /**
     * 将集合转换为Map，使用指定的键和值提取器
     * 
     * @param collection 集合
     * @param keyMapper 键提取器
     * @param valueMapper 值提取器
     * @param <T> 元素类型
     * @param <K> 键类型
     * @param <V> 值类型
     * @return Map
     */
    public static <T, K, V> Map<K, V> toMap(Collection<T> collection, 
                                            Function<T, K> keyMapper, 
                                            Function<T, V> valueMapper) {
        if (isEmpty(collection)) {
            return new HashMap<>();
        }
        return collection.stream().collect(Collectors.toMap(keyMapper, valueMapper));
    }
    
    /**
     * 获取集合的第一个元素
     * 
     * @param collection 集合
     * @param <T> 元素类型
     * @return 第一个元素，如果集合为空则返回null
     */
    public static <T> T getFirst(Collection<T> collection) {
        if (isEmpty(collection)) {
            return null;
        }
        return collection.iterator().next();
    }
    
    /**
     * 获取集合的最后一个元素
     * 
     * @param list List
     * @param <T> 元素类型
     * @return 最后一个元素，如果List为空则返回null
     */
    public static <T> T getLast(List<T> list) {
        if (isEmpty(list)) {
            return null;
        }
        return list.get(list.size() - 1);
    }
    
    /**
     * 安全地添加元素到集合
     * 
     * @param collection 集合
     * @param element 元素
     * @param <T> 元素类型
     * @return 是否添加成功
     */
    public static <T> boolean addIfNotNull(Collection<T> collection, T element) {
        if (collection != null && element != null) {
            return collection.add(element);
        }
        return false;
    }
}